/** Application image settings class */
class ArtifactCommonSettings {
    String gitCommitShort
    String imageFolder
    String releaseImageFolder
    String namespace
    String releaseName
    String imageTag
    String releaseTag
    String artifactVersion
    SemanticVersion releaseVersion

    void initialize(DeployConfig deployConfig, EnvironmentVariables environmentVariables,
                    PipelineParameters pipelineParameters, Git git, SemanticVersion releaseVersion, String artifactVersion) {
        Utils utils = new Utils()

        gitCommitShort = git.getCommitShaShort()

        String serviceIdentifier = utils.prepareName([deployConfig.projectName, deployConfig.serviceName].findAll { it } .join('-'))

        imageFolder = environmentVariables.TAG_NAME ? "${serviceIdentifier}/release" : "${serviceIdentifier}/feature"
        releaseImageFolder = "${serviceIdentifier}/release"

        namespace = utils.prepareName([serviceIdentifier, pipelineParameters.deployEnvironment].findAll { it } .join('-'))
        releaseName = utils.prepareName([serviceIdentifier, pipelineParameters.deployEnvironment].findAll { it } .join('-'))

        imageTag = (environmentVariables.TAG_NAME) ?: "${environmentVariables.BRANCH_NAME.replace('/', '-')}-${gitCommitShort}"
        releaseTag = releaseVersion.toString()

        this.artifactVersion = artifactVersion
        this.releaseVersion = releaseVersion
    }
}
