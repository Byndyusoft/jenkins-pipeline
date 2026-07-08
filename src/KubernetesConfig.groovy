/** Deployment configuration class */
class KubernetesConfig {
    private final Logger logger
    /**cloud*/
    String cloudName
    /**custom yaml for agent cloud*/
    String podTemplateYaml
    /**Volumes that are defined for the pod and are mounted by ALL containers for agent cloud*/
    Map podTemplateVolumes

    KubernetesConfig(Logger logger) {
        this.logger = logger
    }

    void initialize(Map k8sCloud) {
        cloudName = k8sCloud.cloudName ?: 'kubernetes'
        podTemplateYaml = k8sCloud.yaml ?: ''
        podTemplateVolumes = k8sCloud.volumes ?: [:]

        logger.logDebug("KubernetesConfig:initialize cloudName = ${cloudName}")
    }
}
