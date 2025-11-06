class SemanticVersion implements Serializable {
    final int major
    final int minor
    final int patch

    private SemanticVersion(int major, int minor, int patch) {
        this.major = major
        this.minor = minor
        this.patch = patch
    }

    static SemanticVersion parse(String version) {
        if (!version) return null
        def versionParts = version.tokenize('.')
        if (versionParts.size != 3) {
            throw new IllegalArgumentException("Wrong version format - expected MAJOR.MINOR.PATCH - got ${version}")
        }
        try {
            return new SemanticVersion(
                versionParts[0].toInteger(),
                versionParts[1].toInteger(),
                versionParts[2].toInteger()
            )
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Version parts must be integers - got ${version}", e)
        }
    }

    SemanticVersion increase(PatchLevel patchLevel) {
        switch (patchLevel) {
            case PatchLevel.MAJOR:
                return new SemanticVersion(major + 1, 0, 0)
            case PatchLevel.MINOR:
                return new SemanticVersion(major, minor + 1, 0)
            case PatchLevel.PATCH:
                return new SemanticVersion(major, minor, patch + 1)
            default:
                throw new IllegalArgumentException("Unknown patchLevel: ${patchLevel}")
        }
    }

    String toPreReleaseVersion(String branchName, String buildNumber, String commitHash) {
        def preparedBranchName = branchName.toLowerCase()
        return "${this.toString()}-${preparedBranchName}-${buildNumber}-${commitHash}"
    }

    String toString() {
        return "${major}.${minor}.${patch}"
    }
}
