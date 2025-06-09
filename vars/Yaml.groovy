/** Working with yaml files */
class Yaml {
    private def parsedYaml

    Yaml(yaml) {
        parsedYaml = yaml
    }

    def get(String path) {
        def value = parsedYaml

        for (i in path.split('/')) {
            value = value?.get(i)
        }

        return value
    }
}
