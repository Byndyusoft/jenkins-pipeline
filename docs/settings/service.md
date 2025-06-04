## Description setting `<name>.yaml` file

## Example `<name>.yaml` file
```
microservice:
  kind: "Deployment"

  env:
    Caching__Redis__Enabled:
      default: "true"

  replicas:
    default: 1
    preprod: 2
    prod: 6

  resourcesLimitsMemory:
    default: 512Mi
  resourcesRequestsMemory:
    default: 128Mi
  resourcesLimitsCPU:
    default: 2
  resourcesRequestsCPU:
    default: 0.1

  servicePorts:
    - port: 80
      targetPort: 5000
      protocol: TCP
      name: application

  containerPorts:
    - containerPort: 5000
      name: http
      protocol: TCP

  readinessProbe:
    httpGet:
      port: 5000

  ingress:
    enabled: true
    ingressClassName: haproxy
    labels: {}
    annotations:
      default:
        haproxy-ingress.github.io/backend-server-naming: pod
        haproxy-ingress.github.io/maxconn-server: '50'
        haproxy-ingress.github.io/maxqueue-server: '4096'
        haproxy-ingress.github.io/ssl-redirect: 'false'
        cert-manager.io/cluster-issuer: letsencrypt-staging-haproxy
      prod:
        haproxy-ingress.github.io/backend-server-naming: pod
        haproxy-ingress.github.io/maxconn-server: '50'
        haproxy-ingress.github.io/maxqueue-server: '4096'
        haproxy-ingress.github.io/ssl-redirect: 'false'
        cert-manager.io/cluster-issuer: letsencrypt-haproxy
    hosts:
      - host:
          default: api.stage.example.com
          prod: api.example.com
        path: /
        pathType: ImplementationSpecific
        servicePorts: 5000
    tls:
      - secretName: api-tls
        hosts:
          default: api.stage.example.com
          prod: api.example.com

  volumes:
    share-mediastorage:
      mountPath: "/data"
      config:
        nfs:
          server: "10.0.11.40"
          path: "/shares/share"
          readOnly: false

  nodeSelector:
    node-role.kubernetes.io/worker: ""

makefile:
  env:
    <set env for makefile>
```