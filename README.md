# Jenkins Shared Library

Reusable Jenkins Shared Library for standardizing CI/CD pipelines across Python microservice teams. Covers build, test, Docker, Kubernetes deployment, Slack notifications, and LLM-powered failure analysis.

## Library Structure

```
vars/
├── buildPython.groovy        # install deps, lint, pytest, coverage enforcement
├── dockerBuildPush.groovy    # build + push Docker image with SHA and latest tags
├── deployK8s.groovy          # kubectl apply with rollout status + auto-rollback
├── notifySlack.groovy        # webhook-based build status notifications
└── llmAnalyzeFailure.groovy  # ships build log to LLM API, posts root cause to Slack
```

## Jenkins Setup

Go to **Manage Jenkins > Configure System > Global Pipeline Libraries** and add:

| Field | Value |
|---|---|
| Name | `groovylibrary` |
| Default version | `main` |
| Retrieval method | Modern SCM (GitHub) |
| Repository URL | `https://github.com/gerardrecinto/groovylibrary` |

---

## Steps

### `buildPython(config)`

Installs requirements, runs flake8 lint, then pytest with coverage. Fails the build if coverage drops below the threshold.

```groovy
buildPython(
    pythonVersion: '3.11',
    requirementsFile: 'requirements.txt',
    testDir: 'tests/',
    coverageThreshold: 80
)
```

---

### `dockerBuildPush(config)`

Builds the image and pushes two tags: the commit SHA and `latest`.

```groovy
dockerBuildPush(
    imageName: 'my-service',
    registry: 'registry.example.com',
    credentialsId: 'docker-registry-creds',
    dockerfile: 'Dockerfile'
)
```

---

### `deployK8s(config)`

Applies the manifest and waits on `kubectl rollout status`. Triggers a rollback if the rollout times out.

```groovy
deployK8s(
    manifestPath: 'k8s/deployment.yaml',
    namespace: 'production',
    kubeConfigCredential: 'kubeconfig-prod',
    timeoutMinutes: 5
)
```

---

### `notifySlack(config)`

Posts a color-coded build status message to Slack. Accepts SUCCESS, FAILURE, or UNSTABLE.

```groovy
notifySlack(
    status: 'SUCCESS',
    channel: '#ci-cd',
    webhookCredential: 'slack-webhook-url',
    message: "Build #${env.BUILD_NUMBER} deployed to production"
)
```

---

### `llmAnalyzeFailure(config)`

On failure, grabs the last N lines of the build log, sends them to the LLM API with a triage prompt, and posts the response to Slack as a thread reply on the failure alert. Useful for catching missing dependencies, flaky test patterns, and config drift without manually digging through logs.

```groovy
llmAnalyzeFailure(
    apiCredential: 'openai-api-key',
    slackChannel: '#ci-failures',
    logLines: 100,
    model: 'gpt-4o'
)
```

Sample Slack output from the LLM:
```
Root cause: ModuleNotFoundError on 'boto3' in src/uploader.py.
boto3 is not in requirements.txt.
Fix: add boto3>=1.34.0 to requirements.txt and rerun.
```

---

## Example Pipeline

```groovy
@Library('groovylibrary') _

pipeline {
    agent { label 'python-agent' }

    parameters {
        choice(name: 'ENVIRONMENT', choices: ['staging', 'production'], description: 'Target env')
        booleanParam(name: 'SKIP_TESTS', defaultValue: false, description: '')
    }

    environment {
        IMAGE_NAME = 'my-python-service'
        REGISTRY   = 'registry.example.com'
    }

    stages {
        stage('Build') {
            steps {
                buildPython(pythonVersion: '3.11', coverageThreshold: 80)
            }
        }
        stage('Docker') {
            steps {
                dockerBuildPush(
                    imageName: env.IMAGE_NAME,
                    registry: env.REGISTRY,
                    credentialsId: 'docker-creds'
                )
            }
        }
        stage('Deploy') {
            steps {
                deployK8s(
                    manifestPath: 'k8s/',
                    namespace: params.ENVIRONMENT,
                    kubeConfigCredential: 'kubeconfig'
                )
            }
        }
    }

    post {
        success {
            notifySlack(status: 'SUCCESS', channel: '#deployments', webhookCredential: 'slack-webhook')
        }
        failure {
            llmAnalyzeFailure(apiCredential: 'openai-api-key', slackChannel: '#ci-failures')
            notifySlack(status: 'FAILURE', channel: '#deployments', webhookCredential: 'slack-webhook')
        }
    }
}
```
