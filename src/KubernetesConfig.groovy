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
    Map podTemplateVolumes

    void initialize(DeployConfig deployConfig, PipelineParameters pipelineFlow) {
        for (clusterName in deployConfig.clusterNames) {
            if (clusterName =~ pipelineFlow.cluster) {
                cloud = clusterName
                break
            }
        }

        cloud = cloud ?: 'kubernetes'
        podTemplateJenkinsAgentImage = deployConfig?.podTemplateJenkinsAgentImage ?: 'jenkins/inbound-agent:3261.v9c670a_4748a_9-2-alpine3.20-jdk21'
        podTemplateDockerImage = deployConfig?.podTemplateDockerImage ?: 'byndyusoft/build-essentials:0.0.5'
        podTemplateHelmImage = deployConfig?.podTemplateHelmImage ?: 'alpine/helm:3.15.4'
        podTemplateContainer = deployConfig?.podTemplateContainer ?: ['docker', 'helm']
        podTemplateYaml = deployConfig?.yaml ?: ''
        podTemplateServiceAccount = deployConfig?.serviceAccount ?: 'default'
        podTemplateNodeSelector = deployConfig?.nodeSelector ?: ''
        podTemplateVolumes = deployConfig?.volumes ?: [:]
    }
}
