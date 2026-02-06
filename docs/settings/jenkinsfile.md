## Description setting deploy.yaml file

- `@Library(['bs-shared-library']) _` - connecting the library
- `env.DEBUG` - set debug mode. `true\false`. *Default: false*
- `gitHubFlow()` - main function to calling pipeline

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