class SemanticVersion implements Serializable {
    private int major
    private int minor
    private int patch

    SemanticVersion(String version) {
        def versionParts = version.tokenize('.')
        if (versionParts.size != 3) {
            throw new IllegalArgumentException("Wrong version format - expected MAJOR.MINOR.PATCH - got ${version}")
        }

        this.major = versionParts[0].toInteger()
        this.minor = versionParts[1].toInteger()
        this.patch = versionParts[2].toInteger()
    }

    void increaseVersion(PatchLevel patchLevel) {
        switch (patchLevel) {
            case PatchLevel.MAJOR:
                major++
                minor = 0
                patch = 0
                break
            case PatchLevel.MINOR:
                minor++
                patch = 0
                break
            case PatchLevel.PATCH:
                patch++
                break
        }
    }

    String toString() {
        return "${major}.${minor}.${patch}"
    }
}
