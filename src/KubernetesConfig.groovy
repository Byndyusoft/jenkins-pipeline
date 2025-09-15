/** Deployment configuration class */
class KubernetesConfig {
    /**cloud*/
    String cloud
    /**image for jnlp container*/
    String podTemplateJenkinsAgentImage
    /**image for docker container*/
    String podTemplateDockerImage
    /**image for helm container*/
    String podTemplateHelmImage
    /**which containers to start*/
    String[] podTemplateContainer
    /**custom yaml for agent cloud*/
    String podTemplateYaml
    /**service account for use agent cloud*/
    String podTemplateServiceAccount
    /**select node for agent cloud*/
    String podTemplateNodeSelector
    /**Volumes that are defined for the pod and are mounted by ALL containers for agent cloud*/
    List podTemplateVolumes

    KubernetesConfig(Map k8sCloud, DeployConfig deployConfig, PipelineParameters pipelineFlow) {
        if (k8sCloud.cloud == null) {
            for (clusterName in deployConfig.clusterNames) {
                if (clusterName =~ pipelineFlow.cluster) {
                    cloud = clusterName
                    break
                }
            }
        }

        cloud = cloud ?: 'kubernetes'
        podTemplateJenkinsAgentImage = k8sCloud.podTemplateJenkinsAgentImage ?: 'jenkins/inbound-agent:3261.v9c670a_4748a_9-2-alpine3.20-jdk21'
        podTemplateDockerImage = k8sCloud.podTemplateDockerImage ?: 'byndyusoft/build-essentials:feature-add-tools' // TODO обновить версию образа до релизной
        podTemplateHelmImage = k8sCloud.podTemplateHelmImage ?: 'alpine/helm:3.15.4'
        podTemplateContainer = k8sCloud.podTemplateContainer ?: ['docker', 'helm']
        podTemplateYaml = k8sCloud.yaml ?: deployConfig?.yaml ?: ''
        podTemplateServiceAccount = k8sCloud.serviceAccount ?: deployConfig?.serviceAccount ?: 'default'
        podTemplateNodeSelector = k8sCloud.nodeSelector ?: ''
        podTemplateVolumes = k8sCloud.volumes ?: deployConfig?.volumes ?: []
    }
}
