/** Class for working with image repository */
class Nexus {
    private def script
    private final DeployConfig deployConfig
    private final EnvironmentVariables environmentVariables
    private final Logger logger

    Nexus(script, DeployConfig deployConfig, EnvironmentVariables environmentVariables, Logger logger) {
        this.script = script
        this.deployConfig = deployConfig
        this.environmentVariables = environmentVariables
        this.logger = logger
    }

    void initialize() {
        authRegistryImage()
    }

    private runWithCredentials(Closure code) {
        script.withCredentials([script.usernamePassword(credentialsId: deployConfig.registryProvider.credentialsId, usernameVariable: 'userRegistry', passwordVariable: 'passRegistry')]) {
            return code()
        }
    }

    /**
     * Authentication for all push images to private registry
     * https://docs.docker.com/reference/cli/docker/login/#username
     */
    private void authRegistryImage() {
        runWithCredentials {
            // ToDo: Think about how to remove if
            if ((deployConfig.registryProvider.registryImagePullUrl) && (deployConfig.registryProvider.registryImagePushUrl)) {
                script.sh("docker login -u ${script.userRegistry} -p ${script.passRegistry} ${deployConfig.registryProvider.registryImagePullUrl}")
                script.sh("docker login -u ${script.userRegistry} -p ${script.passRegistry} ${deployConfig.registryProvider.registryImagePushUrl}")
            } else {
                logger.logInfo("Parameters 'registryImagePullUrl' or 'registryImagePushUrl' are empty!")
            }
        }
    }

    void pushPackage(JenkinsFileSettings jenkinsFileSettings, ServiceConfig serviceConfig) {
        for (repositoryType in jenkinsFileSettings.repositoryTypes) {
            switch (repositoryType) {
                case RepositoryType.PythonPackage:
                    pushPythonPackage()
                    break
                case RepositoryType.RawPackage:
                    pushRawPackage(jenkinsFileSettings.artifactName)
                    break
                case RepositoryType.NugetPackage:
                    pushNugetPackage(serviceConfig)
                    break
                default:
                    logger.logInfo('Did not determine the type package')
            }
        }
    }

    boolean checkImage(ArtifactSettings artifactSettings) {
        boolean imageExist = false

        runWithCredentials {
            String url = "https://${deployConfig.registryProvider.registryImagePushUrl}/v2/${deployConfig.projectName}/${artifactSettings.imageFolder}/${artifactSettings.imageName}/tags/list"
            imageExist = script.sh(
                    returnStdout: true,
                    script: """curl ${environmentVariables.DEBUG ? '-v' : '-s'} -u ${script.userRegistry}:${script.passRegistry} -X GET \
                        ${url} | jq -e '.tags | contains([\"${artifactSettings.imageTag}\"])' || echo false"""
            ).toBoolean()
        }

        return imageExist
    }

    boolean checkNugetPackage(String namePackage) {
        runWithCredentials {
            URL url = new URL("${deployConfig.registryProvider.registryPackageUrl}")

            return (script.sh(
                returnStdout: true,
                script: """curl -s --output /dev/null -u ${script.userRegistry}:${script.passRegistry} -X GET --write-out '%{http_code}' \
                    ${url.protocol}://${url.host}${url.path.replaceFirst(/index\.json/, '')}${namePackage}""") == '200') ? true : false
        }
    }

    void pushImage(ArtifactSettings artifactSettings) {
        script.sh("docker push ${deployConfig.registryProvider.registryImagePushUrl}/${deployConfig.projectName}/${artifactSettings.imageFolder}/${artifactSettings.imageName}:${artifactSettings.imageTag}")
    }

    void createReleaseImage(ArtifactSettings artifactSettings) {
        script.sh("docker pull ${deployConfig.registryProvider.registryImagePushUrl}/${deployConfig.projectName}/${artifactSettings.imageFolder}/${artifactSettings.imageName}:${artifactSettings.imageTag}")
        script.sh("""docker tag ${deployConfig.registryProvider.registryImagePushUrl}/${deployConfig.projectName}/${artifactSettings.imageFolder}/${artifactSettings.imageName}:${artifactSettings.imageTag} \
            ${deployConfig.registryProvider.registryImagePushUrl}/${deployConfig.projectName}/${artifactSettings.releaseImageFolder}/${artifactSettings.imageName}:${artifactSettings.releaseTag}""")
        script.sh("docker push ${deployConfig.registryProvider.registryImagePushUrl}/${deployConfig.projectName}/${artifactSettings.releaseImageFolder}/${artifactSettings.imageName}:${artifactSettings.releaseTag}")

        artifactSettings.imageFolder = artifactSettings.releaseImageFolder
        artifactSettings.imageTag = artifactSettings.releaseTag
    }

    void pushPythonPackage() {
        runWithCredentials {
            script.sh("twine upload -u ${script.userRegistry} -p ${script.passRegistry} --repository-url ${deployConfig.registryProvider.registryPackageUrl} ./dist/* --verbose")
        }
    }

    void pushRawPackage(String name) {
        runWithCredentials {
            script.sh("curl -v --user '${script.userRegistry}:${script.passRegistry}' --upload-file \"{\$(echo ./out/* | tr ' ' ',')}\" ${deployConfig.registryProvider.registryPackageUrl}/${name}/")
        }
    }

    void pushNugetPackage(serviceConfig) {
        runWithCredentials {
            String nugetFileDirectory = serviceConfig.makeFileEnv.nuget_file_directory ?: '/app/nuget'

            script.sh("mono /usr/local/bin/nuget.exe sources Add -Name \"local-nuget-push\" \
                -Source \"${deployConfig.registryProvider.registryPackageUrl}\" \
                -Username ${script.userRegistry} \
                -password ${script.passRegistry} \
                -StorePasswordInClearText")

            List listPackage = script.sh(returnStdout: true, script: """ls -1 ${nugetFileDirectory}""").split("\n")

            for (i in listPackage) {
                if (!checkNugetPackage(i.replaceFirst(/\.(\d+.\d+.\d+)/, '/$1').replaceFirst(/\.nupkg/, ''))) {
                    script.sh("cd ${nugetFileDirectory} && mono /usr/local/bin/nuget.exe push \"${i}\" \
                        -Source \"${deployConfig.registryProvider.registryPackageUrl}\" -SkipDuplicate -Verbosity detailed")
                }
            }
        }
    }
}
