/** Class for enabling tracing, only classes listed in the initialize method are traced */
class Tracing {
    void initialize(Logger logger) {
        decorateWithTracing(DeployConfig, logger)
        decorateWithTracing(ArtifactSettings, logger)
        decorateWithTracing(JenkinsFileSettings, logger)
        decorateWithTracing(PipelineParameters, logger)
        decorateWithTracing(ServiceConfig, logger)
        decorateWithTracing(Git, logger)
        decorateWithTracing(Helm, logger)
        decorateWithTracing(Kubernetes, logger)
        decorateWithTracing(Make, logger)
        decorateWithTracing(Nexus, logger)
        decorateWithTracing(Utils, logger)
        decorateWithTracing(Vault, logger)
        decorateWithTracing(Yaml, logger)
    }

    private static void decorateWithTracing(classToDecorate, Logger logger) {
        def tracingMetaClass = new TracingMetaClass(classToDecorate.metaClass, logger)
        tracingMetaClass.initialize()

        classToDecorate.metaClass = tracingMetaClass
    }
}
