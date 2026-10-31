// Jenkins equivalent of .github/workflows/nightly-tests.yml.
//
// Both files do the same three things - export environment, call ./run-suite.sh,
// collect what it archived. The pipeline itself lives in the script, so the two
// cannot drift apart and switching CI provider costs one file, not a rewrite.

pipeline {
    agent any

    triggers {
        // 01:30, weekdays only - staging is down at weekends
        cron('30 1 * * 1-5')
    }

    parameters {
        choice(name: 'SUITE_PROFILE', choices: ['regression', 'smoke', 'sanity', 'e2e'],
               description: 'Suite profile')
        choice(name: 'TARGET_ENV',    choices: ['staging', 'prod'],
               description: 'Site under test')
        choice(name: 'BROWSER',       choices: ['chrome', 'firefox', 'edge'],
               description: 'Browser')
        booleanParam(name: 'RECORD_VIDEO', defaultValue: true,  description: 'Record video')
        booleanParam(name: 'AUTO_FIX',     defaultValue: false, description: 'Agent may attempt a fix')
    }

    options {
        timeout(time: 60, unit: 'MINUTES')
        // Jenkins agents persist, so run-suite.sh's own KEEP_RUNS pruning is
        // what actually bounds disk here - unlike on an ephemeral CI runner.
        buildDiscarder(logRotator(numToKeepStr: '20'))
    }

    environment {
        TEST_ENV        = 'docker'
        HEADLESS        = 'false'
        RUN_ID          = "${env.BUILD_NUMBER}"
        KEEP_RUNS       = '5'
        DOCKER_PLATFORM = 'linux/amd64'

        BASE_URL_CREDS  = credentials('staging-base-url')
        USER_CREDS      = credentials('staging-user')      // username = email, password = password
        SMTP_CREDS      = credentials('smtp-user')
        EMAIL_TO        = 'deepak.kumar@graphy.com'
        SEND_EMAIL      = 'true'
        SMTP_HOST       = 'smtp.gmail.com'
        SMTP_PORT       = '587'
    }

    stages {
        stage('Run suite') {
            steps {
                sh '''
                    export STAGING_BASE_URL="$BASE_URL_CREDS"
                    export STAGING_USER_EMAIL="$USER_CREDS_USR"
                    export STAGING_USER_PASSWORD="$USER_CREDS_PSW"
                    export SMTP_USER="$SMTP_CREDS_USR"
                    export SMTP_PASSWORD="$SMTP_CREDS_PSW"
                    export EMAIL_FROM="$SMTP_CREDS_USR"
                    ./run-suite.sh
                '''
            }
        }
    }

    post {
        always {
            archiveArtifacts artifacts: 'test-output/runs/**',
                             allowEmptyArchive: true, fingerprint: false
            junit testResults: 'target/surefire-reports/*.xml', allowEmptyResults: true
        }
    }
}
