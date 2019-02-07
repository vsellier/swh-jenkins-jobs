def repo_name = '{display-name}'

def changelog_distribution

def repo_host = 'pergamon.internal.softwareheritage.org'
def repo_user = 'swhdebianrepo'
def repo_path = '/srv/softwareheritage/repository'

def upload_target = "${{repo_path}}/incoming"
def repo_command = "umask 002; reprepro -vb ${{repo_path}} processincoming incoming"

pipeline {{
  agent {{ label 'debian' }}
  environment {{
    PHAB_CONDUIT_URL = 'https://forge.softwareheritage.org/api/'
  }}
  stages {{
    stage('Notify Phabricator start') {{
      when {{
        beforeAgent true
        expression {{ params.PHID }}
      }}
      agent {{ label 'swh-tox' }}
      steps {{
        withCredentials([
          string(credentialsId: 'swh-public-ci',
                 variable: 'PHAB_CONDUIT_TOKEN')]) {{
          sh '''
            python3 -m pyarcanist send-message work $PHID
          '''
        }}
      }}
    }}
    stage('Checkout') {{
      steps {{
        cleanWs()
        checkout([
          $class: 'GitSCM',
          branches: [[name: params.GIT_REVISION]],
          userRemoteConfigs: [[
            url: "https://forge.softwareheritage.org/source/${{repo_name}}.git",
          ]],
          extensions: [
            [$class: 'RelativeTargetDirectory', relativeTargetDir: repo_name],
          ],
        ])
        script {{
          dir(repo_name) {{
            if(!fileExists('debian/changelog')) {{
              error('Trying to build a debian package without a debian/changelog')
            }}

            changelog_distribution = sh(
              script: 'dpkg-parsechangelog -SDistribution',
              returnStdout: true,
            ).trim()

            def parsed_gbp_config = readProperties(
              file: 'debian/gbp.conf',
              defaults: ['debian-branch': 'master'],
            )

            def debian_branch = parsed_gbp_config['debian-branch']

            sh """
              git checkout -b ${{debian_branch}}
              git branch -f pristine-tar origin/pristine-tar
              git branch -f debian/upstream origin/debian/upstream
            """
          }}
        }}
      }}
    }}
    stage('Write extra keys') {{
      when {{
        beforeAgent true
        expression {{ changelog_distribution != 'UNRELEASED' }}
      }}
      steps {{
        dir('keys') {{
          writeFile(
            file: 'ceph.asc',
            text: '''-----BEGIN PGP PUBLIC KEY BLOCK-----
Version: GnuPG v1

mQINBFX4hgkBEADLqn6O+UFp+ZuwccNldwvh5PzEwKUPlXKPLjQfXlQRig1flpCH
E0HJ5wgGlCtYd3Ol9f9+qU24kDNzfbs5bud58BeE7zFaZ4s0JMOMuVm7p8JhsvkU
C/Lo/7NFh25e4kgJpjvnwua7c2YrA44ggRb1QT19ueOZLK5wCQ1mR+0GdrcHRCLr
7Sdw1d7aLxMT+5nvqfzsmbDullsWOD6RnMdcqhOxZZvpay8OeuK+yb8FVQ4sOIzB
FiNi5cNOFFHg+8dZQoDrK3BpwNxYdGHsYIwU9u6DWWqXybBnB9jd2pve9PlzQUbO
eHEa4Z+jPqxY829f4ldaql7ig8e6BaInTfs2wPnHJ+606g2UH86QUmrVAjVzlLCm
nqoGymoAPGA4ObHu9X3kO8viMBId9FzooVqR8a9En7ZE0Dm9O7puzXR7A1f5sHoz
JdYHnr32I+B8iOixhDUtxIY4GA8biGATNaPd8XR2Ca1hPuZRVuIiGG9HDqUEtXhV
fY5qjTjaThIVKtYgEkWMT+Wet3DPPiWT3ftNOE907e6EWEBCHgsEuuZnAbku1GgD
LBH4/a/yo9bNvGZKRaTUM/1TXhM5XgVKjd07B4cChgKypAVHvef3HKfCG2U/DkyA
LjteHt/V807MtSlQyYaXUTGtDCrQPSlMK5TjmqUnDwy6Qdq8dtWN3DtBWQARAQAB
tCpDZXBoLmNvbSAocmVsZWFzZSBrZXkpIDxzZWN1cml0eUBjZXBoLmNvbT6JAjgE
EwECACIFAlX4hgkCGwMGCwkIBwMCBhUIAgkKCwQWAgMBAh4BAheAAAoJEOhKwsBG
DzmUXdIQAI8YPcZMBWdv489q8CzxlfRIRZ3Gv/G/8CH+EOExcmkVZ89mVHngCdAP
DOYCl8twWXC1lwJuLDBtkUOHXNuR5+Jcl5zFOUyldq1Hv8u03vjnGT7lLJkJoqpG
l9QD8nBqRvBU7EM+CU7kP8+09b+088pULil+8x46PwgXkvOQwfVKSOr740Q4J4nm
/nUOyTNtToYntmt2fAVWDTIuyPpAqA6jcqSOC7Xoz9cYxkVWnYMLBUySXmSS0uxl
3p+wK0lMG0my/gb+alke5PAQjcE5dtXYzCn+8Lj0uSfCk8Gy0ZOK2oiUjaCGYN6D
u72qDRFBnR3jaoFqi03bGBIMnglGuAPyBZiI7LJgzuT9xumjKTJW3kN4YJxMNYu1
FzmIyFZpyvZ7930vB2UpCOiIaRdZiX4Z6ZN2frD3a/vBxBNqiNh/BO+Dex+PDfI4
TqwF8zlcjt4XZ2teQ8nNMR/D8oiYTUW8hwR4laEmDy7ASxe0p5aijmUApWq5UTsF
+s/QbwugccU0iR5orksM5u9MZH4J/mFGKzOltfGXNLYI6D5Mtwrnyi0BsF5eY0u6
vkdivtdqrq2DXY+ftuqLOQ7b+t1RctbcMHGPptlxFuN9ufP5TiTWSpfqDwmHCLsT
k2vFiMwcHdLpQ1IH8ORVRgPPsiBnBOJ/kIiXG2SxPUTjjEGOVgeA
=/Tod
-----END PGP PUBLIC KEY BLOCK-----
'''
          )
        }}
      }}
    }}
    stage('Build package') {{
      when {{
        beforeAgent true
        expression {{ changelog_distribution != 'UNRELEASED' }}
      }}
      steps {{
        script {{
          dir (repo_name) {{
            def wanted_extra_repositories = params.EXTRA_REPOSITORIES.split(',')

            def build_dep_resolver

            def extra_repositories = []
            def extra_repository_keys = []

            def base_distribution = changelog_distribution.split('-')[0]
            def backports = changelog_distribution.endsWith('-backports')
            def swh = changelog_distribution.endsWith('-swh')

            if (base_distribution in ['unstable', 'experimental'] || 'incoming' in wanted_extra_repositories) {{
              def suites = []
              if (base_distribution == 'unstable') {{
                suites = ['buildd-unstable']
              }} else if (base_distribution == 'experimental') {{
                suites = ['buildd-unstable', 'buildd-experimental']
              }} else {{
                suites = ["buildd-${{base_distribution}}-proposed-updates"]
                if (backports || swh) {{
                  suites.add("buildd-${{base_distribution}}-backports")
                }}
              }}
              suites.each {{suite ->
                extra_repositories.add("deb http://incoming.debian.org/debian-buildd/ ${{suite}} main")
              }}
            }}

            if (swh || 'swh' in wanted_extra_repositories) {{
              def swh_distribution = "${{base_distribution}}-swh"
              if (base_distribution in ['unstable', 'experimental']) {{
                swh_distribution = 'unstable'
              }}
              extra_repositories.add("deb [trusted=yes] https://debian.softwareheritage.org/ ${{swh_distribution}} main")
            }}

            if ((backports || swh || 'backports' in wanted_extra_repositories) && !(base_distribution in ['unstable', 'experimental'])) {{
              extra_repositories.add("deb http://deb.debian.org/debian/ ${{base_distribution}}-backports main")
              build_dep_resolver = 'aptitude'
            }}

            if ('ceph' in wanted_extra_repositories && !(base_distribution in ['unstable', 'experimental'])) {{
              extra_repositories.add("deb https://download.ceph.com/debian-luminous/ ${{base_distribution}} main")
              extra_repository_keys.add('../keys/ceph.asc')
            }}

            if (params.BUILD_DEP_RESOLVER) {{
              build_dep_resolver = params.BUILD_DEP_RESOLVER
            }}

            def hostname = sh(
              script: "hostname --fqdn",
              returnStdout: true,
            ).trim();

            def short_hostname = hostname - '.internal.softwareheritage.org';

            def uploader = "Software Heritage autobuilder (on ${{short_hostname}}) <jenkins@${{hostname}}>"

            def gbp_buildpackage = [
              'gbp buildpackage',
              '--git-builder=sbuild',
              '--nolog',
              '--batch',
              '--no-clean-source',
              '--no-run-lintian',
              '--arch-all',
              '--source',
              '--force-orig-source',
              "--uploader='${{uploader}}'",
            ]

            if (build_dep_resolver != null) {{
              gbp_buildpackage.add("--build-dep-resolver=${{build_dep_resolver}}")
            }}

            extra_repositories.each {{ repo ->
              gbp_buildpackage.add("--extra-repository='${{repo}}'")
            }}

            extra_repository_keys.each {{ key ->
              gbp_buildpackage.add("--extra-repository-key='${{key}}'")
            }}

            def gbp_buildpackage_cmd = gbp_buildpackage.join(' ')

            sh(script: gbp_buildpackage_cmd)
          }}

          if (params.DO_UPLOAD) {{
            sh(script: 'debsign *.changes')
          }}

          archiveArtifacts(
            artifacts: sh(
              script: 'dcmd echo *.changes',
              returnStdout: true
            ).split().join(','),
            fingerprint: true,
          )
        }}
      }}
    }}
    stage('Upload package') {{
      when {{
        beforeAgent true
        expression {{ changelog_distribution != 'UNRELEASED' }}
        expression {{ params.DO_UPLOAD }}
      }}
      steps {{
        sshagent (credentials: ['jenkins-debian-repo-ssh']) {{
          sh """
            dcmd rsync -v *.changes ${{repo_user}}@${{repo_host}}:${{upload_target}}
            ssh ${{repo_user}}@${{repo_host}} '${{repo_command}}'
          """
        }}
      }}
    }}
  }}
  post {{
    always {{
      node('swh-tox') {{
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
}}
