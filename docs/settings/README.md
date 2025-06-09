# Description files setting for pipeline and service

- File **required** [./Jenkinsfile](./jenkinsfile.md)
```
Contains settings for calling pipeline
```

- File **required** [`./deploy/deploy.yaml`](./deploy.md)
```
Contains settings for pipeline and deploy
```

- File *optional* [`./deploy/common.yaml`](./common.md)
```
Contains settings for all service, helm and makefile.
Used for mono repository!
```

- File **required** [`./deploy/<artifact name>.yaml`](./service.md)
```
Contains settings for service, helm and makefile
```