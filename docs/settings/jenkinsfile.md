## Description setting deploy.yaml file

- `@Library(['bs-shared-library']) _` - connecting the library
- `env.DEBUG` - set debug mode. `true\false`. *Default: false*
- `gitHubFlow()` - main function to calling pipeline


## Example `Jenkinsfile` file
```
@Library(['bs-shared-library']) _

env.debug=false

gitHubFlow()
```

## Example `Jenkinsfile` file
```
@Library(['bs-shared-library']) _

env.debug=false

gitHubFlow("service1", ["microservice1.yaml", "microservice2.yaml"])
```