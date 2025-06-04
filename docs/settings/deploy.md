## Description setting `deploy.yaml` file
- `clusterName` - jenkins agent name `Dashboard > Manage Jenkins > Clouds`
- `project` - project name
- `registryCredentialsId` - credentials from jenkins for deploy images
- `registryImageUrl` - url registry for pull/push images(**overrides** `registryImagePullUrl` and `registryImagePushUrl`)
- `registryImagePullUrl` - url registry for pull images
- `registryImagePushUrl` - url registry for push images(**required** if set `registryImagePullUrl`)
- `registryPackageUrl` - url registry for deploy package
- `serviceAccount` - for deploy jenkins agent
- `defaultValues` - path default values file
- `microserviceValues` - path final values file for deploy service
- `secretValues` - path final values-secret file for deploy service
- `secret` - setting secrets provider
  - `provider` - module name secret(vault, ...) provider
  - `vaultUrl` - url to vault
  - `vaultAppRoleCredential` - credentials from jenkins for get secret
- `yaml` - custom settings for "Pod Templates jenkins agent(k8s)"

## Example `deploy.yaml` file
```
clusterName: ["k8s-prod", "k8s-stage"]
project: "project1"
registryCredentialsId: "nexus_admin"
registryImageUrl: "artifacts-docker.example.com"
defaultValues: "./.helm/values.yaml"
microserviceValues: "./.helm/microservice_values.yaml"
secretValues: "./.helm/secret_values.yaml"
serviceAccount: "deploy-agent"
gitCredentialsId: "jenkins-cicd"
secret:
    provider: vault
    vaultUrl: "https://vault.example.com"
    vaultAppRoleCredential: "jenkins-role-backend"
yaml: |-
    spec:
    hostAliases:
    - ip: '10.0.0.35'
    hostnames:
        - 'jenkins.example.com'
```

```
clusterName: ["bs-01-stage", "bs-01-prod"]
project: "bs-extractor-expert"
registryCredentialsId: "artifacts-registry"
registryImagePullUrl: "artifacts-docker-group.example.com"
registryImagePushUrl: "artifacts-docker.example.com"
registryPackageUrl: "https://artifacts.example.com/repository/pypi-hosted/"
secret:
  provider: vault
  vaultUrl: "https://vault.example.com"
  vaultAppRoleCredential: "jenkins-backend-role"
serviceAccount: "deploy-agent"
gitCredentialsId: "jenkins-cicd"
yaml: |-
  spec:
    nodeSelector:
      node-role.kubernetes.io/worker-pt: ""
```

```
clusterName: ["bs-01-stage", "bs-01-prod"]
project: "bs-extractor-expert"
registryCredentialsId: "artifacts-registry"
registryImagePullUrl: "artifacts-docker-group.example.com"
registryImagePushUrl: "artifacts-docker.example.com"
secret:
  provider: vault
  vaultUrl: "https://vault.example.com"
  vaultAppRoleCredential: "jenkins-backend-role"
serviceAccount: "deploy-agent"
gitCredentialsId: "jenkins-cicd"
yaml: |-
  spec:
    nodeSelector:
      node-role.kubernetes.io/worker-pt: ""
```