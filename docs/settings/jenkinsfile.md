## Description setting deploy.yaml file

- `@Library(['bs-shared-library']) _` - connecting the library
- `env.DEBUG` - set debug mode. `true\false`. *Default: false*
- `gitHubFlow()` - main function to calling pipeline
  - `first param(artifactSetting)` = service setting(type: **map**)
    - `artifact_name` **required** - artifact_name for get setting(values, vault, ...). *Default: empty*
    - `type` - type deploy(python-package, ...). *Default: empty* (type: **list**)
      - `service` - build, push and deploy service
      - `python-package` - build and push package to pypi registry
      - `raw-package` - build and push package to raw registry
      - `nuget-package` - build and push package to nuget registry
  - `second param(checks)` - (type: **list**)
    - `unittest` - enable unittest. Default: skip test
    - `stylecheck` - enable stylecheck. *Default: skip check*

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