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

    final String pipelineVersion = '1.0.5'
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

            SemanticVersion latestTag = git.findLatestSemVerTag()
            SemanticVersion releaseVersion = new SemanticVersion(latestTag.toString())
            releaseVersion.increaseVersion(pipelineParameters.patchLevel)

            ArtifactSettings artifactSettings = new ArtifactSettings()
            artifactSettings.initialize(deployConfig, jenkinsFileSettings, environmentVariables, pipelineParameters,
                    git, releaseVersion)

            String version
            if (pipelineParameters.stageAvailable(PipelineStage.CreateTag)) {
                version = releaseVersion.toString()
            } else {
                Utils utils = new Utils()
                def getCurrentTagForBranch = git.getCurrentTagForBranch()
                version = "${getCurrentTagForBranch != null ? getCurrentTagForBranch.toString() : latestTag.toString()}-${utils.prepareName(environmentVariables.BRANCH_NAME)}-${environmentVariables.BUILD_NUMBER}-${artifactSettings.gitCommitShort}"
            }

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

            if (pipelineParameters.stageAvailable(PipelineStage.RunAutoTests)) {
                runStage("Running Remote Autotests", 'jnlp') {
                    String autoTestsJob = jenkinsFileSettings.autotestsJobPath

                    if (!autoTestsJob) {
                        error("RunAutoTests stage is enabled but 'autotests_job_path' is not set in Jenkinsfile")
                    }

                    List jobParameters = [
                        string(name: jenkinsFileSettings.autotestsEnvParamName, value: pipelineParameters.deployEnvironment)
                    ] + (jenkinsFileSettings.autotestsJobParameters ?: [])

                    logger.logInfo("Triggering autotests: job=${autoTestsJob}")
                    
                    int maxAttempts = 3
                    int attempt = 0
                    boolean success = false

                    while (!success) {
                        attempt++
                        try {
                            build(
                                job: autoTestsJob,
                                parameters: jobParameters,
                                wait: true,
                                propagate: true
                            )
                            success = true

                        } catch (org.jenkinsci.plugins.workflow.steps.FlowInterruptedException e) {
                            if (attempt < maxAttempts) {
                                logger.logWarning("Attempt ${attempt}/${maxAttempts}: autotests aborted (likely preempted VM). Retrying in 30s...")
                                sleep(30)
                            } else {
                                logger.logError("All ${maxAttempts} attempts aborted. Giving up.")
                                throw e
                            }

                        } catch (Exception e) {
                            logger.logError("Attempt ${attempt}: autotests failed with application/test error. No retry.")
                            throw e
                        }
                    }
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
