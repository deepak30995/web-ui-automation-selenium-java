// Jenkins equivalent of .github/workflows/nightly-tests.yml.
//
// Both files do the same three things - export environment, call ./run-suite.sh,
// collect what it archived. The pipeline itself lives in the script, so the two
// cannot drift apart and switching CI provider costs one file, not a rewrite.

pipeline {
    agent any

    triggers {
        // 01:30 UTC (07:00 IST), weekdays only - staging is down at weekends.
        //
        // TZ is explicit because Jenkins cron defaults to the controller's local
        // timezone while GitHub cron is always UTC. Without it the same '30 1'
        // string means 01:30 IST here and 07:00 IST there - the two CI
        // definitions would silently run five and a half hours apart.
        cron('''TZ=Etc/UTC
30 1 * * 1-5''')
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

        // Both environments are bound on every build. Which one is used is
        // TARGET_ENV's job, resolved by ConfigReader - binding only the selected
        // one would need conditional Groovy here and would drift from the
        // GitHub workflow, which also exports both.
        //
        // A single-value credential binds straight to the name ConfigReader
        // wants, so it needs no export below. A username/password pair binds as
        // <NAME>_USR and <NAME>_PSW, so those are renamed in the shell step.
        STAGING_BASE_URL        = credentials('staging-base-url')
        PROD_BASE_URL           = credentials('prod-base-url')
        STAGING_INCORRECT_EMAIL = credentials('staging-incorrect-email')
        PROD_INCORRECT_EMAIL    = credentials('prod-incorrect-email')
        STAGING_USER_CREDS      = credentials('staging-user')
        PROD_USER_CREDS         = credentials('prod-user')
        SMTP_CREDS              = credentials('smtp-user')
        EMAIL_TO        = 'deepak.kumar@graphy.com'
        SEND_EMAIL      = 'true'
        SMTP_HOST       = 'smtp.gmail.com'
        SMTP_PORT       = '587'
    }

    stages {
        stage('Run suite') {
            steps {
                sh '''
                    export STAGING_USER_EMAIL="$STAGING_USER_CREDS_USR"
                    export STAGING_USER_PASSWORD="$STAGING_USER_CREDS_PSW"
                    export PROD_USER_EMAIL="$PROD_USER_CREDS_USR"
                    export PROD_USER_PASSWORD="$PROD_USER_CREDS_PSW"
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
