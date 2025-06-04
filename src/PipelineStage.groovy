/** Pipeline Steps */
enum PipelineStage {
    CheckImage,
    BuildApplication,
    BuildDockerImage,
    RunTests,
    RunCodeStyleCheck,
    CreateReleaseImage,
    BuildPackage,
    PushPackage,
    CreateTag,
    DeployApplication
}
