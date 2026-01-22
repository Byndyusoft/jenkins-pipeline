/** Service common configuration class common.yaml */
class commonConfig {
    Map artifactSetting
    Map microservice
    String makeOption
    Map makeFileEnv
    String makeFileEnvString
    String helmOption

    void initialize(Yaml commonYaml) {
        Utils utils = new Utils()

        common = serviceYaml.get('common') as Map ?: [:]

        makeOption = serviceYaml.get('make/option') ?: ''
        makeFileEnv = serviceYaml.get('makefile/env') as Map ?: [:]
        makeFileEnvString = utils.mapToString(makeFileEnv) ?: ''

        helmOption = serviceYaml.get('helm/option') ?: ''
    }
}
