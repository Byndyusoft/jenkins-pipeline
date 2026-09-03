/** Microservice configuration class {artifact name}.yaml */
class MicroServiceConfig {
    Map artifactSetting
    Map microservice
    String makeOption
    Map makeFileEnv
    String makeFileEnvString

    void initialize(Yaml microServiceYaml) {
        Utils utils = new Utils()

        artifactSetting = microServiceYaml.get('artifactSetting') as Map ?: [:]

        microservice = microServiceYaml.get('microservice') as Map ?: [:]

        makeOption = microServiceYaml.get('make/option') ?: ''
        makeFileEnv = microServiceYaml.get('makefile/env') as Map ?: [:]
        makeFileEnvString = utils.mapToString(makeFileEnv) ?: ''
    }
}
