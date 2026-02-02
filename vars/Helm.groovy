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

        Map fullValues = [:]
        if (script.fileExists(deployConfig.microServiceValuesFilePath)) {
            fullValues = new Yaml(script.readYaml(file: deployConfig.microServiceValuesFilePath)).get('/')
        }

        Map valuesOverrides = utils.merge(commonConfig.common, artifactVariables.get('serviceConfig').microservice)

        valuesOverrides["microservice"] = [name: deployConfig.serviceName, registryUrl: deployConfig.registryProvider.registryImagePushUrl, imageFolder: artifactCommonSettings.imageFolder, image: artifactVariables.get('artifactName'), tag: artifactCommonSettings.imageTag]
        valuesOverrides["projectName"] = deployConfig.projectName
        valuesOverrides["serviceName"] = deployConfig.serviceName
        valuesOverrides["environment"] = artifactCommonSettings.deployEnvironment
        valuesOverrides["gitCommitShort"] = artifactCommonSettings.gitCommitShort
        valuesOverrides["namespace"] = artifactCommonSettings.namespace
        valuesOverrides["weight"] = artifactVariables.get('serviceConfig')artifactSetting.get('weight')
        // valuesOverrides["makefile"] = [env: serviceConfig.makeFileEnv]

        fullValues.put(artifactVariables.get('artifactName'), valuesOverrides)

        script.writeYaml file: deployConfig.microServiceValuesFilePath, overwrite: true, data: fullValues

        // Secrets
        Map fullValuesSecret = [:]
        Map valuesOverridesSecret  = [:]
        if (script.fileExists(deployConfig.secretValuesFilePath)) {
            fullValuesSecret = new Yaml(script.readYaml(file: deployConfig.secretValuesFilePath)).get('/')
        }

        switch (deployConfig.secretProvider.providerName) {
            case 'vault':
                Vault vault = new Vault(script, deployConfig)
                String vaultPathSecret = "${artifactCommonSettings.cluster}/${artifactCommonSettings.serviceIdentifier}/${artifactVariables.get('artifactName')}/${artifactCommonSettings.deployEnvironment}"
                valuesOverridesSecret = [secret: vault.getVaultSecret(vaultPathSecret)]
                break
            default:
                script.writeYaml file: deployConfig.secretValuesFilePath, overwrite: true, data: [:]
                break
        }

        fullValuesSecret.put(artifactVariables.get('artifactName'), valuesOverridesSecret)
        script.writeYaml file: deployConfig.secretValuesFilePath, overwrite: true, data: fullValuesSecret

        // !!!!!!!!!!Testing
        sleep(30000)
    }
}
