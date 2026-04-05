#!/usr/bin/env groovy

/**
 * llmAnalyzeFailure
 *
 * Grabs the tail of the Jenkins build log, sends it to the OpenAI chat
 * completions API with a triage prompt, and posts the response as a Slack
 * thread reply on the failure notification.
 *
 * Requires:
 *   - A Jenkins secret-text credential holding the OpenAI API key
 *   - The curl binary on the agent
 *
 * Usage:
 *   llmAnalyzeFailure(
 *       apiCredential: 'openai-api-key',
 *       slackChannel:  '#ci-failures',
 *       logLines:      100,
 *       model:         'gpt-4o'
 *   )
 */
def call(Map config = [:]) {
    def apiCred      = config.get('apiCredential', 'openai-api-key')
    def slackChannel = config.get('slackChannel', '#ci-failures')
    def logLines     = config.get('logLines', 100)
    def model        = config.get('model', 'gpt-4o')
    def webhookCred  = config.get('webhookCredential', 'slack-webhook-url')

    // pull last N lines of the build log
    def buildLog = currentBuild.rawBuild.getLog(logLines).join('\n')
    // escape for JSON
    def escapedLog = buildLog.replace('\\', '\\\\').replace('"', '\\"').replace('\n', '\\n')

    def prompt = "You are a CI/CD build triage assistant. A Jenkins pipeline just failed. " +
                 "Identify the root cause and give a one-paragraph fix. " +
                 "Be specific about file names and line numbers if visible. " +
                 "Build log tail:\\n${escapedLog}"

    def requestBody = """{
        "model": "${model}",
        "messages": [
            {"role": "system", "content": "You are a helpful CI/CD build triage assistant."},
            {"role": "user",   "content": "${prompt}"}
        ],
        "max_tokens": 400,
        "temperature": 0.2
    }"""

    def analysis = ''

    withCredentials([string(credentialsId: apiCred, variable: 'OPENAI_KEY')]) {
        def response = sh(
            script: """
                curl -s https://api.openai.com/v1/chat/completions \
                    -H "Authorization: Bearer \$OPENAI_KEY" \
                    -H "Content-Type: application/json" \
                    -d '${requestBody}'
            """,
            returnStdout: true
        ).trim()

        // parse the content field out of the JSON response with python
        analysis = sh(
            script: """python3 -c "
import json, sys
data = json.loads('''${response}''')
print(data['choices'][0]['message']['content'])
" """,
            returnStdout: true
        ).trim()
    }

    echo "LLM Triage Analysis:\n${analysis}"
    currentBuild.description = "LLM Triage: ${analysis.take(200)}"

    if (webhookCred) {
        def safeAnalysis = analysis.replace('\\', '\\\\').replace('"', '\\"').replace('\n', '\\n')
        def slackPayload = """{
            "channel": "${slackChannel}",
            "attachments": [{
                "color": "danger",
                "title": "LLM Failure Triage - ${env.JOB_NAME} #${env.BUILD_NUMBER}",
                "text": "${safeAnalysis}",
                "footer": "<${env.BUILD_URL}|View Build>"
            }]
        }"""
        withCredentials([string(credentialsId: webhookCred, variable: 'WEBHOOK_URL')]) {
            sh """
                curl -s -X POST \$WEBHOOK_URL \
                    -H 'Content-Type: application/json' \
                    -d '${slackPayload}'
            """
        }
    }
}
