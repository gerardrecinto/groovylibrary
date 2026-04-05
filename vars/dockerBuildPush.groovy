#!/usr/bin/env groovy

def call(Map config = [:]) {
    def imageName    = config.get('imageName')
    def registry     = config.get('registry')
    def credId       = config.get('credentialsId', 'docker-registry-creds')
    def dockerfile   = config.get('dockerfile', 'Dockerfile')
    def gitSha       = sh(script: 'git rev-parse --short HEAD', returnStdout: true).trim()
    def shaTag       = "${registry}/${imageName}:${gitSha}"
    def latestTag    = "${registry}/${imageName}:latest"

    if (!imageName || !registry) {
        error "dockerBuildPush: imageName and registry are required"
    }

    docker.withRegistry("https://${registry}", credId) {
        def img = docker.build(shaTag, "-f ${dockerfile} .")
        img.push()
        img.push('latest')
        echo "Pushed ${shaTag} and ${latestTag}"
    }
}
