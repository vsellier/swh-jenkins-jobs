import jenkins.model.Jenkins

@NonCPS
def job_exists(name) {{
  def instance = jenkins.model.Jenkins.instance
  return instance.getItemByFullName(name) != null
}}

pipeline {{
  agent none
  stages {{
    stage('Build and upload PyPI package') {{
      when {{
        expression {{ params.GIT_TAG =~ /^v\d+(.\d+)+$/ }}
        expression {{ job_exists('/{name}/pypi') }}
      }}
      steps {{
        build(
          job: '/{name}/pypi',
          parameters: [
            string(name: 'GIT_TAG', value: params.GIT_TAG),
          ],
          wait: false,
        )
      }}
    }}
    stage('Debian packaging for new release') {{
      when {{
        expression {{ params.GIT_TAG =~ /^v\d+(.\d+)+$/ }}
        expression {{ job_exists('/debian/packages/{name}/update-for-release') }}
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
    stage('Debian automatic build') {{
      when {{
        expression {{ params.GIT_TAG =~ /^debian\/.*$/ }}
        expression {{ job_exists('/debian/packages/{name}/build') }}
      }}
      steps {{
                build(
          job: '/debian/packages/{name}/build',
          parameters: [
            string(name: 'GIT_TAG', value: params.GIT_TAG),
            booleanParam(name: 'UPLOAD', value: true),
          ],
          wait: false,
        )
      }}
    }}
  }}
}}
