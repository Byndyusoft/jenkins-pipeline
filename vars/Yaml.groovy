/** Working with yaml files */
class Yaml {
    private def parsedYaml
    private final Logger logger

    Yaml(yaml, Logger logger) {
        this.parsedYaml = yaml
        this.logger = logger
    }

    def get(String path) {
        def value

        logger.logInfo("parsedYaml=${parsedYaml}")

        for (i in path.split('/')) {
            value = parsedYaml?.get(i)
        }

        logger.logInfo("value=${value}")
        return value
    }
}
