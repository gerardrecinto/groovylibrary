#!/usr/bin/env groovy

def call(Map config = [:]) {
    def manifestPath    = config.get('manifestPath', 'k8s/')
    def namespace       = config.get('namespace', 'default')
    def kubeCredential  = config.get('kubeConfigCredential', 'kubeconfig')
    def timeoutMinutes  = config.get('timeoutMinutes', 5)
    def deploymentName  = config.get('deploymentName', '')

    withCredentials([file(credentialsId: kubeCredential, variable: 'KUBECONFIG')]) {
        sh "kubectl apply -f ${manifestPath} --namespace=${namespace}"

        if (deploymentName) {
            def rolloutCmd = """
                kubectl rollout status deployment/${deploymentName} \
                    --namespace=${namespace} \
                    --timeout=${timeoutMinutes}m
            """
            def status = sh(script: rolloutCmd, returnStatus: true)
            if (status != 0) {
                echo "Rollout failed, triggering rollback for ${deploymentName}"
                sh "kubectl rollout undo deployment/${deploymentName} --namespace=${namespace}"
                error "Deployment ${deploymentName} failed rollout check. Rolled back."
            }
        } else {
            // no deployment name given, just wait a bit and show pod state
            sleep(time: 15, unit: 'SECONDS')
            sh "kubectl get pods --namespace=${namespace}"
        }
    }
}
