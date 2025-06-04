/** Class for working with make */
class Make {
    private def script
    private ServiceConfig serviceConfig

    Make(script, ServiceConfig serviceConfig){
        this.script = script
        this.serviceConfig = serviceConfig
    }

    void runUnitTests() {
        script.sh("make ${serviceConfig.makeOption} test ${serviceConfig.makeFileEnvString}")
        script.junit testResults: 'test/results.xml'
    }

    void runStyleChecks() {
        script.sh("make ${serviceConfig.makeOption} lint ${serviceConfig.makeFileEnvString}")
    }

    void buildImage(DeployConfig deployConfig, ArtifactSettings artifactSettings) {
        String fullImagePath = "${deployConfig.registryProvider.registryImagePushUrl}/${deployConfig.projectName}/${artifactSettings.imageFolder}/${artifactSettings.imageName}:${artifactSettings.imageTag}"

        script.sh("make ${serviceConfig.makeOption} build-image app_image=${fullImagePath} ${serviceConfig.makeFileEnvString}")
    }

    void buildApplication() {
        script.sh("make ${serviceConfig.makeOption} build-app ${serviceConfig.makeFileEnvString}")
    }

    void packPackage(String packageVersion) {
        script.sh("make ${serviceConfig.makeOption} pack-package ${serviceConfig.makeFileEnvString} packageVersion=${packageVersion}")
    }
}
