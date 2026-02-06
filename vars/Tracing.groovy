/** Class for enabling tracing, only classes listed in the initialize method are traced */
class Tracing {
    void initialize(Logger logger) {
        decorateWithTracing(ArtifactCommonSettings, logger)
        decorateWithTracing(CommonConfig, logger)
        decorateWithTracing(DeployConfig, logger)
        decorateWithTracing(KubernetesConfig, logger)
        decorateWithTracing(PipelineParameters, logger)
        // decorateWithTracing(SemanticVersion, logger)
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
