pipeline {{
  agent none
  stages {{
    stage('Refresh tag list') {{
      agent any
      steps {{
        checkout([
          $class: 'GitSCM',
          userRemoteConfigs: [[
            url: 'https://forge.softwareheritage.org/source/{display-name}.git',
          ]],
          branches: [[
            name: params.GIT_TAG,
          ]],
          browser: [
            $class: 'Phabricator',
            repo: '{display-name}',
            repoUrl: 'https://forge.softwareheritage.org/',
          ],
        ])
      }}
    }}
    stage('Build and upload PyPI package') {{
      when {{
        expression {{ params.GIT_TAG ==~ /v\d+(.\d+)+/ }}
        expression {{ jobExists('/{name}/pypi-upload') }}
      }}
      steps {{
        build(
          job: '/{name}/pypi-upload',
          parameters: [
            string(name: 'GIT_TAG', value: params.GIT_TAG),
            string(name: 'PYPI_HOST', value: '{incoming-tag-auto-pypi-host}'),
          ],
        )
      }}
    }}
    stage('Update Debian packaging for new release') {{
      when {{
        expression {{ params.GIT_TAG ==~ /v\d+(.\d+)+/ }}
        expression {{ jobExists('/debian/packages/{name}/update-for-release') }}
      }}
      steps {{
        build(
          job: '/debian/packages/{name}/update-for-release',
          parameters: [
            string(name: 'GIT_TAG', value: params.GIT_TAG),
          ],
          wait: false,
        )
      }}
    }}
    stage('Build Debian package') {{
      when {{
        expression {{ params.GIT_TAG ==~ /debian\/.*/ }}
        expression {{ !(params.GIT_TAG ==~ /debian\/upstream\/.*/) }}
        expression {{ jobExists('/debian/packages/{name}/gbp-buildpackage') }}
      }}
      steps {{
        build(
          job: '/debian/packages/{name}/gbp-buildpackage',
          parameters: [
            string(name: 'GIT_REVISION', value: params.GIT_TAG),
            booleanParam(name: 'DO_UPLOAD', value: true),
          ],
          wait: false,
        )
      }}
    }}
  }}
}}
