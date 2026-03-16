/** Pipeline Steps */
enum PipelineStage {
    CheckImage,
    BuildApplication,
    BuildDockerImage,
    RunTests,
    RunAutoTests,
    RunCodeStyleCheck,
    CreateReleaseImage,
    BuildPackage,
    PushPackage,
    CreateTag,
    DeployApplication
}
