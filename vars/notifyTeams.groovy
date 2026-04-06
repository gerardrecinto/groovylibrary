#!/usr/bin/env groovy

/**
 * notifyTeams
 *
 * Posts a build status card to a Microsoft Teams channel via Incoming Webhook.
 * Uses the Teams Actionable Message Card format (compatible with all Teams versions).
 *
 * Usage:
 *   notifyTeams(
 *       status: 'SUCCESS',            // SUCCESS | FAILURE | UNSTABLE
 *       webhookCredential: 'teams-webhook-url',
 *       message: 'Optional custom message'
 *   )
 *
 * Requires:
 *   - A Jenkins secret-text credential holding the Teams Incoming Webhook URL
 *   - curl on the agent
 */
def call(Map config = [:]) {
    def status      = config.get('status', 'UNKNOWN')
    def webhookCred = config.get('webhookCredential', 'teams-webhook-url')
    def customMsg   = config.get('message', '')

    def colorMap = [
        SUCCESS  : '00b300',
        FAILURE  : 'cc0000',
        UNSTABLE : 'ff9900',
        UNKNOWN  : '808080'
    ]
    def themeColor = colorMap.get(status, '808080')

    def title   = "${status}: ${env.JOB_NAME} #${env.BUILD_NUMBER}"
    def text    = customMsg ?: "${env.JOB_NAME} build #${env.BUILD_NUMBER} ${status.toLowerCase()} on branch ${env.GIT_BRANCH ?: 'unknown'}"
    def buildUrl = env.BUILD_URL ?: ''

    // Teams Adaptive Card payload (Incoming Webhook format)
    def payload = """{
        "@type": "MessageCard",
        "@context": "https://schema.org/extensions",
        "themeColor": "${themeColor}",
        "summary": "${title}",
        "sections": [{
            "activityTitle": "${title}",
            "activityText": "${text}",
            "facts": [
                { "name": "Job",         "value": "${env.JOB_NAME}" },
                { "name": "Build",       "value": "#${env.BUILD_NUMBER}" },
                { "name": "Status",      "value": "${status}" },
                { "name": "Branch",      "value": "${env.GIT_BRANCH ?: 'unknown'}" },
                { "name": "Commit",      "value": "${env.GIT_COMMIT?.take(7) ?: 'unknown'}" }
            ],
            "markdown": true
        }],
        "potentialAction": [{
            "@type": "OpenUri",
            "name": "View Build",
            "targets": [{ "os": "default", "uri": "${buildUrl}" }]
        }]
    }"""

    withCredentials([string(credentialsId: webhookCred, variable: 'TEAMS_WEBHOOK')]) {
        sh """
            curl -s -X POST "\$TEAMS_WEBHOOK" \
                -H 'Content-Type: application/json' \
                -d '${payload}'
        """
    }
}
