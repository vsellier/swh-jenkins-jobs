def repo_name = '{display-name}'

def environment

def release
switch (params.DESTINATION) {{
  case ~/^stretch(-.*|)$/:
    release = 9
    break
  case ~/^buster(-.*|)$/:
    release = 10
    break
  case ~/^bullseye(-.*|)$/:
    release = 11
    break
  case ~/^bookworm(-.*|)$/:
    release = 12
    break
  default:
    release = 'UNKNOWN'
    break
}}

def backport_ok = false

pipeline {{
  agent {{ label 'debian' }}
  stages {{
    stage('Checkout') {{
      steps {{
        cleanWs()
        checkout([
          $class: 'GitSCM',
          branches: [[name: "debian/${{params.DESTINATION}}"], [name: "refs/tags/${{params.GIT_TAG}}"]],
          userRemoteConfigs: [[
            url: "https://forge.softwareheritage.org/source/${{repo_name}}.git",
          ]],
        ])
      }}
    }}
    stage('Check the source branch') {{
      steps {{
        script {{
          def current_release = sh(
            script: "dpkg-parsechangelog -SDistribution",
            returnStdout: true,
          ).trim();

          backport_ok = current_release == params.SOURCE && release != 'UNKNOWN'
        }}
      }}
    }}
    stage('Seed the environment') {{
      when {{
        beforeAgent true
        expression {{ backport_ok }}
      }}
      steps {{
        script {{
          def hostname = sh(
            script: "hostname --fqdn",
            returnStdout: true,
          ).trim();

          def short_hostname = hostname - '.internal.softwareheritage.org';

          environment = [
            "DEBEMAIL=jenkins@${{hostname}}",
            "DEBFULLNAME=Software Heritage autobuilder (on ${{short_hostname}})",
          ]
        }}
      }}
    }}
    stage('Merge package version to destination branch') {{
      when {{
        beforeAgent true
        expression {{ backport_ok }}
      }}
      steps {{
        withEnv(environment) {{
          sh """
            git checkout debian/${{params.DESTINATION}}
            git merge ${{params.GIT_TAG}} --no-commit --no-edit || true
            git checkout ${{params.GIT_TAG}} -- debian/changelog
            git add debian/changelog
            git commit --no-verify --no-edit
            git show
          """
          }}
      }}
    }}
    stage('Update changelog for backport') {{
      when {{
        beforeAgent true
        expression {{ backport_ok }}
      }}
      steps {{
        withEnv(environment) {{
          sh """
            dch -l ~bpo${{release}}+ -D ${{params.DESTINATION}} --force-distribution 'Rebuild for ${{params.DESTINATION}}'
            git add debian/changelog
            git commit --no-verify -m "Updated backport on ${{params.DESTINATION}} from ${{params.GIT_TAG}} (${{params.SOURCE}})"
            git show
          """
        }}
      }}
    }}
    stage('Upload changes') {{
      when {{
        beforeAgent true
        expression {{ backport_ok }}
      }}
      steps {{
        sshagent (credentials: ['jenkins-public-ci-ssh']) {{
          script {{
            def git_push = 'git push --follow-tags --all'
            if (params.DRY_RUN) {{
              git_push += ' -n'
            }}

            sh(script: git_push)
          }}
        }}
      }}
    }}
  }}
}}
