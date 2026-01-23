import jenkins.model.Jenkins

def call(Map repositorySetting = [:], Map k8sCloud = [:]) {
    Logger logger = new Logger()

    EnvironmentVariables environmentVariables = new EnvironmentVariables(env)

    if (environmentVariables.DEBUG) {
        def tracing = new Tracing()
        tracing.initialize(logger)
    }

    JenkinsFileSettings jenkinsFileSettings = new JenkinsFileSettings()

    jenkinsFileSettings.initialize(repositorySetting)

    final String pipelineVersion = '2.0.0'
    final String configDir = './deploy'

    logger.logInfo('###################################################################')
    logger.logInfo("Version jenkins=${Jenkins.instance.getVersion()}")
    logger.logInfo("Version groovy=${GroovySystem.version}")
    logger.logInfo("Version pipeline=${pipelineVersion}")
    logger.logInfo("Debug mode is env.DEBUG=${environmentVariables.DEBUG}")
    logger.logInfo('###################################################################')

    Kubernetes kubernetes = new Kubernetes(this)
    DeployConfig deployConfig = getDeployConfig(kubernetes, configDir, logger)

    PipelineParameters pipelineParameters = new PipelineParameters(this, logger)
    pipelineParameters.initialize(jenkinsFileSettings, environmentVariables, deployConfig)

    logger.logInfo('###################################################################')
    logger.logInfo("Deploy to cluster=${pipelineParameters.cluster}")
    logger.logInfo("Pipeline parameters \"deploy environment\" is pipelineParameters.deployEnvironment=${pipelineParameters.deployEnvironment}")
    logger.logInfo('###################################################################')

    if (pipelineParameters.onlyPipelineUpdate) {
        logger.logInfo('Pipeline parameters updated, ignore build, exit from pipeline')
        return
    }

    if (pipelineParameters.stageAvailable(PipelineStage.DeployApplication)) {
        if (pipelineParameters.deployEnvironment == null) {
            logger.logInfo("Pipeline parameters deploy environment is not set. pipelineParameters.deployEnvironment = ${pipelineParameters.deployEnvironment}")
            currentBuild.result = 'FAILURE'
            return
        }
    }

    KubernetesConfig kubernetesConfig = new KubernetesConfig()
    kubernetesConfig.initialize(k8sCloud, deployConfig, pipelineParameters)

    kubernetes.customPodTemplate(kubernetesConfig) {
        node(POD_LABEL) {
            /**
                ToDo
                Fixed: docker: Cannot connect to the Docker daemon at unix:///var/run/docker.sock.
                Is the docker daemon running?.
            */
            sleep(10)

            stage('Checkout SCM') {
                checkout scm
            }

            // def currentDirectoryPath = pwd()
            def artifactsVariables = [:]

            def fileIndir = findFiles(glob: "deploy/*").collect { file -> file.name }
            def excludedFileName = ["common.yaml", "deploy.yaml"]

            Utils utils = new Utils()
            Git git = new Git(this, deployConfig)

            SemanticVersion latestTag = git.findLatestSemVerTag()
            SemanticVersion releaseVersion = new SemanticVersion(latestTag.toString())

            String version
            if (pipelineParameters.stageAvailable(PipelineStage.CreateTag)) {
                releaseVersion.increaseVersion(pipelineParameters.patchLevel)
                version = releaseVersion.toString()
            } else {
                def getCurrentTagForBranch = git.getCurrentTagForBranch()
                version = "${getCurrentTagForBranch != null ? getCurrentTagForBranch.toString() : latestTag.toString()}-${utils.prepareName(environmentVariables.BRANCH_NAME)}-${environmentVariables.BUILD_NUMBER}-${git.getCommitShaShort()}"
            }

            ArtifactCommonSettings artifactCommonSettings = new ArtifactCommonSettings()

            artifactCommonSettings.initialize(deployConfig, jenkinsFileSettings, environmentVariables, pipelineParameters, git, releaseVersion)

            Nexus nexus = new Nexus(this, deployConfig, environmentVariables, logger)

            runStage('Nexus initialize', 'docker') {
                nexus.initialize()
            }

            for (fileName in fileIndir) {
                if (!excludedFileName.contains(fileName)) {
                    logger.logInfo("fileName=${fileName}")

                    ServiceConfig serviceConfig = new ServiceConfig()
                    Yaml serviceYaml = new Yaml(readYaml(file: "${configDir}/${fileName}"))
                    serviceConfig.initialize(serviceYaml)

                    String microserviceName = fileName.split("\\.")[0]

                    if (!serviceConfig.artifactSetting.get('enabled')) {
                        continue
                    }

                    artifactsVariables.put("${microserviceName}", [
                        "artifactName": "${microserviceName}",
                        "serviceConfig": serviceConfig,
                        "outputDir": "./out/${microserviceName}",
                        "fullImagePath": "${deployConfig.registryProvider.registryImagePushUrl}/${deployConfig.projectName}/${artifactCommonSettings.imageFolder}/${microserviceName}:${artifactCommonSettings.imageTag}",
                        "releaseFullImagePath": "${deployConfig.registryProvider.registryImagePushUrl}/${deployConfig.projectName}/${artifactCommonSettings.releaseImageFolder}/${microserviceName}:${artifactCommonSettings.releaseTag}"
                    ])

                    logger.logInfo("artifactsVariables=${artifactsVariables}")
                }
            }

            CommonConfig commonConfig = new CommonConfig()
            Yaml commonYaml = new Yaml(readYaml(file: "${configDir}/common.yaml"))
            commonConfig.initialize(commonYaml)

            Make make = new Make(this, commonConfig, logger)

            if (pipelineParameters.stageAvailable(PipelineStage.CheckImage)) {
                runStage('Check image exists', 'docker') {
                    artifactsVariables.each{ artifactName, artifactVariables ->
                        if (nexus.checkImage(artifactCommonSettings, artifactName)) {
                            pipelineParameters.deleteStage([InstallDependencies, PipelineStage.RunTests, PipelineStage.RunCodeStyleCheck, PipelineStage.BuildApplication, PackApplication, PipelineStage.BuildDockerImage])
                        }
                    }
                }
            }

            if (pipelineParameters.stageAvailable(PipelineStage.InstallDependencies)) {
                runStage('Install dependencies', 'docker') {
                    make.installDependencies()
                }
            }

            if (pipelineParameters.stageAvailable(PipelineStage.BuildApplication)) {
                runStage('Build application', 'docker') {
                    make.buildApplication(version)
                }
            }

            if (pipelineParameters.stageAvailable(PipelineStage.RunTests)) {
                runStage('Unit test', 'docker') {
                    make.runUnitTests()
                }
            }

            if (pipelineParameters.stageAvailable(PipelineStage.RunCodeStyleCheck)) {
                runStage('Style checks', 'docker') {
                    make.runStyleChecks()
                }
            }

            artifactsVariables.each{ artifactName, artifactVariables ->
                if (pipelineParameters.stageAvailable(PipelineStage.PackApplication)) {
                    runStage('Pack application', 'docker') {
                        make.packApplication(artifactVariables)
                    }
                }

                if (pipelineParameters.stageAvailable(PipelineStage.BuildDockerImage)) {
                    runStage('Build image', 'docker') {
                        make.buildImage(artifactVariables)
                    }

                    runStage('Push image', 'docker') {
                        nexus.pushImage(artifactVariables)
                    }
                }

                if (pipelineParameters.stageAvailable(PipelineStage.CreateReleaseImage)) {
                    runStage('Push release image', 'docker') {
                        nexus.createReleaseImage(artifactCommonSettings, artifactVariables)
                    }
                }

                if (pipelineParameters.stageAvailable(PipelineStage.PackPackage)) {
                    runStage('Pack package', 'docker') {
                        make.packPackage(version)
                    }

                    if (pipelineParameters.stageAvailable(PipelineStage.PushPackage)) {
                        runStage('Push package', 'docker') {
                            nexus.pushPackage(jenkinsFileSettings, artifactVariables)
                        }
                    }
                }
            }

            // if (pipelineParameters.stageAvailable(PipelineStage.DeployApplication)) {
            //     Helm helm = new Helm(this, logger)

            //     stage('Prepare microservice yaml configs') {
            //         Yaml commonYaml = null
            //         String commonYamlPath = "${configDir}/common.yaml"

            //         if (fileExists(commonYamlPath)) {
            //             commonYaml = new Yaml(readYaml(file: commonYamlPath))
            //         }

            //         helm.prepareServiceYamlConfigs(deployConfig, artifactVariables.get('serviceConfig'), commonYaml,
            //                 jenkinsFileSettings, pipelineParameters, artifactCommonSettings)
            //     }

            //     runStage("Deployment $jenkinsFileSettings.artifactName to ${pipelineParameters.deployEnvironment}", 'helm') {
            //         helm.deployApplication(deployConfig, artifactVariables.get('serviceConfig'), artifactCommonSettings, environmentVariables)
            //     }
            // }

            if (pipelineParameters.stageAvailable(PipelineStage.CreateTag)) {
                runStage('Make release', 'docker') {
                    git.createTag(releaseVersion)
                }
            }
        }
    }
}

private DeployConfig getDeployConfig(Kubernetes kubernetes, String configDir, Logger logger) {
    KubernetesConfig customConfig = new KubernetesConfig()
    customConfig.initialize([cloud: 'kubernetes', podTemplateContainer: ['jnlp']], null, null)

    return kubernetes.customPodTemplate(customConfig) {
        node(POD_LABEL) {
            stage('Get deploy config') {
                checkout scm

                if (!fileExists(configDir)) {
                    currentBuild.result = 'FAILURE'
                    return
                }

                Yaml deployYaml = new Yaml(readYaml(file: "${configDir}/deploy.yaml"))
                DeployConfig deployConfig = new DeployConfig(logger)

                deployConfig.initialize(deployYaml)

                return deployConfig
            }
        }
    } as DeployConfig
}

private def runStage(String stageName, String containerName, Closure code) {
    return stage(stageName) {
        return container(containerName) {
            return code()
        }
    }
}
