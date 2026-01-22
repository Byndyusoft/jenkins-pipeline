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
    BuildPackage,
    PushPackage,
    CreateTag,
    DeployApplication
}
