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
                  userRemoteConfigs: [[url: 'https://forge.softwareheritage.org/source/{display-name}.git']]
                ])
      }}
    }}

    stage('Setup environment') {{
      steps {{
        sh '''#!/bin/bash
        python3 -m venv ~/swh-web-env
        source ~/swh-web-env/bin/activate
        pip3 install wheel
        pip3 install -e .[testing]
        yarn install && yarn build-dev && yarn run cypress install
        '''
      }}
    }}

    stage('Run cypress tests') {{
      steps {{
        sh '''#!/bin/bash
        source ~/swh-web-env/bin/activate
        export PYTHONPATH=$PWD
        python3 swh/web/manage.py migrate
        python3 swh/web/manage.py createcachetable
        python3 swh/web/manage.py runserver --nostatic --settings=swh.web.settings.tests &
        wait-for-it localhost:5004
        yarn run cypress run
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
    }}
  }}

}}