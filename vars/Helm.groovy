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

    void prepareServiceYamlConfigs(DeployConfig deployConfig, CommonConfig commonConfig, Map artifactVariables, ArtifactCommonSettings artifactCommonSettings) {
        Utils utils = new Utils()

        Map fullValues = [microservices: [:]]
        logger.logInfo("${fullValues.getClass()}")
        if (script.fileExists(deployConfig.microServiceValuesFilePath)) {
            fullValues = new Yaml(script.readYaml(file: deployConfig.microServiceValuesFilePath)).get('/')
        }
        logger.logInfo("${fullValues.getClass()}")

        Map valuesOverrides = utils.merge(commonConfig.common, artifactVariables.get('serviceConfig').microservice)

        valuesOverrides["microservice"] = [name: artifactVariables.get('artifactName'), registryUrl: deployConfig.registryProvider.registryImagePushUrl, imageFolder: artifactCommonSettings.imageFolder, image: artifactVariables.get('artifactName'), tag: artifactCommonSettings.imageTag]
        valuesOverrides["projectName"] = deployConfig.projectName
        valuesOverrides["serviceName"] = deployConfig.serviceName
        valuesOverrides["environment"] = artifactCommonSettings.deployEnvironment
        valuesOverrides["gitCommitShort"] = artifactCommonSettings.gitCommitShort
        valuesOverrides["namespace"] = artifactCommonSettings.namespace
        valuesOverrides["weight"] = artifactVariables.get('serviceConfig')artifactSetting.get('weight')

        // Secrets
        Map valuesOverridesSecret  = [:]

        switch (deployConfig.secretProvider.providerName) {
            case 'vault':
                Vault vault = new Vault(script, deployConfig)
                String vaultPathSecret = "${artifactCommonSettings.cluster}/${artifactCommonSettings.serviceIdentifier}/${artifactVariables.get('artifactName')}/${artifactCommonSettings.deployEnvironment}"
                valuesOverridesSecret = [envSecret: vault.getVaultSecret(vaultPathSecret)]
                break
            default:
                script.writeYaml file: deployConfig.secretValuesFilePath, overwrite: true, data: [:]
                break
        }

        logger.logInfo("${fullValues.getClass()}")
        fullValues.microservices.put(artifactVariables.get('artifactName'), utils.merge(valuesOverrides, valuesOverridesSecret))
        script.writeYaml file: deployConfig.microServiceValuesFilePath, overwrite: true, data: fullValues

        // !!!!!!!!!!Testing
        sleep(30000)
    }
}
