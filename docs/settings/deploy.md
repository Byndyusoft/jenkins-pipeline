## Docs
- [kubernetes-plugin docs](https://github.com/jenkinsci/kubernetes-plugin?tab=readme-ov-file#pod-template)

## Description setting `deploy.yaml` file
- `clusters` - jenkins agent name `Dashboard > Manage Jenkins > Clouds`
- `project` - project name
- `serviceName` - service name
- `customPrefixNamespace` - Custom prefix for namespace. Replace `project` and `serviceName` only in namespace
- `registryCredentialsId` - credentials from jenkins for deploy images
- `registryImageUrl` - url registry for pull/push images(**overrides** `registryImagePullUrl` and `registryImagePushUrl`)
- `registryImagePullUrl` - url registry for pull images
- `registryImagePushUrl` - url registry for push images(**required** if set `registryImagePullUrl`)
- `registryPackageUrl` - url registry for deploy package
- `serviceAccount` - for deploy jenkins agent
- `defaultValues` - path default values file
- `serviceValues` - path final values file for deploy service
- `secret` - setting secrets provider
  - `provider` - module name secret(vault, ...) provider
  - `vaultUrl` - url to vault
  - `vaultAppRoleCredential` - credentials from jenkins for get secret
- `yaml` - custom settings for "Pod Templates jenkins agent(k8s)"
- `volumes` - volumes for "Pod Templates jenkins agent(k8s)".
  - `persistentVolumeClaim` - an existing persistent volume claim by name
     - `claimName` - claim name
     - `mountPath` - path to mount this volume inside the pod
 

## Example `deploy.yaml` file
```
clusters:
  prod:
    deployCloudNames: [k8s-prod]
    environments:
      prod:
        important: true
  tech:
    buildCloudNames: [k8s-tech]
    environments: {}
project: "project1"
serviceName: "test"
registryCredentialsId: "nexus_admin"
registryImageUrl: "artifacts-docker.example.com"
defaultValues: "./.nelm/values.yaml"
serviceValues: "./.nelm/service_values.yaml"
gitCredentialsId: "jenkins-cicd"
secret:
    provider: vault
    vaultUrl: "https://vault.example.com"
    vaultAppRoleCredential: "jenkins-role-backend"
```

```
clusters:
  prod:
    deployCloudNames: [k8s-prod]
    environments:
      prod:
        important: true
  tech:
    buildCloudNames: [k8s-tech]
    environments: {}
project: "project1"
serviceName: "test"
registryCredentialsId: "artifacts-registry"
registryImagePullUrl: "artifacts-docker-group.example.com"
registryImagePushUrl: "artifacts-docker.example.com"
defaultValues: "./.nelm/values.yaml"
serviceValues: "./.nelm/service_values.yaml"
secret:
  provider: vault
  vaultUrl: "https://vault.example.com"
  vaultAppRoleCredential: "jenkins-backend-role"
gitCredentialsId: "jenkins-cicd"
volumes: 
  persistentVolumeClaim:
    - claimName: 'nuget-cache-volume'
      mountPath: '/root/.nuget'
```