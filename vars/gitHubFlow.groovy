import jenkins.model.Jenkins

def call() {
    Logger logger = new Logger()

    EnvironmentVariables environmentVariables = new EnvironmentVariables(env)

    if (environmentVariables.DEBUG) {
        def tracing = new Tracing()
        tracing.initialize(logger)
    }

    final String pipelineVersion = '2.0.0'
    final String configDir = './deploy'

    logger.logInfo('###################################################################')
    logger.logInfo("Version jenkins=${Jenkins.instance.getVersion()}")
    logger.logInfo("Version groovy=${GroovySystem.version}")
    logger.logInfo("Version pipeline=${pipelineVersion}")
    logger.logInfo("Debug mode is env.DEBUG=${environmentVariables.DEBUG}")
    logger.logInfo('###################################################################')

    Kubernetes kubernetes = new Kubernetes(this)

    KubernetesConfig customConfig = new KubernetesConfig()
    customConfig.initialize([cloud: 'kubernetes', podTemplateContainer: ['jnlp']], null, null)

    DeployConfig deployConfig = new DeployConfig(logger)

    Map artifactsVariables = [:]
    List<ArtifactType> artifactsTypes = []

    Utils utils = new Utils()

    kubernetes.customPodTemplate(customConfig) {
        node(POD_LABEL) {
            stage('Get configs') {
                checkout scm

                if (!fileExists(configDir)) {
                    currentBuild.result = 'FAILURE'
                    return
                }

                Yaml deployYaml = new Yaml(readYaml(file: "${configDir}/deploy.yaml"))
                deployConfig.initialize(deployYaml)

                def fileIndir = findFiles(glob: "deploy/*").collect { file -> file.name }
                def excludedFileName = ["common.yaml", "deploy.yaml"]

                for (fileName in fileIndir) {
                    if (!excludedFileName.contains(fileName)) {
                        logger.logDebug("fileName=${fileName}")

                        ServiceConfig serviceConfig = new ServiceConfig()
                        Yaml serviceYaml = new Yaml(readYaml(file: "${configDir}/${fileName}"))
                        serviceConfig.initialize(serviceYaml)

                        if (!serviceConfig.artifactSetting.get('enabled')) {
                            continue
                        }

                        String microserviceName = fileName.split("\\.")[0]

                        List<ArtifactType> artifactTypes = utils.mapArtifactType(serviceConfig.artifactSetting.get('type') as List<String> ?: [])

                        artifactsTypes.addAll(artifactTypes.flatten())

                        artifactsVariables.put("${microserviceName}", [
                            "artifactTypes": artifactTypes,
                            "artifactName": microserviceName,
                            "serviceConfig": serviceConfig,
                            "outputDir": "./out/${microserviceName}"
                        ])
                    }
                }

                logger.logDebug("artifactsVariables=${artifactsVariables}")
                artifactsTypes = artifactsTypes.unique()
            }
        }
    }

    PipelineParameters pipelineParameters = new PipelineParameters(this, logger)
    pipelineParameters.initialize(deployConfig, environmentVariables, artifactsTypes)

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
    kubernetesConfig.initialize([:], deployConfig, pipelineParameters)

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

            Git git = new Git(this, deployConfig)

            SemanticVersion latestTag = git.findLatestSemVerTag()
            SemanticVersion releaseVersion = new SemanticVersion(latestTag.toString())

            String artifactVersion
            if (pipelineParameters.stageAvailable(PipelineStage.CreateTag)) {
                releaseVersion.increaseVersion(pipelineParameters.patchLevel)
                artifactVersion = releaseVersion.toString()
            } else {
                def getCurrentTagForBranch = git.getCurrentTagForBranch()
                artifactVersion = "${getCurrentTagForBranch != null ? getCurrentTagForBranch.toString() : latestTag.toString()}-${utils.prepareName(environmentVariables.BRANCH_NAME)}-${environmentVariables.BUILD_NUMBER}-${git.getCommitShaShort()}"
            }

            ArtifactCommonSettings artifactCommonSettings = new ArtifactCommonSettings()
            artifactCommonSettings.initialize(deployConfig, environmentVariables, pipelineParameters, git, releaseVersion, artifactVersion)

            Nexus nexus = new Nexus(this, deployConfig, environmentVariables, logger)

            runStage('Nexus initialize', 'docker') {
                nexus.initialize()
            }

            CommonConfig commonConfig = new CommonConfig()
            Yaml commonYaml = null
            if (fileExists("${configDir}/common.yaml")) {
                commonYaml = new Yaml(readYaml(file: "${configDir}/common.yaml"))
            }
            commonConfig.initialize(commonYaml)

            Make make = new Make(this, commonConfig, logger)

            if (pipelineParameters.stageAvailable(PipelineStage.CheckImage)) {
                runStage('Check image exists', 'docker') {
                    boolean artifactNotExist = false
                    artifactsVariables.each { artifactName, artifactVariables ->
                        if (!artifactVariables.get('artifactTypes').disjoint([ArtifactType.Service])) {
                            if (!nexus.checkImage(artifactCommonSettings, artifactName)) {
                                artifactNotExist = true
                                logger.logInfo("Microservice ${artifactName} image does not exist")
                                return true // each break
                            }
                        }
                    }

                    if (!artifactNotExist) {
                        pipelineParameters.deleteStage([PipelineStage.InstallDependencies, PipelineStage.RunTests, PipelineStage.RunCodeStyleCheck, PipelineStage.BuildApplication, PipelineStage.PackApplication, PipelineStage.BuildDockerImage])
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
                    make.buildApplication(artifactVersion)
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

            if (pipelineParameters.stageAvailable(PipelineStage.PackApplication)) {
                runStage('Pack application', 'docker') {
                    artifactsVariables.each { artifactName, artifactVariables ->
                        if (!artifactVariables.get('artifactTypes').disjoint([ArtifactType.Service])) {
                            make.packApplication(artifactVariables)
                        }
                    }
                }
            }

            if (pipelineParameters.stageAvailable(PipelineStage.BuildDockerImage)) {
                runStage('Build image', 'docker') {
                    artifactsVariables.each { artifactName, artifactVariables ->
                        if (!artifactVariables.get('artifactTypes').disjoint([ArtifactType.Service])) {
                            make.buildImage(deployConfig, artifactCommonSettings, artifactVariables)
                        }
                    }
                }

                runStage('Push image', 'docker') {
                    artifactsVariables.each { artifactName, artifactVariables ->
                        if (!artifactVariables.get('artifactTypes').disjoint([ArtifactType.Service])) {
                            nexus.pushImage(artifactCommonSettings, artifactName)
                        }
                    }
                }
            }

            if (pipelineParameters.stageAvailable(PipelineStage.CreateReleaseImage)) {
                runStage('Push release image', 'docker') {
                    artifactsVariables.each { artifactName, artifactVariables ->
                        if (!artifactVariables.get('artifactTypes').disjoint([ArtifactType.Service])) {
                            nexus.createReleaseImage(artifactCommonSettings, artifactName)
                        }
                    }
                }
            }

            if (pipelineParameters.stageAvailable(PipelineStage.PackPackage)) {
                runStage('Pack package', 'docker') {
                    artifactsVariables.each { artifactName, artifactVariables ->
                        if (!artifactVariables.get('artifactTypes').disjoint([ArtifactType.NugetPackage, ArtifactType.PythonPackage, ArtifactType.RawPackage])) {
                            make.packPackage(artifactVersion, artifactVariables)
                        }
                    }
                }

                if (pipelineParameters.stageAvailable(PipelineStage.PushPackage)) {
                    runStage('Push package', 'docker') {
                        artifactsVariables.each { artifactName, artifactVariables ->
                            if (!artifactVariables.get('artifactTypes').disjoint([ArtifactType.NugetPackage, ArtifactType.PythonPackage, ArtifactType.RawPackage])) {
                                nexus.pushPackage(artifactVariables)
                            }
                        }
                    }
                }
            }

            if (pipelineParameters.stageAvailable(PipelineStage.DeployApplication)) {
                Helm helm = new Helm(this, logger)

                stage('Prepare yaml configs') {
                    artifactsVariables.each { artifactName, artifactVariables ->
                        if (!artifactVariables.get('artifactTypes').disjoint([ArtifactType.Service])) {
                            helm.prepareServiceYamlConfigs(deployConfig, commonConfig, artifactVariables, artifactCommonSettings)
                        }
                    }
                }

                runStage("Deployment to ${pipelineParameters.deployEnvironment}", 'helm') {
                    helm.deployApplication(deployConfig, commonConfig, artifactCommonSettings, environmentVariables)
                }
            }

            if (pipelineParameters.stageAvailable(PipelineStage.CreateTag)) {
                runStage('Make release', 'docker') {
                    git.createTag(artifactCommonSettings.releaseVersion)
                }
            }
        }
    }
}

private def runStage(String stageName, String containerName, Closure code) {
    return stage(stageName) {
        return container(containerName) {
            return code()
        }
    }
}
