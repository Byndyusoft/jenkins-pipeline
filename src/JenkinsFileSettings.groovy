/** The class of service settings specified in JenkinsFile */
class JenkinsFileSettings {
    /** Name of the service */
    String repositoryName
    /** Artifact type */
    List<RepositoryType> repositoryTypes

    void initialize(Map artifactSetting) {
        Utils utils = new Utils()
        repositoryName = utils.prepareName(artifactSetting.repository_name as String)
        repositoryTypes = mapRepositoryType(artifactSetting.repository_types as List<String> ?: [])
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
