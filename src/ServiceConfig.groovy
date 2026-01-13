/** Service configuration class {artifact name}.yaml */
class ServiceConfig {
    Map artifactSetting
    Map microservice
    String makeOption
    Map makeFileEnv
    String makeFileEnvString
    String helmOption

    ServiceConfig(Yaml serviceYaml) {
        Utils utils = new Utils()

        artifactSetting = serviceYaml.get('artifactSetting') as Map ?: [:]

        microservice = serviceYaml.get('microservice') as Map ?: [:]

        makeOption = serviceYaml.get('make/option') ?: ''
        makeFileEnv = serviceYaml.get('makefile/env') as Map ?: [:]
        makeFileEnvString = utils.mapToString(makeFileEnv) ?: ''

        helmOption = serviceYaml.get('helm/option') ?: ''
    }
}
