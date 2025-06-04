/** The class of service settings specified in JenkinsFile */
class JenkinsFileSettings {
    /** Name of the service */
    String artifactName
    /** Repository type */
    List<RepositoryType> repositoryTypes

    void initialize(Map serviceSetting) {
        Utils utils = new Utils()
        artifactName = utils.prepareName(serviceSetting.artifact_name as String)
        repositoryTypes = mapRepositoryType(serviceSetting.type as List<String> ?: [])
    }

    private List<RepositoryType> mapRepositoryType(List<String> repositoryTypeOptions) {
        List repoTypes = []

        for (repositoryTypeOption in repositoryTypeOptions) {
            switch (repositoryTypeOption) {
                case 'python-package':
                    repoTypes.add(RepositoryType.PythonPackage)
                    break
                case 'raw-package':
                    repoTypes.add(RepositoryType.RawPackage)
                    break
                case 'nuget-package':
                    repoTypes.add(RepositoryType.NugetPackage)
                    break
                case 'service':
                    repoTypes.add(RepositoryType.Service)
                    break
            }
        }

        return (repoTypes) ?: [RepositoryType.Service]
    }
}
