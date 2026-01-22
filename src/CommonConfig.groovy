/** Service common configuration class common.yaml */
class CommonConfig {
    Map common
    String makeOption
    Map makeFileEnv
    String makeFileEnvString
    String helmOption

    void initialize(Yaml commonYaml) {
        Utils utils = new Utils()

        common = commonYaml.get('common') as Map ?: [:]

        makeOption = commonYaml.get('make/option') ?: ''
        makeFileEnv = commonYaml.get('makefile/env') as Map ?: [:]
        makeFileEnvString = utils.mapToString(makeFileEnv) ?: ''

        helmOption = commonYaml.get('helm/option') ?: ''
    }
}
