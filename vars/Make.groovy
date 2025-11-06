/** Class for working with make */
class Make {
    private def script
    private ServiceConfig serviceConfig
    private final Logger logger

    Make(script, ServiceConfig serviceConfig, Logger logger){
        this.script = script
        this.serviceConfig = serviceConfig
        this.logger = logger
    }

    void runUnitTests() {
        script.sh("make ${serviceConfig.makeOption} test ${serviceConfig.makeFileEnvString}")
        if (script.fileExists('test/results.xml')) {
            script.junit testResults: 'test/results.xml'
        } else {
            logger.logInfo("Test report file 'test/results.xml' not found. Skipping junit step.")
        }
    }

    void runStyleChecks() {
        script.sh("make ${serviceConfig.makeOption} lint ${serviceConfig.makeFileEnvString}")
    }

    void buildImage(DeployConfig deployConfig, ArtifactSettings artifactSettings) {
        String fullImagePath = "${deployConfig.registryProvider.registryImagePushUrl}/${deployConfig.projectName}/${artifactSettings.imageFolder}/${artifactSettings.imageName}:${artifactSettings.imageTag}"
        
        // TODO app_image переделать в appImage
        script.sh("make ${serviceConfig.makeOption} build-image app_image=${fullImagePath} ${serviceConfig.makeFileEnvString}")
    }

    void buildApplication(String version) {
        script.sh("make ${serviceConfig.makeOption} build-app version=${version} ${serviceConfig.makeFileEnvString}")
    }

    void packPackage(String packageVersion) {
        script.sh("make ${serviceConfig.makeOption} pack-package packageVersion=${packageVersion} ${serviceConfig.makeFileEnvString}")
    }
}
