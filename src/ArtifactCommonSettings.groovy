/** Application image settings class */
class ArtifactCommonSettings {
    String gitCommitShort
    String imageFolder
    String releaseImageFolder
    String releaseName
    String namespace
    String imageTag
    String releaseTag
    String imageName

    void initialize(DeployConfig deployConfig, JenkinsFileSettings jenkinsFileSettings, EnvironmentVariables environmentVariables,
                    PipelineParameters pipelineParameters, Git git, SemanticVersion releaseVersion) {
        Utils utils = new Utils()

        gitCommitShort = git.getCommitShaShort()
        imageTag = (environmentVariables.TAG_NAME) ?: "${environmentVariables.BRANCH_NAME.replace('/', '-')}-${gitCommitShort}"

        imageName = "test1"

        imageFolder = environmentVariables.TAG_NAME ? 'release' : 'feature'
        releaseImageFolder = 'release'

        namespace = utils.prepareName("${deployConfig.projectName}-${pipelineParameters.deployEnvironment}")
        releaseName = utils.prepareName("${deployConfig.projectName}-${jenkinsFileSettings.artifactName}-${pipelineParameters.deployEnvironment}")

        releaseTag = releaseVersion.toString()
    }
}
