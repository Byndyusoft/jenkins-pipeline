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

    void deployApplication(DeployConfig deployConfig, ServiceConfig serviceConfig, ArtifactSettings artifactSettings, EnvironmentVariables environmentVariables) {
        try {
            script.sh("""helm upgrade --atomic --install \
                            ${(environmentVariables.DEBUG ? '--debug' : '')} \
                            --timeout ${deployTimeoutSeconds}s \
                            --create-namespace \
                            --namespace ${artifactSettings.namespace} \
                            -f ${deployConfig.defaultValuesFilePath} \
                            -f ${deployConfig.microServiceValuesFilePath} \
                            -f ${deployConfig.secretValuesFilePath} ${serviceConfig.helmOption} \
                            ${artifactSettings.releaseName} .helm/""")
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
                                   ArtifactSettings artifactSettings) {

        Utils utils = new Utils()
        Map commonEnv = commonYaml == null ? [:] : commonYaml.get('common') as Map
        Map valuesOverrides = utils.merge(commonEnv, serviceConfig.microservice)

        valuesOverrides["microservice"] = [name: jenkinsFileSettings.artifactName, registryUrl: deployConfig.registryProvider.registryImagePushUrl, imageFolder: artifactSettings.imageFolder, image: jenkinsFileSettings.artifactName, tag: artifactSettings.imageTag]
        valuesOverrides["project"] = deployConfig.projectName
        valuesOverrides["environment"] = "${pipelineParameters.deployEnvironment}"
        valuesOverrides["gitCommitShort"] = artifactSettings.gitCommitShort
        valuesOverrides["namespace"] = artifactSettings.namespace
        valuesOverrides["makefile"] = [env: serviceConfig.makeFileEnv]

        script.writeYaml file: deployConfig.microServiceValuesFilePath, overwrite: true, data: valuesOverrides

        switch (deployConfig.secretProvider.providerName) {
            case 'vault':
                Vault vault = new Vault(script, deployConfig)
                // TODO "stage/bs-tmc/tmc-api/preprod" зачем дважды повторяется оружение (stage, preprod)
                String vaultPathSecret = "${pipelineParameters.cluster}/${deployConfig.projectName}/${jenkinsFileSettings.artifactName}/${pipelineParameters.deployEnvironment}"
                Map valuesOverridesSecret = [secret: vault.getVaultSecret(vaultPathSecret)]

                script.writeYaml file: deployConfig.secretValuesFilePath, overwrite: true, data: valuesOverridesSecret
                break
            default:
                script.writeYaml file: deployConfig.secretValuesFilePath, overwrite: true, data: [:]
                break
        }
    }

}
