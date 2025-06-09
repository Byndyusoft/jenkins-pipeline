/** Pipeline Launch Settings Class */
class PipelineParameters {
    private final def script
    private final Logger logger

    private List<PipelineStage> mandatoryStages
    private List<PipelineStage> optionalStages
    private List<DeployEnvironment> environments

    private final String titleDeploymentEnvironment = 'Deployment environment'
    private final String titleBuildParameters = 'Build parameters'
    private final String buildApplication = 'Build application'
    private final String deployApplication = 'Deploy application'
    private final String releaseType = 'Release Type'
    private final String runTests = 'Run tests'
    private final String runCodeStyleCheck = 'Run code style check'
    private final String buildPackage = 'Build package'
    private final String publishPackage = 'Publish package'
    private final String masterBranchName = 'master'

    boolean onlyPipelineUpdate = false
    DeployEnvironment deployEnvironment
    PatchLevel patchLevel
    String cluster

    PipelineParameters(script, Logger logger) {
        this.script = script
        this.logger = logger
    }

    void initialize(JenkinsFileSettings jenkinsFileSettings, EnvironmentVariables environmentVariables) {
        mandatoryStages = []
        optionalStages = []
        environments = []

        initializeDefaultStages(jenkinsFileSettings, environmentVariables)

        List params = buildParameters()

        script.properties([this.script.parameters(params)])

        if (environmentVariables.BUILD_NUMBER == '1') {
            onlyPipelineUpdate = true
        } else if (script.params.reload == true) {
            onlyPipelineUpdate = true
        }

        deployEnvironment = script.params[titleDeploymentEnvironment] ? script.params[titleDeploymentEnvironment] as DeployEnvironment : null
        patchLevel = script.params.version_type ?: PatchLevel.PATCH
        cluster = deployEnvironment == DeployEnvironment.prod ? 'prod' : 'stage'

        if (script.params[titleBuildParameters].contains(buildApplication) == false) {
            deleteStage([PipelineStage.BuildApplication, PipelineStage.BuildDockerImage, PipelineStage.DeployApplication,])
        }

        if (script.params[titleBuildParameters].contains(deployApplication) == false) {
            deleteStage([PipelineStage.DeployApplication])
        }

        if (script.params[titleBuildParameters].contains(runTests) == false) {
            deleteStage([PipelineStage.RunTests])
        }

        if (script.params[titleBuildParameters].contains(runCodeStyleCheck) == false) {
            deleteStage([PipelineStage.RunCodeStyleCheck])
        }

        if (script.params[titleBuildParameters].contains(buildPackage) == false) {
            deleteStage([PipelineStage.BuildPackage, PipelineStage.PushPackage])
        }

        if (script.params[titleBuildParameters].contains(publishPackage) == false) {
            deleteStage([PipelineStage.PushPackage])
        }
    }

    boolean stageAvailable(PipelineStage stage) {
        return mandatoryStages.contains(stage) || optionalStages.contains(stage)
    }

    void deleteStage(List<PipelineStage> deleteStages) {
        for (deleteStage in deleteStages) {
            mandatoryStages -= deleteStage
            optionalStages -= deleteStage
        }
    }

    /** Available pipeline options are determined by the service type and branch/tag */
    private List buildParameters() {
        List parameters = []
        List<String> buildVariants = []

        if (stageAvailable(PipelineStage.BuildApplication)) {
            buildVariants.add("\'${buildApplication}:selected${mandatoryStages.contains(PipelineStage.BuildApplication) ? ':disabled' : ''}\'")
        }

        if (stageAvailable(PipelineStage.DeployApplication)) {
            buildVariants.add("\'${deployApplication}:selected${mandatoryStages.contains(PipelineStage.DeployApplication) ? ':disabled' : ''}\'")
        }

        if (stageAvailable(PipelineStage.RunTests)) {
            buildVariants.add("\'${runTests}:selected${mandatoryStages.contains(PipelineStage.RunTests) ? ':disabled' : ''}\'")
        }

        if (stageAvailable(PipelineStage.RunCodeStyleCheck)) {
            buildVariants.add("\'${runCodeStyleCheck}${mandatoryStages.contains(PipelineStage.RunCodeStyleCheck) ? ':selected:disabled' : ''}\'")
        }

        if (stageAvailable(PipelineStage.BuildPackage)) {
            buildVariants.add("\'${buildPackage}:selected${mandatoryStages.contains(PipelineStage.RunCodeStyleCheck) ? ':disabled' : ''}\'")
        }

        if (stageAvailable(PipelineStage.PushPackage)) {
            buildVariants.add("\'${publishPackage}:selected${mandatoryStages.contains(PipelineStage.RunCodeStyleCheck) ? ':disabled' : ''}\'")
        }

        // the order of parameters is important, so the dry run flag should be on top.
        // if you move it to the end, all dependent parameters will stop working
        parameters.add(script.booleanParam(defaultValue: false, description: 'Update pipeline', name: 'reload'))

        parameters.add(script.reactiveChoice(choiceType: 'PT_CHECKBOX', filterLength: 1, filterable: false, name: titleBuildParameters, referencedParameters: 'reload',
                script: script.groovyScript(fallbackScript: [classpath: [], oldScript: '', sandbox: true, script: 'return \'<p>ERROR</p>\''],
                        script: [classpath: [], oldScript: '', sandbox: true, script: '''if (reload) {
                return []
            } else {
                return [''' + buildVariants.join(',') + ''']
            }'''])))

        if (environments) {
            if (stageAvailable(PipelineStage.DeployApplication)) {
                parameters.add(script.reactiveChoice(choiceType: 'PT_RADIO', filterLength: 1, filterable: false, name: titleDeploymentEnvironment, referencedParameters: 'reload',
                        script: script.groovyScript(fallbackScript: [classpath: [], oldScript: '', sandbox: true, script: 'return \'<p>ERROR</p>\''],
                                script: [classpath: [], oldScript: '', sandbox: true, script: '''if (reload) {
                    return []
                } else {
                    return [\'''' + environments.join('\',\'') +'''\']
                }'''])))
            }
        }

        if (stageAvailable(PipelineStage.CreateTag)) {
            parameters.add(script.choice(choices: [PatchLevel.PATCH, PatchLevel.MINOR, PatchLevel.MAJOR], description: releaseType, name: 'version_type'))
        }

        return parameters
    }

    private initializeDefaultStages(JenkinsFileSettings jenkinsFileSettings, EnvironmentVariables environmentVariables) {
        logger.logDebug("PipelineParameters:initializeDefaultStages jenkinsFileSettings.repositoryTypes = ${jenkinsFileSettings.repositoryTypes}")

        for (repositoryType in jenkinsFileSettings.repositoryTypes) {
            switch (repositoryType) {
                case RepositoryType.NugetPackage:
                    if (environmentVariables.BRANCH_NAME == masterBranchName) {
                        mandatoryStages.addAll([PipelineStage.RunTests, PipelineStage.RunCodeStyleCheck, PipelineStage.CreateTag,
                                PipelineStage.BuildPackage, PipelineStage.PushPackage])
                        break
                    }

                    optionalStages.addAll([PipelineStage.RunTests, PipelineStage.RunCodeStyleCheck, PipelineStage.BuildPackage, PipelineStage.PushPackage])
                    break

                case RepositoryType.RawPackage:
                    if (environmentVariables.BRANCH_NAME == masterBranchName) {
                        mandatoryStages.addAll([PipelineStage.RunTests, PipelineStage.RunCodeStyleCheck, PipelineStage.CreateTag,
                                PipelineStage.BuildPackage, PipelineStage.PushPackage])
                        break
                    }

                    optionalStages.addAll([PipelineStage.RunTests, PipelineStage.RunCodeStyleCheck, PipelineStage.BuildPackage, PipelineStage.PushPackage])
                    break

                case RepositoryType.PythonPackage:
                    if (environmentVariables.BRANCH_NAME == masterBranchName) {
                        mandatoryStages.addAll([PipelineStage.RunTests, PipelineStage.RunCodeStyleCheck, PipelineStage.CreateTag,
                                PipelineStage.BuildPackage, PipelineStage.PushPackage])
                        break
                    }

                    optionalStages.addAll([PipelineStage.RunTests, PipelineStage.RunCodeStyleCheck, PipelineStage.BuildPackage, PipelineStage.PushPackage])
                    break

                // By default
                case RepositoryType.Service:
                    mandatoryStages.addAll([PipelineStage.CheckImage])

                    if (environmentVariables.TAG_NAME) {
                        mandatoryStages.addAll([PipelineStage.BuildApplication, PipelineStage.BuildDockerImage, PipelineStage.DeployApplication])
                        // ToDo: wait test environment
                        // environments = [DeployEnvironment.test, DeployEnvironment.preprod, DeployEnvironment.prod]
                        environments.addAll([DeployEnvironment.preprod, DeployEnvironment.prod])
                        break
                    }

                    if (environmentVariables.BRANCH_NAME == masterBranchName) {
                        mandatoryStages.addAll([PipelineStage.RunTests, PipelineStage.RunCodeStyleCheck, PipelineStage.CreateReleaseImage, PipelineStage.BuildApplication, PipelineStage.BuildDockerImage, PipelineStage.CreateTag])
                        break
                    }

                    optionalStages.addAll([PipelineStage.RunTests, PipelineStage.RunCodeStyleCheck, PipelineStage.BuildApplication, PipelineStage.BuildDockerImage, PipelineStage.DeployApplication])
                    // ToDo: wait test environment
                    // environments = [DeployEnvironment.test, DeployEnvironment.preprod]
                    environments.addAll([DeployEnvironment.preprod])
                    break

                case RepositoryType.None:
                    logger.logDebug("PipelineParameters:initializeDefaultStages RepositoryType is None")
                    return []
            }
        }
        logger.logDebug("PipelineParameters:initializeDefaultStages mandatoryStages = ${mandatoryStages}")
        logger.logDebug("PipelineParameters:initializeDefaultStages optionalStages = ${optionalStages}")
        logger.logDebug("PipelineParameters:initializeDefaultStages environments = ${environments}")
    }
}
