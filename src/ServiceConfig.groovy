/** Service configuration class {artifact name}.yaml */
class ServiceConfig {
    Map microservice
    String makeOption
    Map makeFileEnv
    String makeFileEnvString
    String helmOption

    void initialize(Yaml serviceYaml) {
        microservice = serviceYaml.get('microservice') as Map ?: [:]

        Utils utils = new Utils()

        makeOption = serviceYaml.get('make/option') ?: ''
        makeFileEnv = serviceYaml.get('makefile/env') as Map ?: [:]
        makeFileEnvString = utils.mapToString(makeFileEnv) ?: ''

        helmOption = serviceYaml.get('helm/option') ?: ''
    }
}
