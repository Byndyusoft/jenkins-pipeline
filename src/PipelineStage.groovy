/** Pipeline Steps */
enum PipelineStage {
    CheckImage,
    InstallDependencies,
    BuildApplication,
    PackApplication,
    BuildDockerImage,
    RunTests,
    RunCodeStyleCheck,
    CreateReleaseImage,
    PackAndPushPackage,
    CreateTag,
    DeployApplication
}
