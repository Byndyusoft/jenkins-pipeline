/** Deployment configuration class */
class KubernetesConfig {
    /**cloud*/
    String cloudName
    /**custom yaml for agent cloud*/
    String podTemplateYaml
    /**Volumes that are defined for the pod and are mounted by ALL containers for agent cloud*/
    Map podTemplateVolumes

    void initialize(Map k8sCloud) {
        cloudName = k8sCloud.cloudName ?: 'kubernetes'
        podTemplateYaml = k8sCloud.yaml ?: ''
        podTemplateVolumes = k8sCloud.volumes ?: [:]
    }
}
