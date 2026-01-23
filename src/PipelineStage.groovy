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
    PackPackage,
    PushPackage,
    CreateTag,
    DeployApplication
}
