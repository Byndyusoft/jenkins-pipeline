/** Class for working with helm */
class Nelm {
    private final def script
    private final int deployTimeoutSeconds
    private final Logger logger

    Nelm(script, Logger logger) {
        this.script = script
        this.logger = logger
        deployTimeoutSeconds = 300
    }

    void deployApplication(DeployConfig deployConfig, CommonConfig commonConfig, ArtifactCommonSettings artifactCommonSettings, EnvironmentVariables environmentVariables) {
        try {
            script.sh("""nelm release install --auto-rollback \
                        ${(environmentVariables.DEBUG ? '--log-level="debug"' : '')} \
                        --timeout=${deployTimeoutSeconds}s \
                        -n ${artifactCommonSettings.namespace} \
                        --values=${deployConfig.defaultValuesFilePath} \
                        --values=${deployConfig.microServiceValuesFilePath} ${commonConfig.nelmOption} \
                        -r ${artifactCommonSettings.releaseName} .nelm/""")
        } catch (e) {
            logger.logInfo("Nelm's work ended with an error ${e}")
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

        Map fullValues = [microservices: []]
        if (script.fileExists(deployConfig.microServiceValuesFilePath)) {
            fullValues = new Yaml(script.readYaml(file: deployConfig.microServiceValuesFilePath)).get('/')
        }

        Map valuesOverrides = utils.merge(commonConfig.common, artifactVariables.get('serviceConfig').microservice)

        valuesOverrides['name'] = artifactVariables.get('artifactName')
        valuesOverrides['microservice'] = [registryUrl: deployConfig.registryProvider.registryImagePushUrl, imageFolder: artifactCommonSettings.imageFolder, image: artifactVariables.get('artifactName'), tag: artifactCommonSettings.imageTag]
        valuesOverrides['projectName'] = deployConfig.projectName
        valuesOverrides['serviceName'] = deployConfig.serviceName
        valuesOverrides['environment'] = artifactCommonSettings.deployEnvironment
        valuesOverrides['gitCommitShort'] = artifactCommonSettings.gitCommitShort
        valuesOverrides['namespace'] = artifactCommonSettings.namespace
        valuesOverrides['weight'] = artifactVariables.get('serviceConfig')artifactSetting.get('weight')

        // Secrets
        Map valuesOverridesSecret  = [:]

        switch (deployConfig.secretProvider.providerName) {
            case 'vault':
                Vault vault = new Vault(script, deployConfig)
                String vaultPathSecret = "${artifactCommonSettings.cluster}/${artifactCommonSettings.serviceIdentifier}/${artifactVariables.get('artifactName')}/${artifactCommonSettings.deployEnvironment}"
                valuesOverridesSecret = [envSecret: vault.getVaultSecret(vaultPathSecret)]
                break
            default:
                break
        }

        fullValues.microservices.add(utils.merge(valuesOverrides, valuesOverridesSecret))
        script.writeYaml file: deployConfig.microServiceValuesFilePath, overwrite: true, data: fullValues
    }
}
