/** Application image settings class */
class ArtifactCommonSettings {
    String gitCommitShort
    String imageFolder
    String releaseImageFolder
    String releaseName
    String namespace
    String imageTag
    SemanticVersion releaseVersion
    String releaseTag
    String version

    void initialize(DeployConfig deployConfig, EnvironmentVariables environmentVariables,
                    PipelineParameters pipelineParameters, Git git, SemanticVersion releaseVersion, String version) {
        Utils utils = new Utils()

        gitCommitShort = git.getCommitShaShort()
        imageTag = (environmentVariables.TAG_NAME) ?: "${environmentVariables.BRANCH_NAME.replace('/', '-')}-${gitCommitShort}"

        imageFolder = environmentVariables.TAG_NAME ? 'release' : 'feature'
        releaseImageFolder = 'release'

        namespace = utils.prepareName("${deployConfig.projectName}-${pipelineParameters.deployEnvironment}")
        releaseName = utils.prepareName("${deployConfig.projectName}-${deployConfig.serviceName}-${pipelineParameters.deployEnvironment}")

        releaseVersion = releaseVersion

        releaseTag = releaseVersion.toString()

        version = version
    }
}
