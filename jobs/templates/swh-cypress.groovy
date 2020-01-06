pipeline {{

  agent {{ label '{docker-image}' }}

  environment {{
    PHAB_CONDUIT_URL = 'https://forge.softwareheritage.org/api/'
  }}

  stages {{

    stage('Checkout') {{
      steps {{
        withCredentials([
          string(credentialsId: 'swh-public-ci',
                  variable: 'PHAB_CONDUIT_TOKEN')]) {{
            sh '''
            if [ -n "$PHID" ]; then
              echo "{{
                \\\"buildTargetPHID\\\": \\\"$PHID\\\",
                \\\"artifactKey\\\": \\\"link.jenkins\\\",
                \\\"artifactType\\\": \\\"uri\\\",
                \\\"artifactData\\\": {{
                  \\\"uri\\\": \\\"$BUILD_URL\\\",
                  \\\"name\\\": \\\"Jenkins\\\",
                  \\\"ui.external\\\": true
                }}
              }}" | arc call-conduit --conduit-uri $PHAB_CONDUIT_URL --conduit-token $PHAB_CONDUIT_TOKEN harbormaster.createartifact
              python3 -m pyarcanist send-message work $PHID
            fi
            '''
        }}
        checkout([$class: 'GitSCM',
                  branches: [[name: "${{params.REVISION}}"]],
                  doGenerateSubmoduleConfigurations: false,
                  extensions: [],
                  gitTool: 'Default',
                  submoduleCfg: [],
                  userRemoteConfigs: [[url: 'https://forge.softwareheritage.org/source/{display-name}.git']],
                  browser: [$class: 'Phabricator', repoUrl: 'https://forge.softwareheritage.org', repo: '{name}']
                ])
      }}
    }}

    stage('Setup environment') {{
      steps {{
        sh '''#!/bin/bash
        python3 -m pip install --user -e .[testing]
        yarn install && yarn build-test && yarn run cypress install
        '''
      }}
    }}

    stage('Run cypress tests') {{
      steps {{
        sh '''#!/bin/bash
        set -e
        export PYTHONPATH=$PWD
        python3 swh/web/manage.py migrate --settings=swh.web.settings.tests
        python3 swh/web/manage.py createcachetable --settings=swh.web.settings.tests
        cat swh/web/tests/create_test_admin.py | python3 swh/web/manage.py shell --settings=swh.web.settings.tests
        python3 swh/web/manage.py runserver --nostatic --settings=swh.web.settings.tests &
        wait-for-it localhost:5004
        yarn run cypress run
        yarn run mochawesome
        yarn run nyc-report
        '''
      }}
    }}

  }}

  post {{
    always {{
      withCredentials([
        string(credentialsId: 'swh-public-ci',
              variable: 'PHAB_CONDUIT_TOKEN')]) {{
        withEnv(["JOBSTATUS=${{currentBuild.currentResult}}"]) {{
          sh '''
          if [ "$JOBSTATUS" = "SUCCESS" ]; then
            MSGTYPE=pass
          else
            MSGTYPE=fail
          fi
          echo "Current job status is $JOBSTATUS -> $MGSTYPE"
          if [ -n "$PHID" ]; then
            python3 -m pyarcanist send-message $MSGTYPE $PHID
          fi
          '''
        }}
      }}
      publishHTML (target: [
        allowMissing: true,
        alwaysLinkToLastBuild: false,
        keepAll: true,
        reportDir: 'cypress/mochawesome/report',
        reportFiles: 'mochawesome.html',
        reportName: "Mochawesome Tests Report"
      ])
      publishHTML (target: [
        allowMissing: true,
        alwaysLinkToLastBuild: false,
        keepAll: true,
        reportDir: 'cypress/coverage/lcov-report',
        reportFiles: 'index.html',
        reportName: "Istanbul Code Coverage"
      ])
    }}
  }}

}}
