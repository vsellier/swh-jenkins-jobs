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
              echo "{
                \\\"buildTargetPHID\\\": \\\"$PHID\\\",
                \\\"artifactKey\\\": \\\"link.jenkins\\\",
                \\\"artifactType\\\": \\\"uri\\\",
                \\\"artifactData\\\": {
                  \\\"uri\\\": \\\"$BUILD_URL\\\",
                  \\\"name\\\": \\\"Jenkins\\\",
                  \\\"ui.external\\\": true
                }
              }" | arc call-conduit --conduit-uri $PHAB_CONDUIT_URL --conduit-token $PHAB_CONDUIT_TOKEN harbormaster.createartifact
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

    stage('Static analysis') {{
      steps {{
        echo 'flake8'
        sh '''python3 -m detox -e flake8'''

        echo 'radon';
        sh  '''
        mkdir -p reports
        python3 -m radon raw --json swh/ > reports/raw_report.json
        python3 -m radon cc  --json swh/ > reports/cc_report.json
        python3 -m radon mi  --json swh/ > reports/mi_report.json
        python3 -m radon hal --json swh/ > reports/hal_report.json
        python3 -m radon cc  --xml  swh/ > reports/cc_report.xml
        '''
      }}
    }} // static analysis

    stage('Unit tests') {{
      options {{
        timeout(time: 20, unit: 'MINUTES')
      }}
      steps {{
        sh '''
        python3 -m tox -e py3 -- \
          --cov-report=xml \
          --junit-xml=test-results.xml
        '''
        }}
      post {{
        always {{
          step([$class: 'CoberturaPublisher',
                autoUpdateHealth: false,
                autoUpdateStability: false,
                coberturaReportFile: 'coverage.xml',
                failNoReports: false,
                failUnhealthy: false,
                failUnstable: false,
                maxNumberOfBuilds: 10,
                onlyStable: false,
                sourceEncoding: 'ASCII',
                zoomCoverageChart: false])
          // JUnit report
          junit allowEmptyResults: true,
                testResults: 'test-results.xml'

          // disabled for now, requires the plugin Warning v5 (still in RC)
          //recordIssues enabledForFailure: true,
          //    tools: [[pattern: '**/reports/cc_report.xml', tool: [$class: 'Ccm']]]
        }}
      }} // post
    }} // unit tests
  }} // stages

  post {{
    always {{
      // Archive a few report files
      archiveArtifacts allowEmptyArchive: true,
                        artifacts: 'reports/*,*.xml,tox*.ini',
                        fingerprint: true
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
            }} // withEnv
      }} // withCredentials
    }} // always
  }} // post
}} // pipeline