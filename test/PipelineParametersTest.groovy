class JenkinsParametersStub {
    Map params
    Map env
    List installedParameters

    def booleanParam(Map value) { value }
    def reactiveChoice(Map value) { value }
    def groovyScript(Map value) { value }
    def choice(Map value) { value }

    def parameters(List value) {
        installedParameters = value
        value
    }

    void properties(List value) {}
}

def repository = new File('.').canonicalFile
def loader = new GroovyClassLoader(this.class.classLoader)
loader.addClasspath(
    new File(
        repository,
        'src'
    ).path
)
loader.addClasspath(
    new File(
        repository,
        'vars'
    ).path
)

def parametersType = loader.loadClass('PipelineParameters')
def settingsType = loader.loadClass('JenkinsFileSettings')
def environmentType = loader.loadClass('EnvironmentVariables')
def deployConfigType = loader.loadClass('DeployConfig')
def loggerType = loader.loadClass('Logger')
def stageType = loader.loadClass('PipelineStage')

def initialize = { Map options, String selection ->
    def environment = [
        DEBUG: 'false',
        BRANCH_NAME: options.branch ?: 'feature/package-publish',
        BUILD_NUMBER: options.buildNumber ?: '42',
        TAG_NAME: options.tag
    ]
    def script = new JenkinsParametersStub(
        env: environment,
        params: [
            reload: options.reload ?: false,
            make_release: options.release ? 'Make Release' : '',
            'Build parameters': selection,
            'Deployment environment': 'preprod'
        ]
    )
    def logger = loggerType.newInstance()
    logger.binding = new Binding([env: environment])

    def settings = settingsType.newInstance()
    settings.initialize([artifact_name: 'test-package', type: options.types ?: ['nuget-package']])

    def deployConfig = deployConfigType.newInstance(logger)
    deployConfig.additionalDeployEnvironments = ['test']

    def parameters = parametersType.newInstance(
        script,
        logger
    )
    parameters.initialize(
        settings,
        environmentType.newInstance(environment),
        deployConfig
    )

    [
        script: script,
        parameters: parameters,
        stages: stageType.enumConstants.findAll { parameters.stageAvailable(it) }*.name() as Set
    ]
}

def simulateForm = { Map options = [:] ->
    def allOptions = 'Build application,Deploy application,Run tests,Run code style check,Build package,Publish package'
    def previous = initialize(
        options + [release: options.previousRelease ?: false],
        allOptions
    )
    def form = previous.script.installedParameters.find { it.name == 'Build parameters' }
    def binding = new Binding([
        reload: options.reload ?: false,
        make_release: options.release ? 'Make Release' : ''
    ])
    def choices = new GroovyShell(binding).evaluate(form.script.script.script).collect { it.toString() }
    def labels = choices.collect { it.tokenize(':').first() }
    def selection = choices.findAll { it.tokenize(':').contains('selected') }
        .collect { it.tokenize(':').first() }
        .findAll { !(it in (options.deselect ?: [])) }
        .join(',')
    def current = initialize(options, selection)

    current + [choices: choices, labels: labels]
}

def cases = [:]
def packageLabels = ['Run tests', 'Run code style check', 'Build package', 'Publish package']
def packageStages = ['RunTests', 'RunCodeStyleCheck', 'BuildPackage', 'PushPackage'] as Set

['nuget-package', 'python-package', 'raw-package'].each { type ->
    cases["${type}: feature offers and runs package stages"] = {
        def result = simulateForm([types: [type]])

        assert result.labels == packageLabels
        assert result.stages == packageStages
        assert result.choices.every { it.endsWith(':selected') }
    }

    cases["${type}: master without release publishes without a tag"] = {
        def result = simulateForm([types: [type], branch: 'master'])

        assert result.labels == packageLabels
        assert result.stages == packageStages
    }

    cases["${type}: master release locks package stages and creates a tag"] = {
        def result = simulateForm([types: [type], branch: 'master', release: true])

        assert result.labels == packageLabels
        assert result.stages == packageStages + ['CreateTag']
        assert result.choices.every { it.endsWith(':selected:disabled') }
    }
}

cases['package: unchecking publish still packs'] = {
    def result = simulateForm([deselect: ['Publish package']])

    assert result.stages == ['RunTests', 'RunCodeStyleCheck', 'BuildPackage'] as Set
}

cases['package: unchecking build also prevents publish'] = {
    def result = simulateForm([deselect: ['Build package']])

    assert result.stages == ['RunTests', 'RunCodeStyleCheck'] as Set
}

cases['package: checks can be unchecked for a non-release build'] = {
    def result = simulateForm([deselect: ['Run tests', 'Run code style check']])

    assert result.stages == ['BuildPackage', 'PushPackage'] as Set
}

def serviceLabels = ['Build application', 'Deploy application', 'Run tests', 'Run code style check']
def serviceStages = [
    'CheckImage',
    'BuildApplication',
    'BuildDockerImage',
    'RunTests',
    'RunCodeStyleCheck',
    'DeployApplication'
] as Set

['feature/service', 'master'].each { branch ->
    cases["service: ${branch} offers only application stages"] = {
        def result = simulateForm([types: ['service'], branch: branch])

        assert (result.labels as Set) == (serviceLabels as Set)
        assert result.stages == serviceStages
    }
}

cases['service: release toggle locks stages and hides deployment'] = {
    def result = simulateForm([types: ['service'], branch: 'master', release: true])

    assert (result.labels as Set) == ((serviceLabels - ['Deploy application']) as Set)
    assert result.choices.every { it.endsWith(':selected:disabled') }
    assert result.stages == serviceStages - ['DeployApplication'] + ['CreateTag', 'CreateReleaseImage']
}

cases['service: turning off release restores deployment and unlocks stages'] = {
    def result = simulateForm([
        types: ['service'],
        branch: 'master',
        previousRelease: true,
        release: false
    ])

    assert (result.labels as Set) == (serviceLabels as Set)
    assert result.choices.every { it.endsWith(':selected') }
    assert result.stages == serviceStages
}

cases['service: tag offers only its available build and deployment stages'] = {
    def result = simulateForm([types: ['service'], branch: '1.2.3', tag: '1.2.3'])

    assert result.labels == ['Build application', 'Deploy application']
    assert result.stages == ['CheckImage', 'BuildApplication', 'BuildDockerImage', 'DeployApplication'] as Set
}

cases['mixed repository: offers package and application stages once each'] = {
    def result = simulateForm([types: ['service', 'nuget-package']])

    assert (result.labels as Set) == ((serviceLabels + ['Build package', 'Publish package']) as Set)
    assert result.labels.size() == result.labels.toSet().size()
    assert result.stages == serviceStages + packageStages
}

['nuget-package', 'service'].each { type ->
    cases["${type}: reload hides choices and updates parameters only"] = {
        def result = simulateForm([types: [type], reload: true])

        assert result.choices == []
        assert result.parameters.onlyPipelineUpdate
    }
}

cases['first build updates parameters only'] = {
    def result = simulateForm([buildNumber: '1'])

    assert result.parameters.onlyPipelineUpdate
}

def started = System.nanoTime()
int failures = 0
boolean verbose = args.contains('--verbose')

cases.each { name, scenario ->
    try {
        scenario()
        if (verbose) {
            println "PASS ${name}"
        }
    } catch (Throwable failure) {
        failures++
        def diagnostic = verbose ? failure.message : failure.message.readLines().take(8).join('\n')
        System.err.println("FAIL ${name}\n${diagnostic}")
        if (verbose) {
            failure.printStackTrace()
        }
    }
}

def duration = (System.nanoTime() - started) / 1_000_000
println "Tests: ${cases.size()}, passed: ${cases.size() - failures}, failed: ${failures}, skipped: 0 (${duration.longValue()} ms)"
if (failures) {
    System.exit(1)
}
