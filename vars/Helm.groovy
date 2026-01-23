/** Class for working with helm */
class Helm {
    private final def script
    private final int deployTimeoutSeconds
    private final Logger logger

    Helm(script, Logger logger) {
        this.script = script
        this.logger = logger
        deployTimeoutSeconds = 300
    }

    void deployApplication(DeployConfig deployConfig, ServiceConfig serviceConfig, ArtifactCommonSettings artifactCommonSettings, EnvironmentVariables environmentVariables) {
        try {
            script.sh("""helm upgrade --atomic --install \
                            ${(environmentVariables.DEBUG ? '--debug' : '')} \
                            --timeout ${deployTimeoutSeconds}s \
                            --create-namespace \
                            --namespace ${artifactCommonSettings.namespace} \
                            -f ${deployConfig.defaultValuesFilePath} \
                            -f ${deployConfig.microServiceValuesFilePath} \
                            -f ${deployConfig.secretValuesFilePath} ${serviceConfig.helmOption} \
                            ${artifactCommonSettings.releaseName} .helm/""")
        } catch (e) {
            logger.logInfo("Helm's work ended with an error")
            script.timeout(time: 300, unit: "SECONDS") {
                script.input 'Stop this?'
            }
            catchError(buildResult: 'FAILURE', stageResult: 'FAILURE') {
                script.sh("exit 1")
            }
        }
    }

    void prepareServiceYamlConfigs(DeployConfig deployConfig, ServiceConfig serviceConfig, Yaml commonYaml,
                                   JenkinsFileSettings jenkinsFileSettings, PipelineParameters pipelineParameters,
                                   ArtifactCommonSettings artifactCommonSettings) {

        Utils utils = new Utils()
        Map commonEnv = commonYaml == null ? [:] : commonYaml.get('common') as Map
        Map valuesOverrides = utils.merge(commonEnv, serviceConfig.microservice)

        valuesOverrides["microservice"] = [name: jenkinsFileSettings.repositoryName, registryUrl: deployConfig.registryProvider.registryImagePushUrl, imageFolder: artifactCommonSettings.imageFolder, image: jenkinsFileSettings.repositoryName, tag: artifactCommonSettings.imageTag]
        valuesOverrides["project"] = deployConfig.projectName
        valuesOverrides["environment"] = "${pipelineParameters.deployEnvironment}"
        valuesOverrides["gitCommitShort"] = artifactCommonSettings.gitCommitShort
        valuesOverrides["namespace"] = artifactCommonSettings.namespace
        valuesOverrides["makefile"] = [env: serviceConfig.makeFileEnv]

        script.writeYaml file: deployConfig.microServiceValuesFilePath, overwrite: true, data: valuesOverrides

        switch (deployConfig.secretProvider.providerName) {
            case 'vault':
                Vault vault = new Vault(script, deployConfig)
                String vaultPathSecret = "${pipelineParameters.cluster}/${deployConfig.projectName}/${jenkinsFileSettings.repositoryName}/${pipelineParameters.deployEnvironment}"
                Map valuesOverridesSecret = [secret: vault.getVaultSecret(vaultPathSecret)]

                script.writeYaml file: deployConfig.secretValuesFilePath, overwrite: true, data: valuesOverridesSecret
                break
            default:
                script.writeYaml file: deployConfig.secretValuesFilePath, overwrite: true, data: [:]
                break
        }
    }

}
