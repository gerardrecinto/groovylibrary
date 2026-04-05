#!/usr/bin/env groovy

def call(Map config = [:]) {
    def status      = config.get('status', 'UNKNOWN')
    def channel     = config.get('channel', '#ci-cd')
    def webhookCred = config.get('webhookCredential', 'slack-webhook-url')
    def customMsg   = config.get('message', '')

    def colorMap = [
        SUCCESS  : 'good',
        FAILURE  : 'danger',
        UNSTABLE : 'warning',
        UNKNOWN  : '#808080'
    ]
    def color = colorMap.get(status, '#808080')

    def jobLink = "${env.BUILD_URL}"
    def text = customMsg ?: "${status}: ${env.JOB_NAME} #${env.BUILD_NUMBER}"

    def payload = """
    {
        "channel": "${channel}",
        "attachments": [
            {
                "color": "${color}",
                "text": "${text}",
                "footer": "<${jobLink}|View Build>",
                "ts": ${System.currentTimeMillis() / 1000}
            }
        ]
    }
    """

    withCredentials([string(credentialsId: webhookCred, variable: 'WEBHOOK_URL')]) {
        sh """
            curl -s -X POST \$WEBHOOK_URL \
                -H 'Content-Type: application/json' \
                -d '${payload}'
        """
    }
}
