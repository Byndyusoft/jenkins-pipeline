/** Class for working with git */
class Git {
    private final def script
    private final DeployConfig deployConfig

    Git(script, DeployConfig deployConfig) {
        this.script = script
        this.deployConfig = deployConfig
    }

    String getCommitShaShort() {
        return script.sh(returnStdout: true, script: 'git rev-parse HEAD').trim().take(7)
    }

    SemanticVersion getCurrentTagForBranch() {
        String currentTagForBranch = script.sh(returnStdout: true, script: 'git describe --tags --abbrev=0 --always').trim()
        return (checkSemanticVersion(currentTagForBranch)) ? new SemanticVersion(currentTagForBranch) : null
    }

    String createTag(SemanticVersion semanticVersion) {
        script.withCredentials([script.usernamePassword(credentialsId: deployConfig.gitCredentialsId,
                usernameVariable: 'GIT_LOGIN',
                passwordVariable: 'GIT_TOKEN')]) {
            script.sh("git config --global --add safe.directory \$(pwd)")

            String repoUrl = script.sh(returnStdout: true, script: 'git remote get-url origin | awk -F  \'://\' \'{print $2}\'').trim()

            // ToDo Do I need to get it from a file?
            script.sh("git config --global user.email 'cicd@example.com'")
            script.sh("git config --global user.name 'Manages CI/CD'")

            script.sh("""git tag -a ${semanticVersion.toString()} -m "Created CI/CD. ${semanticVersion.toString()}" """)
            script.sh("git push https://${script.GIT_TOKEN}@${repoUrl} --tags")
        }
    }

    SemanticVersion findLatestSemVerTag() {
        script.sh("git config --global --add safe.directory \$(pwd)")

        // Docs https://git-scm.com/docs/git-for-each-ref#_field_names
        String gitLastTag = script.sh(returnStdout: true, script: 'git tag --sort=creatordate | tail -1').trim()

        if (gitLastTag == null || gitLastTag.trim() == '') {
            gitLastTag = '0.0.0'
        }

        if (checkSemanticVersion(gitLastTag)) {
            return new SemanticVersion(gitLastTag)
        }

        return script.error("Latest git tag is not semantic. Latest tag is ${gitLastTag}")
    }

    /** Check semantic version */
    private boolean checkSemanticVersion(String gitLastTag) {
        // For regex debug - https://regex101.com/r/vkijKf/1/
        String semVerRegex = '^(0|[1-9]\\d*)\\.(0|[1-9]\\d*)\\.(0|[1-9]\\d*)(?:-((?:0|[1-9]\\d*|\\d*[a-zA-Z-][0-9a-zA-Z-]*)' +
                '(?:\\.(?:0|[1-9]\\d*|\\d*[a-zA-Z-][0-9a-zA-Z-]*))*))?(?:\\+([0-9a-zA-Z-]+(?:\\.[0-9a-zA-Z-]+)*))?$'

        return (gitLastTag ==~ semVerRegex) ? true : false
    }
}
