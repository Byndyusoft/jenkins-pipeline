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
    private final String buildPackage = 'Build package'
    private final String publishPackage = 'Publish package'
    private final String masterBranchName = 'master'

    boolean onlyPipelineUpdate = false
    boolean makeRelease = false
    String deployEnvironment
    PatchLevel patchLevel
    String cluster

    PipelineParameters(script, Logger logger) {
        this.script = script
        this.logger = logger
    }

    void initialize(JenkinsFileSettings jenkinsFileSettings, EnvironmentVariables environmentVariables, DeployConfig deployConfig) {
        mandatoryStages = []
        optionalStages = []
        environments = []

        def makeReleaseParam = script.params['make_release']
        if (makeReleaseParam != null) {
            makeRelease = makeReleaseParam.toString().contains('Make Release')
        } else {
            makeRelease = (environmentVariables.BRANCH_NAME == masterBranchName)
        }


        initializeDefaultStages(jenkinsFileSettings, environmentVariables, deployConfig)

        List params = buildParameters()

        script.properties([this.script.parameters(params)])

        if (environmentVariables.BUILD_NUMBER == '1') {
            onlyPipelineUpdate = true
        } else if (script.params.reload == true) {
            onlyPipelineUpdate = true
        }

        if (!deployEnvironment) {
            deployEnvironment = script.params[titleDeploymentEnvironment]
        }

        if (makeRelease && !deployEnvironment && environments) {
            def preprodName = DeployEnvironment.preprod.name()
            deployEnvironment = environments.contains(preprodName) ? preprodName : environments[0]
            logger.logDebug("PipelineParameters: auto-selecting environment for release: ${deployEnvironment}")
        }

        def versionTypeParam = script.params.version_type
        patchLevel = versionTypeParam ? PatchLevel.valueOf(versionTypeParam.toString()) : PatchLevel.PATCH

        cluster = deployEnvironment == DeployEnvironment.prod.name() ? 'prod' : 'stage'

        if (!makeRelease) {

            deleteStage([PipelineStage.CreateTag, PipelineStage.CreateReleaseImage])

            if (script.params[titleBuildParameters].contains(buildApplication) == false) {
                deleteStage([PipelineStage.BuildApplication, PipelineStage.BuildDockerImage, PipelineStage.DeployApplication])
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

        if (script.env.BRANCH_NAME == masterBranchName) {
            parameters.add(script.reactiveChoice(
                choiceType        : 'PT_CHECKBOX',
                filterable        : false,
                filterLength      : 1,
                name              : 'make_release',
                description       : 'Make Release: full release cycle with tag creation. When unchecked — deploy to non-prod without tag creating.',
                referencedParameters: 'reload',
                script            : script.groovyScript(
                    fallbackScript: [classpath: [], oldScript: '', sandbox: true, script: "return ['Make Release:selected']"],
                    script        : [classpath: [], oldScript: '', sandbox: true, script: """
                        def isReload = (reload?.toString() == 'true')
                        if (isReload) {
                            return []
                        }
                        return ['Make Release:selected']
                    """]
                )
            ))
        }

        parameters.add(script.reactiveChoice(
            choiceType: 'PT_CHECKBOX', 
            filterLength: 1, 
            filterable: false, 
            name: titleBuildParameters, 
            referencedParameters: 'reload,make_release',
            script: script.groovyScript(
                fallbackScript: [classpath: [], oldScript: '', sandbox: true, script: 'return ["<p>ERROR</p>"]'],
                script: [classpath: [], oldScript: '', sandbox: true, script: """
                    def isReload = (reload?.toString() == 'true')
                    def isRelease = make_release?.toString()?.contains('Make Release')

                    if (isReload || isRelease) {
                        return []
                    } else {
                        return [${buildVariants.join(',')}]
                    }
                """]
            )
        ))

        if (environments) {            
            parameters.add(script.reactiveChoice(
                choiceType: 'PT_RADIO', 
                filterLength: 1, 
                filterable: false, 
                name: titleDeploymentEnvironment, 
                referencedParameters: 'reload,make_release',
                script: script.groovyScript(
                    fallbackScript: [classpath: [], oldScript: '', sandbox: true, script: 'return ["<p>ERROR</p>"]'],
                    script: [classpath: [], oldScript: '', sandbox: true, script: """
                        def isReload = (reload?.toString() == 'true')
                        def isRelease = make_release?.toString()?.contains('Make Release')

                        if (isReload || isRelease) {
                            return []
                        } else {
                            return [${Utils.toJenkinsChoiceFormat(environments)}]
                        }
                    """]
                )
            ))
        }

        if (stageAvailable(PipelineStage.CreateTag)) {
            parameters.add(script.choice(
                choices: [PatchLevel.PATCH, PatchLevel.MINOR, PatchLevel.MAJOR], 
                description: 'Release version increment (only for Make Release)', 
                name: 'version_type'
            ))
        }

        return parameters
    }

    private initializeDefaultStages(JenkinsFileSettings jenkinsFileSettings, EnvironmentVariables environmentVariables, DeployConfig deployConfig) {
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
                        environments.addAll(deployConfig.additionalDeployEnvironments)
                        environments.addAll([DeployEnvironment.preprod.name(), DeployEnvironment.prod.name()])
                        break
                    }

                    if (environmentVariables.BRANCH_NAME == masterBranchName) {
                        optionalStages.add(PipelineStage.CreateTag) 
                        optionalStages.addAll([PipelineStage.RunTests, PipelineStage.RunCodeStyleCheck, PipelineStage.BuildApplication, PipelineStage.BuildDockerImage, PipelineStage.DeployApplication])
                        environments.addAll(deployConfig.additionalDeployEnvironments)
                        environments.add(DeployEnvironment.preprod.name())

                        if (makeRelease) {
                            mandatoryStages.addAll([PipelineStage.RunTests, PipelineStage.RunCodeStyleCheck, PipelineStage.CreateReleaseImage, PipelineStage.BuildApplication, PipelineStage.BuildDockerImage, PipelineStage.CreateTag])
                        }
                        break
                    }

                    optionalStages.addAll([PipelineStage.RunTests, PipelineStage.RunCodeStyleCheck, PipelineStage.BuildApplication, PipelineStage.BuildDockerImage, PipelineStage.DeployApplication])
                    environments.addAll(deployConfig.additionalDeployEnvironments)
                    environments.add(DeployEnvironment.preprod.name())
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
