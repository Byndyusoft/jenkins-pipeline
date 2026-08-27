/** Deployment configuration deploy.yaml */
class DeployConfig {
    private final Logger logger
    /** Credentials from jenkins for git repositories */
    String gitCredentialsId
    /** project name */
    String projectName
    /** service name */
    String serviceName
    Map clouds
    /** list of available environments */
    List deployEnvironments = []
    List deployEnvironmentsImportant = []
    String cloudBuildName
    /** Credentials from jenkins for nelm */
    String nelmKeyCredentialsId
    /** path default values file */
    String defaultValuesFilePath
    /** path final values file for deploy service */
    String microServiceValuesFilePath

    /** custom yaml for "Pod Templates jenkins agent(k8s)" */
    String yaml
    /** volumes for "Pod Templates jenkins agent(k8s)" */
    Map volumes
    /** setting secrets provider */
    SecretProvider secretProvider
    /** setting registry provider */
    RegistryProvider registryProvider

    DeployConfig(Logger logger) {
        this.logger = logger
    }

    void initialize(Yaml deployYaml) {
        gitCredentialsId = deployYaml.get('gitCredentialsId')

        projectName = deployYaml.get('project')
        serviceName = deployYaml.get('serviceName') ?: ''

        clouds = deployYaml.get('clouds') as Map
        deployEnvironments = clouds.values().findAll { it.important != true && it.environment }.collect { it.environment }.flatten().unique()
        deployEnvironmentsImportant = clouds.values().findAll { it.important == true && it.environment }.collect { it.environment }.flatten().unique()

        cloudBuildName = clouds.values().collectMany { it.cloudBuildNames ?: [] }.unique().first()

        nelmKeyCredentialsId = deployYaml.get('nelmKeyCredentialsId')
        defaultValuesFilePath = deployYaml.get('defaultValues')
        microServiceValuesFilePath = deployYaml.get('serviceValues')

        yaml = deployYaml.get('yaml')
        volumes = deployYaml.get('volumes') as Map

        secretProvider = new SecretProvider(deployYaml.get('secret') as Map ?: [:])
        registryProvider = new RegistryProvider(logger)
        registryProvider.initialize(deployYaml)
    }

    /** Settings of the secrets provider */
    class SecretProvider {
        /**module name secret(vault, ...) provider*/
        final String providerName
        /**url to vault*/
        final String url
        /**credentials from jenkins for get secret*/
        final String credentialsId

        SecretProvider(Map secretSection) {
            providerName = secretSection?.provider ?: 'none'
            url = secretSection?.vaultUrl
            credentialsId = secretSection?.vaultAppRoleCredential
        }
    }

    /** Image Storage Settings */
    class RegistryProvider {
        private final Logger logger
        /**url registry for pull images from hosted, proxy and group */
        String registryImagePullUrl
        /**url registry for push images from hosted, proxy and group */
        String registryImagePushUrl
        /**url registry for deploy package*/
        String registryPackageUrl
        /**credentials from jenkins for deploy images*/
        String credentialsId

        RegistryProvider(Logger logger) {
            this.logger = logger
        }

        void initialize(Yaml registrySection) {
            setRegistryImageUrl(registrySection)

            registryPackageUrl = registrySection.get('registryPackageUrl') ?: ''
            credentialsId = registrySection.get('registryCredentialsId') ?: ''
        }

        private void setRegistryImageUrl(Yaml registrySection) {
            if (registrySection.get('registryImageUrl')) {
                registryImagePullUrl = registrySection.get('registryImageUrl')
                registryImagePushUrl = registrySection.get('registryImageUrl')
                return
            }

            if (!registrySection.get('registryImagePullUrl')) {
                logger.logInfo('The "registryImagePullUrl" parameter is required for image.')
            }

            if (!registrySection.get('registryImagePushUrl')) {
                logger.logInfo('The "registryImagePushUrl" parameter is required for image.')
            }

            registryImagePullUrl = registrySection.get('registryImagePullUrl')
            registryImagePushUrl = registrySection.get('registryImagePushUrl')
        }
    }
}
