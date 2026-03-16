## Description setting Jenkinsfile
- `@Library(['jenkins-pipeline-opensource']) _` - connecting the library
- `env.DEBUG` - set debug mode. `true\false`. *Default: false*
- `gitHubFlow()` - main function to calling pipeline
  - `first param(serviceSetting)` = service setting (type: **map**)
    - `artifact_name` **required** - artifact name for getting settings (values, vault, ...). *Default: empty*
    - `type` - deploy type. *Default: service* (type: **list**)
      - `service` - build, push and deploy service
      - `python-package` - build and push package to pypi registry
      - `raw-package` - build and push package to raw registry
      - `nuget-package` - build and push package to nuget registry
    - `has_autotests` - enable automated tests stage. *Default: false*
    - `autotests_job_path` **required if has_autotests: true** - full path to Jenkins autotests job. *Default: empty*
      - Example: `bs-sociotech/bs--sociotech--qa-tests/main`
    - `autotests_env_param_name` - name of the environment parameter in the autotests job. *Default: TARGET_ENV_NAME*
      - Use if your autotests job uses a different parameter name (e.g. `env`, `stand`, `environment`)
    - `autotests_job_parameters` - additional parameters to pass to the autotests job, excluding environment (type: **list of string()**). *Default: empty*
      - Environment is always injected automatically via `autotests_env_param_name`
  - `second param(checks)` - (type: **list**) *(deprecated, use serviceSetting params instead)*
    - `unittest` - enable unittest. *Default: skip test*
    - `stylecheck` - enable stylecheck. *Default: skip check*
  - `third param(k8sCloud)` - Jenkins cloud settings (`Dashboard > Manage Jenkins > Clouds`) (type: **map**)
    - `cloud` - Jenkins agent name. *Default: chosen by name in `Dashboard > Manage Jenkins > Clouds` and `environment`*
    - `yaml` - custom pod yaml. *Default: empty*
    - `volumes` - pod volumes. *Default: empty*
    - `serviceAccount` - service account for Jenkins agent. *Default: empty*

## Autotest behavior by branch
| Branch | has_autotests | Behavior |
|--------|--------------|----------|
| feature/* | false | No autotests stage |
| feature/* | true | Checkbox available, unchecked by default. Optional run |
| master | false | No autotests stage, tag created immediately after build |
| master | true | Autotests checked by default, can be unchecked manually. Deploy to preprod → autotests → tag created only on success. If unchecked — tag is created without running autotests |
| tag | any | No autotests (already ran on master before tag was created) |

## Example `Jenkinsfile` file

Minimal — service without autotests:
```groovy
@Library(['jenkins-pipeline-opensource']) _
env.DEBUG=false
gitHubFlow([artifact_name: "sociotech-api"])
```

Service with autotests (standard qa-tests job):
```groovy
@Library(['jenkins-pipeline-opensource']) _
env.DEBUG=false
gitHubFlow([
    artifact_name: "sociotech-ui",
    has_autotests: true,
    autotests_job_path: 'bs-sociotech/bs--sociotech--qa-tests/main',
    autotests_job_parameters: [
        string(name: 'BROWSER', value: 'all'),
        string(name: 'TAGS', value: '')
    ]
])
```

Service with autotests (custom job with different parameter names):
```groovy
@Library(['jenkins-pipeline-opensource']) _
env.DEBUG=false
gitHubFlow([
    artifact_name: "other-service",
    has_autotests: true,
    autotests_job_path: 'bs-sociotech/other-qa-tests/main',
    autotests_env_param_name: 'env',
    autotests_job_parameters: [
        string(name: 'api', value: 'v2'),
        string(name: 'type', value: 'regression')
    ]
])
```

Service with multiple repository types:
```groovy
@Library(['jenkins-pipeline-opensource']) _
env.DEBUG=false
gitHubFlow([artifact_name: "test-updaterworker", type: ["service", "nuget-package"]], ["unittest"])
```