pipeline {{
  agent {{ label 'swh-tox' }}

  stages {{
    stage('Run tests') {{
      agent none
      steps {{
        build(
          job: '/{name}/tests',
          parameters: [
            string(name: 'REVISION', value: params.GIT_TAG),
          ],
          propagate: !params.IGNORE_TESTS,
        )
      }}
    }}

    stage('Checkout') {{
      steps {{
        checkout([$class: 'GitSCM',
                  branches: [[name: params.GIT_TAG]],
                  doGenerateSubmoduleConfigurations: false,
                  extensions: [],
                  gitTool: 'Default',
                  submoduleCfg: [],
                  userRemoteConfigs: [[url: 'https://forge.softwareheritage.org/source/{display-name}.git']]
        ])
      }}
    }}

    stage('Build') {{
      steps {{
        sh 'python3 setup.py sdist bdist_wheel'
        archiveArtifacts allowEmptyArchive: true,
          artifacts: 'dist/*',
          fingerprint: true
      }}
    }}

    stage('Publish') {{
      when {{
        anyOf {{
          expression {{ return params.FORCE_UPLOAD }}
          expression {{
            LASTV=sh(returnStdout: true,
                     script:'curl -s https://${{PYPI}}/pypi/`python setup.py --name`/json | jq -r .info.version || true').trim()
            return 'v'+LASTV != params.GIT_TAG
            }}
        }}
      }}
      steps {{
        withCredentials([
          usernamePassword(credentialsId: "${{params.PYPI}}",
                           usernameVariable: 'TWINE_USERNAME',
                           passwordVariable: 'TWINE_PASSWORD')]) {{
          sh '''
            if [ -z "$PYPI_UPLOAD" ]; then PYPI_UPLOAD=$PYPI; fi
            python3 -m twine upload --repository-url https://${{PYPI_UPLOAD}}/legacy/ dist/*
          '''
        }}
      }}
    }}
  }}
}}
