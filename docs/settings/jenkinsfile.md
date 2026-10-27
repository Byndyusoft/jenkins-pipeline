## Description setting deploy.yaml file

- `@Library(['bs-shared-library']) _` - connecting the library
- `env.DEBUG` - set debug mode. `true\false`. *Default: false*
- `gitHubFlow()` - main function to calling pipeline
  - `first param(serviceSetting)` = service setting(type: **map**)
    - `artifact_name` **required** - artifact_name for get setting(values, vault, ...). *Default: empty*
    - `type` - type deploy(python-package, ...). *Default: empty* (type: **list**)
      - `service` - build, push and deploy service
      - `python-package` - build and push package to pypi registry
      - `raw-package` - build and push package to raw registry
      - `nuget-package` - build and push package to nuget registry
  - `second param(checks)` - retained for compatibility (type: **list**). The current pipeline does not read this argument; tests and style checks are selected through **Build parameters**.
  - `third param(k8sCloud)` - setting jenkins cloud(`Dashboard > Manage Jenkins > Clouds`) (type: **map**)
    - `cloud` - set jenkins agent name. *Default: choosing by name `Dashboard > Manage Jenkins > Clouds` and `environment`*
    - `yaml` - *Default: empty*
    - `volumes` - *Default: empty*
    - `serviceAccount` - for deploy jenkins agent. *Default: empty*

## Example `Jenkinsfile` file
```
@Library(['bs-shared-library']) _

env.DEBUG=false

gitHubFlow([artifact_name: "checkerworker"])
```

```
@Library(['bs-shared-library']) _

env.debug=false

gitHubFlow([artifact_name: "test-updaterworker", type: ["service", "nuget-package"]], ["unittest"])
```

```
@Library(['bs-shared-library']) _

env.debug=false

gitHubFlow([artifact_name: "test-client", type: ["nuget-package"]], ["unittest"])
```

## Package repositories

For `nuget-package`, `python-package`, and `raw-package`, **Build parameters** offers **Run tests**, **Run code style check**, **Build package**, and **Publish package**. These options are selected by default. Feature branches and master builds without **Make Release** can select the required stages. Unchecking **Publish package** keeps packaging enabled; unchecking **Build package** prevents both packaging and publication.

On master with **Make Release**, package stages and checks are mandatory and the pipeline creates a version tag. Application-only repositories offer application stages instead of package stages; mixed repository types offer both. Release mode excludes application deployment and locks the selected release stages.

After changing the shared-library version, run the existing job with **reload**, reopen **Build with Parameters**, and choose the desired options. Reload updates the form without building or publishing artifacts.
