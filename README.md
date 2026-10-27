## Instruction shared library for jenkins

### Connect shared library to jenkins
- [Manual: install in jenkins](./docs/install.md)

### Configuration pipeline
- [Manual: configuration pipeline](./docs/settings/README.md)

### Deploy to `preprod`
- [Manual: deploy to preprod](./docs/deploy_to_preprod.md)

### Deploy to `prod`
- [Manual: deploy to prod](./docs/deploy_to_prod.md)

## Usage examples
- [Examples](https://github.com/Byndyusoft/jenkins-pipeline-usage-examples.git)

## Checking pipeline parameters

Run the parameter-form and stage-selection checks without connecting to Jenkins:

```sh
./gradlew --quiet testPipelineParameters
```

The checks evaluate the generated form script and feed its selections back into the actual pipeline parameters. They cover package repositories, feature branches, master release modes, application repositories, mixed types, tags, and parameter reload. The default output contains totals and failure diagnostics. Add `-PverboseTests` to print every scenario and full failure details.
