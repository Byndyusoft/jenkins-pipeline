/** Wrapper over the podTemplate */
class Kubernetes {
    private final def script

    Kubernetes(script) {
        this.script = script
    }

    def customPodTemplate(KubernetesConfig kubernetesConfig, Closure closure) {
        Map podParams = [
                cloud            : kubernetesConfig.cloudName,
                inheritFrom: 'default'
        ]

        if (kubernetesConfig.podTemplateYaml) {
            podParams.yaml = """${kubernetesConfig.podTemplateYaml}"""
        }

        if (kubernetesConfig.podTemplateVolumes) {
            volumes = []
            if (kubernetesConfig.podTemplateVolumes.get('persistentVolumeClaim')) {
                for (pvc in kubernetesConfig.podTemplateVolumes.get('persistentVolumeClaim')) {
                    volumes.add(script.persistentVolumeClaim(claimName: pvc['claimName'], mountPath: pvc['mountPath']))
                }
                podParams.volumes = volumes
            }
        }

        script.podTemplate(podParams) {
            closure()
        }
    }
}
