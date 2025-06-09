/** The class of access to environment variables */
class EnvironmentVariables {

    final boolean DEBUG
    /**For a multibranch project, this will be set to the name of the branch being built,
     * for example in case you wish to deploy to production from master but not from feature branches;
     * if corresponding to some kind of change request, the name is generally arbitrary (refer to CHANGE_ID and CHANGE_TARGET).*/
    final String BRANCH_NAME
    /**The current build number, such as "153".*/
    final String BUILD_NUMBER
    /**For a multibranch project corresponding to some kind of tag,
     * this will be set to the name of the tag being built, if supported; else unset.*/
    final String TAG_NAME
    /**For a multibranch project corresponding to some kind of change request,
     * this will be set to the change ID, such as a pull request number, if supported; else unset.*/
    final String CHANGE_ID

    EnvironmentVariables(env) {
        DEBUG = env.DEBUG.toBoolean()
        BRANCH_NAME = env.BRANCH_NAME
        BUILD_NUMBER = env.BUILD_NUMBER
        TAG_NAME = env.TAG_NAME
        CHANGE_ID = env.CHANGE_ID
    }
}
