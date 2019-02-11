def repo_name = '{display-name}'

def changelog_distribution

def repo_host = 'pergamon.internal.softwareheritage.org'
def repo_user = 'swhdebianrepo'
def repo_path = '/srv/softwareheritage/repository'

def upload_target = "${{repo_path}}/incoming"
def repo_command = "umask 002; reprepro -vb ${{repo_path}} processincoming incoming"

def backport_job = '/' + (currentBuild.fullProjectName.split('/')[0..-2] + ['automatic-backport']).join('/')

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
          // wget https://www.postgresql.org/media/keys/ACCC4CF8.asc
          writeFile(
            file: 'postgres.asc',
            text: '''-----BEGIN PGP PUBLIC KEY BLOCK-----

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
=DA1T
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

            if ('pgdg' in wanted_extra_repositories && !(base_distribution in ['unstable', 'experimental'])) {{
              extra_repositories.add("deb http://apt.postgresql.org/pub/repos/apt/ ${{base_distribution}}-pgdg main")
              extra_repository_keys.add('../keys/postgres.asc')
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
    stage('Prepare backport') {{
      when {{
        beforeAgent true
        expression {{ changelog_distribution != 'UNRELEASED' }}
        expression {{ params.BACKPORT_ON_SUCCESS }}
        expression {{ jobExists(backport_job) }}
      }}
      steps {{
        script {{
          params.BACKPORT_ON_SUCCESS.split(',').each {{ bpo_pair ->
            def (src_suite, dst_suite) = bpo_pair.split('>')

            if (src_suite == changelog_distribution) {{
              build(
                job: backport_job,
                parameters: [
                  string(name: 'GIT_TAG', value: params.GIT_REVISION),
                  string(name: 'SOURCE', value: src_suite),
                  string(name: 'DESTINATION', value: dst_suite),
                ],
                wait: false,
              )
            }}
          }}
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
