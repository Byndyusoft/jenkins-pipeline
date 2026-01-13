/** Working with yaml files */
class Yaml {
    private def parsedYaml

    Yaml(yaml) {
        this.parsedYaml = yaml
    }

    def get(String path) {
        def value

        for (i in path.split('/')) {
            value = parsedYaml?.get(i)
        }

        return value
    }
}
