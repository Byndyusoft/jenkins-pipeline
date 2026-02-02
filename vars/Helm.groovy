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

    void prepareServiceYamlConfigs(DeployConfig deployConfig, CommonConfig commonConfig, def artifactVariables) {
        Utils utils = new Utils()

        Map fullValues = new Yaml(readYaml(file: deployConfig.microServiceValuesFilePath)).get('/')

        Map valuesOverrides = utils.merge(commonConfig.common, artifactVariables.get('ServiceConfig').microservice)

        fullValues.put(artifactVariables.get('artifactName'), valuesOverrides)

        // valuesOverrides["microservice"] = [name: deployConfig.serviceName, registryUrl: deployConfig.registryProvider.registryImagePushUrl, imageFolder: artifactCommonSettings.imageFolder, image: deployConfig.serviceName, tag: artifactCommonSettings.imageTag]
        // valuesOverrides["projectName"] = deployConfig.projectName
        // valuesOverrides["serviceName"] = deployConfig.serviceName
        // valuesOverrides["environment"] = "${artifactCommonSettings.deployEnvironment}"
        // valuesOverrides["gitCommitShort"] = artifactCommonSettings.gitCommitShort
        // valuesOverrides["namespace"] = artifactCommonSettings.namespace
        // valuesOverrides["makefile"] = [env: serviceConfig.makeFileEnv]

        script.writeYaml file: deployConfig.microServiceValuesFilePath, overwrite: true, data: fullValues

        switch (deployConfig.secretProvider.providerName) {
            case 'vault':
                Vault vault = new Vault(script, deployConfig)
                String vaultPathSecret = "${artifactCommonSettings.cluster}/${artifactCommonSettings.serviceIdentifier}/${artifactVariables.get('artifactName')}/${artifactCommonSettings.deployEnvironment}"
                Map valuesOverridesSecret = [secret: vault.getVaultSecret(vaultPathSecret)]

                script.writeYaml file: deployConfig.secretValuesFilePath, overwrite: true, data: valuesOverridesSecret
                break
            default:
                script.writeYaml file: deployConfig.secretValuesFilePath, overwrite: true, data: [:]
                break
        }

        // !!!!!!!!!!Testing
        script.timeout(time: 300, unit: "SECONDS")
    }
}
