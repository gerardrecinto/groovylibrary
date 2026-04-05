#!/usr/bin/env groovy

def call(Map config = [:]) {
    def pythonVersion    = config.get('pythonVersion', '3.11')
    def requirementsFile = config.get('requirementsFile', 'requirements.txt')
    def testDir          = config.get('testDir', 'tests/')
    def coverageMin      = config.get('coverageThreshold', 80)

    sh """
        python${pythonVersion} -m pip install --upgrade pip
        pip install -r ${requirementsFile}
        pip install flake8 pytest pytest-cov
    """

    // lint - fail on syntax errors and undefined names, warn on style
    sh "flake8 . --count --select=E9,F63,F7,F82 --show-source --statistics"
    sh "flake8 . --count --exit-zero --max-complexity=10 --max-line-length=127 --statistics"

    // tests with coverage
    sh """
        pytest ${testDir} \
            --cov=. \
            --cov-report=xml:coverage.xml \
            --cov-report=term-missing \
            --cov-fail-under=${coverageMin} \
            -v
    """

    // publish coverage report if JUnit plugin available
    if (currentBuild.rawBuild.getParent().getPublishersList().find { it.class.simpleName == 'JUnitResultArchiver' } == null) {
        echo "Coverage report saved to coverage.xml"
    }
}
