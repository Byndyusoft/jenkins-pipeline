/** Application image settings class */
class ArtifactSettings {
    String gitCommitShort
    String imageFolder
    String releaseImageFolder
    String releaseName
    String namespace
    String imageName
    String imageTag
    String releaseTag

    void initialize(DeployConfig deployConfig, JenkinsFileSettings jenkinsFileSettings, EnvironmentVariables environmentVariables,
                    PipelineParameters pipelineParameters, Git git, SemanticVersion latestVersion) {
        Utils utils = new Utils()

        gitCommitShort = git.getCommitShaShort()
        imageTag = (environmentVariables.TAG_NAME) ?: "${environmentVariables.BRANCH_NAME.replace('/', '-')}-${gitCommitShort}"
        imageName = jenkinsFileSettings.artifactName

        imageFolder = environmentVariables.TAG_NAME ? 'release' : 'feature'
        releaseImageFolder = 'release'

        namespace = utils.prepareName("${deployConfig.projectName}-${pipelineParameters.deployEnvironment}")
        releaseName = utils.prepareName("${deployConfig.projectName}-${jenkinsFileSettings.artifactName}-${pipelineParameters.deployEnvironment}")

        releaseTag = latestVersion.toString()
    }
}
