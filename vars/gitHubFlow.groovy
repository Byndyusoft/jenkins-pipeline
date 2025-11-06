import jenkins.model.Jenkins

def call(Map serviceSetting = [:], List<String> checks = [], Map k8sCloud = [:], HashMap functions = [:]) {
    Logger logger = new Logger()

    EnvironmentVariables environmentVariables = new EnvironmentVariables(env)

    if (environmentVariables.DEBUG) {
        def tracing = new Tracing()
        tracing.initialize(logger)
    }

    JenkinsFileSettings jenkinsFileSettings = new JenkinsFileSettings()

    jenkinsFileSettings.initialize(serviceSetting)

    final String pipelineVersion = '1.0.2'
    final String configDir = './deploy'

    logger.logInfo('###################################################################')
    logger.logInfo("Version jenkins=${Jenkins.instance.getVersion()}")
    logger.logInfo("Version groovy=${GroovySystem.version}")
    logger.logInfo("Version pipeline=${pipelineVersion}")
    logger.logInfo("Debug mode is env.DEBUG=${environmentVariables.DEBUG}")
    logger.logInfo('###################################################################')

    PipelineParameters pipelineParameters = new PipelineParameters(this, logger)

    pipelineParameters.initialize(jenkinsFileSettings, environmentVariables)

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

    Kubernetes kubernetes = new Kubernetes(this)

    DeployConfig deployConfig = getDeployConfig(kubernetes, configDir, logger)

    KubernetesConfig kubernetesConfig = new KubernetesConfig(k8sCloud, deployConfig, pipelineParameters)

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

            Yaml serviceYaml = new Yaml(readYaml(file: "${configDir}/${jenkinsFileSettings.artifactName}.yaml"))

            ServiceConfig serviceConfig = new ServiceConfig()

            serviceConfig.initialize(serviceYaml)

            Nexus nexus = new Nexus(this, deployConfig, environmentVariables, logger)

            runStage('Nexus initialize', 'docker') {
                nexus.initialize()
            }

            Git git = new Git(this, deployConfig)

            SemanticVersion latestTag = SemanticVersion.parse(git.findLatestSemVerTag())
            SemanticVersion releaseVersion = latestTag.increase(pipelineParameters.patchLevel)
            
            ArtifactSettings artifactSettings = new ArtifactSettings()
            artifactSettings.initialize(deployConfig, jenkinsFileSettings, environmentVariables, pipelineParameters,
                    git, releaseVersion)

            String version = pipelineParameters.stageAvailable(PipelineStage.CreateTag) \
              ? releaseVersion.toString() \
              : latestTag.toPreReleaseVersion(
                  environmentVariables.BRANCH_NAME,
                  environmentVariables.BUILD_NUMBER,
                  artifactSettings.gitCommitShort
                )

            Make make = new Make(this, serviceConfig, logger)

            if (pipelineParameters.stageAvailable(PipelineStage.CheckImage)) {
                runStage('Check image exists', 'docker') {
                    if (nexus.checkImage(artifactSettings)) {
                        pipelineParameters.deleteStage([PipelineStage.RunTests, PipelineStage.RunCodeStyleCheck, PipelineStage.BuildApplication, PipelineStage.BuildDockerImage])
                    }
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

            if (pipelineParameters.stageAvailable(PipelineStage.BuildDockerImage)) {
                runStage('Build image', 'docker') {
                    make.buildImage(deployConfig, artifactSettings)
                }

                runStage('Push image', 'docker') {
                    nexus.pushImage(artifactSettings)
                }
            }

            if (pipelineParameters.stageAvailable(PipelineStage.CreateReleaseImage)) {
                runStage('Push release image', 'docker') {
                    nexus.createReleaseImage(artifactSettings)
                }
            }

            if (pipelineParameters.stageAvailable(PipelineStage.BuildPackage)) {
                runStage('Pack package', 'docker') {
                    make.packPackage(version)
                }

                if (pipelineParameters.stageAvailable(PipelineStage.PushPackage)) {
                    runStage('Push package', 'docker') {
                        nexus.pushPackage(jenkinsFileSettings, serviceConfig)
                    }
                }
            }

            if (pipelineParameters.stageAvailable(PipelineStage.DeployApplication)) {
                Helm helm = new Helm(this, logger)

                stage('Prepare microservice yaml configs') {
                    Yaml commonYaml = null
                    String commonYamlPath = "${configDir}/common.yaml"

                    if (fileExists(commonYamlPath)) {
                        commonYaml = new Yaml(readYaml(file: commonYamlPath))
                    }

                    helm.prepareServiceYamlConfigs(deployConfig, serviceConfig, commonYaml,
                            jenkinsFileSettings, pipelineParameters, artifactSettings)
                }

                runStage("Deployment $jenkinsFileSettings.artifactName to ${pipelineParameters.deployEnvironment}", 'helm') {
                    helm.deployApplication(deployConfig, serviceConfig, artifactSettings, environmentVariables)
                }
            }

            if (pipelineParameters.stageAvailable(PipelineStage.CreateTag)) {
                runStage('Make release', 'docker') {
                    git.createTag(releaseVersion)
                }
            }
        }
    }
}

private DeployConfig getDeployConfig(Kubernetes kubernetes, String configDir, Logger logger) {
    KubernetesConfig customConfig = new KubernetesConfig([cloud: 'kubernetes', podTemplateContainer: ['jnlp']], null, null)

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
