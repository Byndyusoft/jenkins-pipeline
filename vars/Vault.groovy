/** Class for working with Vault secret storages */
class Vault {
    private final def script
    private final String vaultUrl
    private final String vaultCredentialsId

    Vault(script, DeployConfig deployConfig) {
        this.script = script
        vaultUrl = deployConfig.secretProvider.url ?: 'http://vault:8200'
        vaultCredentialsId = deployConfig.secretProvider.credentialsId
    }

    Map getVaultSecret(String vaultPath) {
        if (vaultPath == null) {
            script.error('vaultUtils require vaultPath')
        }

        if (vaultCredentialsId == null) {
            script.error('vaultUtils require vaultAppRoleCredential')
        }

        String pathSplitChar = '/'
        List<String> pathParts = vaultPath.split(pathSplitChar).toList().reverse()
        String kvName = pathParts.pop()
        String pathInVault = pathParts.reverse().join(pathSplitChar)

        return script.withCredentials([script.usernamePassword(credentialsId: "$vaultCredentialsId", usernameVariable: 'USERNAME', passwordVariable: 'PASSWORD')]) {
            Map vaultAuthJson = jsonFromCurl(
                'POST',
                null,
                "{\"role_id\":\"$script.USERNAME\",\"secret_id\":\"$script.PASSWORD\"}".replace('"', '\\"'),
                "$vaultUrl/v1/auth/approle/login")

            Map vaultSecrets = jsonFromCurl(
                'GET',
                ['X-Vault-Token': vaultAuthJson.auth.client_token],
                null,
                 "$vaultUrl/v1/$kvName/data/$pathInVault")

            if (vaultSecrets.data == null)
                return [:]

            return vaultSecrets.data.data
        }
    }

    private Map jsonFromCurl(String method, Map headers, String data, String url) {
        String curlCmd = "set +x && curl -k ${script.env.DEBUG == 'true' ? '-v' : ''} --request $method "

        if (headers != null) {
            headers.each { key, val -> curlCmd += "--header \"$key:$val\" " }
        }

        if (data != null) {
            curlCmd += "--data \"$data\" "
        }

        curlCmd += "$url"

        // https://www.jenkins.io/doc/pipeline/steps/pipeline-utility-steps/#readjson-read-json-from-files-in-the-workspace
        Map json = script.readJSON text: (script.sh(returnStdout: true, script: "$curlCmd").trim())

        return json
    }
}
