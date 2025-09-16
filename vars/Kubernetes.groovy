/** Wrapper over the podTemplate */
class Kubernetes {
    private final def script

    Kubernetes(script) {
        this.script = script
    }

    def customPodTemplate(KubernetesConfig kubernetesConfig, Closure closure) {
        Map podParams = [
                cloud            : kubernetesConfig.cloud,
                containers       : constructContainer(kubernetesConfig.podTemplateJenkinsAgentImage,
                        kubernetesConfig.podTemplateContainer,
                        kubernetesConfig.podTemplateDockerImage,
                        kubernetesConfig.podTemplateHelmImage),
                yamlMergeStrategy: script.merge(),
                serviceAccount   : kubernetesConfig.podTemplateServiceAccount,
                nodeSelector     : kubernetesConfig.podTemplateNodeSelector
        ]

        if (kubernetesConfig.podTemplateYaml) {
            podParams.yaml = """${kubernetesConfig.podTemplateYaml}"""
        }

        if (kubernetesConfig.podTemplateVolumes) {
            script.echo "${kubernetesConfig.podTemplateVolumes}"
            script.echo "${kubernetesConfig.podTemplateVolumes.getClass()}"
            script.echo "${kubernetesConfig.podTemplateVolumes[0]}"
            script.echo "${kubernetesConfig.podTemplateVolumes[0].getClass()}"

            if (kubernetesConfig.podTemplateVolumes.get('persistentVolumeClaim')) {
                script.echo "${kubernetesConfig.podTemplateVolumes.get('persistentVolumeClaim')[0]}"
                podParams.volumes = [persistentVolumeClaim(claimName: kubernetesConfig.podTemplateVolumes.get('persistentVolumeClaim')[0]['claimName'], mountPath: kubernetesConfig.podTemplateVolumes.get('persistentVolumeClaim')[0]['mountPath'])]
            }

            //podParams.volumes = [script.dynamicPVC(mountPath: '/root/.nuget', requestsSize: '1Gi', storageClassName: 'localpath-data')]
            //podParams.volumes = [script.persistentVolumeClaim(claimName: 'storage-volume-cache', mountPath: '/root/.nuget')]
        }

        script.podTemplate(podParams) {
            closure()
        }
    }

    /**
     * A method for constructing containers for a function podTemplate
     * https://www.jenkins.io/doc/pipeline/steps/kubernetes/#podtemplate-define-a-podtemplate-to-use-in-the-kubernetes-plugin
     * @param jenkinsAgentImage - image for jnlp container
     * @param images - which containers to start
     * @param DockerImage - image for docker container
     * @param HelmImage - image for helm container
     * @return container structure ready for transfer to podTemplate
     */
    private Object[] constructContainer(String jenkinsAgentImage, String[] images, String DockerImage, String HelmImage) {
        def res = [
                script.containerTemplate(
                        name: "jnlp", image: jenkinsAgentImage,
                        resourceRequestMemory: "100Mi", resourceRequestCpu: "100m"
                )
        ]

        if (images.contains("docker")) {
            res << script.containerTemplate(
                    name: "docker", image: DockerImage, privileged: true,
                    resourceRequestMemory: "100Mi", resourceRequestCpu: "100m",
                    envVars: [script.containerEnvVar(key: "DOCKER_TLS_CERTDIR", value: '')]
            )
        }

        if (images.contains("helm")) {
            res << script.containerTemplate(name: "helm", image: HelmImage, command: "sleep", "args": "99d")
        }

        return res
    }
}
