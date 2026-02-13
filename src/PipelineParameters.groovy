/** Pipeline Launch Settings Class */
class PipelineParameters {
    private final def script
    private final Logger logger

    private List<PipelineStage> mandatoryStages
    private List<PipelineStage> optionalStages
    private List<String> environments

    private final String titleDeploymentEnvironment = 'Deployment environment'
    private final String titleBuildParameters = 'Build parameters'
    private final String buildApplication = 'Build application'
    private final String deployApplication = 'Deploy application'
    private final String releaseType = 'Release Type'
    private final String runTests = 'Run tests'
    private final String runCodeStyleCheck = 'Run code style check'
    private final String packPackage = 'Pack package'
    private final String publishPackage = 'Publish package'
    private final String masterBranchName = 'master'

    boolean onlyPipelineUpdate = false
    String deployEnvironment
    PatchLevel patchLevel
    String cluster

    PipelineParameters(script, Logger logger) {
        this.script = script
        this.logger = logger
    }

    void initialize(DeployConfig deployConfig, EnvironmentVariables environmentVariables, List<ArtifactType> artifactsTypes) {
        mandatoryStages = []
        optionalStages = []
        environments = []

        initializeDefaultStages(deployConfig, environmentVariables, artifactsTypes)

        List params = buildParameters()

        script.properties([this.script.parameters(params)])

        if (environmentVariables.BUILD_NUMBER == '1') {
            onlyPipelineUpdate = true
        } else if (script.params.reload == true) {
            onlyPipelineUpdate = true
        }

        deployEnvironment = script.params[titleDeploymentEnvironment]

        patchLevel = script.params.version_type ?: PatchLevel.PATCH

        cluster = deployEnvironment == DeployEnvironment.prod.name() ? 'prod' : 'stage'

        if (script.params[titleBuildParameters].contains(buildApplication) == false) {
            deleteStage([PipelineStage.InstallDependencies, PipelineStage.RunTests, PipelineStage.BuildApplication, PipelineStage.PackApplication, PipelineStage.BuildDockerImage, PipelineStage.DeployApplication, PipelineStage.PackPackage, PipelineStage.PushPackage])
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

        if (script.params[titleBuildParameters].contains(packPackage) == false) {
            deleteStage([PipelineStage.PackPackage, PipelineStage.PushPackage])
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
            buildVariants.add("\'<b>${buildApplication}</b>:selected${mandatoryStages.contains(PipelineStage.BuildApplication) ? ':disabled' : ''}\'")
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

        if (stageAvailable(PipelineStage.PackPackage)) {
            buildVariants.add("\'${packPackage}:selected${mandatoryStages.contains(PipelineStage.RunCodeStyleCheck) ? ':disabled' : ''}\'")
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
                                script: [classpath: [], oldScript: '', sandbox: true, script: """if (reload) {
                    return []
                } else {
                    return [${Utils.toJenkinsChoiceFormat(environments)}]
                }"""])))
            }
        }

        if (stageAvailable(PipelineStage.CreateTag)) {
            parameters.add(script.choice(choices: [PatchLevel.PATCH, PatchLevel.MINOR, PatchLevel.MAJOR], description: releaseType, name: 'version_type'))
        }

        return parameters
    }

    private initializeDefaultStages(DeployConfig deployConfig, EnvironmentVariables environmentVariables, List<ArtifactType> artifactsTypes) {
        logger.logDebug("PipelineParameters:initializeDefaultStages artifactsTypes = ${artifactsTypes}")

        for (artifactType in artifactsTypes) {
            switch (artifactType) {
                case ArtifactType.NugetPackage:
                    if (environmentVariables.BRANCH_NAME == masterBranchName) {
                        mandatoryStages.addAll([PipelineStage.RunTests, PipelineStage.RunCodeStyleCheck, PipelineStage.CreateTag,
                                PipelineStage.PackPackage, PipelineStage.PushPackage])
                        break
                    }

                    optionalStages.addAll([PipelineStage.RunTests, PipelineStage.RunCodeStyleCheck, PipelineStage.PackPackage, PipelineStage.PushPackage])
                    break

                case ArtifactType.RawPackage:
                    if (environmentVariables.BRANCH_NAME == masterBranchName) {
                        mandatoryStages.addAll([PipelineStage.RunTests, PipelineStage.RunCodeStyleCheck, PipelineStage.CreateTag,
                                PipelineStage.PackPackage, PipelineStage.PushPackage])
                        break
                    }

                    optionalStages.addAll([PipelineStage.RunTests, PipelineStage.RunCodeStyleCheck, PipelineStage.PackPackage, PipelineStage.PushPackage])
                    break

                case ArtifactType.PythonPackage:
                    if (environmentVariables.BRANCH_NAME == masterBranchName) {
                        mandatoryStages.addAll([PipelineStage.RunTests, PipelineStage.RunCodeStyleCheck, PipelineStage.CreateTag,
                                PipelineStage.PackPackage, PipelineStage.PushPackage])
                        break
                    }

                    optionalStages.addAll([PipelineStage.RunTests, PipelineStage.RunCodeStyleCheck, PipelineStage.PackPackage, PipelineStage.PushPackage])
                    break

                // By default
                case ArtifactType.Service:
                    mandatoryStages.addAll([PipelineStage.CheckImage])

                    if (environmentVariables.TAG_NAME) {
                        mandatoryStages.addAll([PipelineStage.InstallDependencies, PipelineStage.BuildApplication, PipelineStage.PackApplication, PipelineStage.BuildDockerImage, PipelineStage.DeployApplication])
                        environments.addAll(deployConfig.additionalDeployEnvironments)
                        environments.addAll([DeployEnvironment.preprod.name(), DeployEnvironment.prod.name()])
                        break
                    }

                    if (environmentVariables.BRANCH_NAME == masterBranchName) {
                        mandatoryStages.addAll([PipelineStage.InstallDependencies, PipelineStage.RunTests, PipelineStage.RunCodeStyleCheck, PipelineStage.CreateReleaseImage, PipelineStage.BuildApplication, PipelineStage.PackApplication, PipelineStage.BuildDockerImage, PipelineStage.CreateTag])
                        break
                    }

                    optionalStages.addAll([PipelineStage.InstallDependencies, PipelineStage.RunTests, PipelineStage.RunCodeStyleCheck, PipelineStage.BuildApplication, PipelineStage.PackApplication, PipelineStage.BuildDockerImage, PipelineStage.DeployApplication])
                    environments.addAll(deployConfig.additionalDeployEnvironments)
                    environments.add(DeployEnvironment.preprod.name())
                    break

                case ArtifactType.None:
                    logger.logDebug("PipelineParameters:initializeDefaultStages ArtifactType is None")
                    return []
            }
        }

        logger.logDebug("PipelineParameters:initializeDefaultStages mandatoryStages = ${mandatoryStages}")
        logger.logDebug("PipelineParameters:initializeDefaultStages optionalStages = ${optionalStages}")
        logger.logDebug("PipelineParameters:initializeDefaultStages environments = ${environments}")
    }
}
