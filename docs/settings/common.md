## Description setting `common.yaml` file

## Example `common.yaml` file
```
common:
  kind: "Deployment"

  env:
    Logging__LogLevel__Default:
      default: "warning"
    ASPNETCORE_URLS:
      default: "http://*:5000"
    ASPNETCORE_ENVIRONMENT:
      prod: "Production"
      default: "staging"
    DOTNET_ENVIRONMENT:
      prod: "Production"
      default: "staging"
    Jaeger__AgentHost:
      default: "jaeger-agent.jaeger"
    Jaeger__AgentPort:
      default: "6831"

  replicas:
    default: 1
    preprod: 1
    prod: 1

  resourcesLimitsMemory:
    default: 256Mi
  resourcesRequestsMemory:
    default: 64Mi
  resourcesLimitsCPU:
    default: 1
  resourcesRequestsCPU:
    default: 0.1

  imagePullSecrets:
    - name: registry

  initContainers:
    enable: false
    command: ""

  livenessProbe:
    enabled: false

  readinessProbe:
    enabled: false

  volumes: {}

  nodeSelector: []

  tolerations: []

```