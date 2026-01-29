/** Class for working with make */
class Make {
    private def script
    private CommonConfig commonConfig
    private final Logger logger

    Make(script, CommonConfig commonConfig, Logger logger){
        this.script = script
        this.commonConfig = commonConfig
        this.logger = logger
    }

    void installDependencies() {
        script.sh("make ${commonConfig.makeOption} install-dependencies ${commonConfig.makeFileEnvString}")
    }

    void runUnitTests() {
        script.sh("make ${commonConfig.makeOption} test ${commonConfig.makeFileEnvString}")
        if (script.fileExists('test/results.xml')) {
            script.junit testResults: 'test/results.xml'
        } else {
            logger.logInfo("Test report file 'test/results.xml' not found. Skipping junit step.")
        }
    }

    void runStyleChecks() {
        script.sh("make ${commonConfig.makeOption} lint ${commonConfig.makeFileEnvString}")
    }

    void buildApplication(String version) {
        script.sh("make ${commonConfig.makeOption} build-app version=${version} ${commonConfig.makeFileEnvString}")
    }

    void packApplication(def artifactVariables) {
        script.sh("make ${commonConfig.makeOption} pack-application outputDir=${artifactVariables.get('outputDir')} ${commonConfig.makeFileEnvString} ${artifactVariables.get('serviceConfig').makeFileEnvString}")
    }

    void buildImage(DeployConfig deployConfig, def artifactVariables) {
        String fullImagePath = "${deployConfig.registryProvider.registryImagePushUrl}/${deployConfig.projectName}/${artifactCommonSettings.imageFolder}/${artifactVariables.get('artifactName')}:${artifactCommonSettings.imageTag}"
        script.sh("make ${commonConfig.makeOption} build-image appImage=${fullImagePath} outputDir=${artifactVariables.get('outputDir')} ${commonConfig.makeFileEnvString} ${artifactVariables.get('serviceConfig').makeFileEnvString}")
    }

    void packPackage(String packageVersion, def artifactVariables) {
        script.sh("make ${commonConfig.makeOption} pack-package packageVersion=${packageVersion} outputDir=${artifactVariables.get('outputDir')} ${commonConfig.makeFileEnvString} ${artifactVariables.get('serviceConfig').makeFileEnvString}")
    }
}
