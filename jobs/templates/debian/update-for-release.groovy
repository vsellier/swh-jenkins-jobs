def upstream_tag = params.GIT_TAG
def version = upstream_tag.substring(1)
def repo_name = '{display-name}'
def python_module = repo_name.replace('-', '.')

def full_environ

def debian_upstream_tag_exists = false

pipeline {{
  agent {{ label 'debian' }}
  stages {{
    stage('Checkout') {{
      steps {{
        cleanWs()
        checkout([
          $class: 'GitSCM',
          branches: [[name: 'debian/unstable-swh'], [name: 'debian/*'], [name: 'pristine-tar']],
          userRemoteConfigs: [[
            url: "https://forge.softwareheritage.org/source/${{repo_name}}.git",
          ]],
          extensions: [
            [$class: 'RelativeTargetDirectory', relativeTargetDir: repo_name],
          ],
          poll: false,
        ])
        dir (repo_name) {{
          sh '''
            git checkout -B pristine-tar origin/pristine-tar
            git checkout -B debian/upstream origin/debian/upstream
            git checkout -B debian/unstable-swh origin/debian/unstable-swh
          '''
          script {{
            def tag_exists_retval = sh(
              script: "git rev-parse --verify debian/upstream/${{version}}",
              returnStatus: true
            )
            debian_upstream_tag_exists = (tag_exists_retval == 0)
          }}
        }}
      }}
    }}
    stage('Get author information from tag') {{
      when {{
        beforeAgent true
        expression {{ !debian_upstream_tag_exists }}
      }}
      steps {{
        dir (repo_name) {{
          // This script retrieves the author data for the tag that we're currently processing
          script {{
            // Check if the tag is annotated or not
            def objecttype = sh(
              script: "git tag -l --format='%(*objecttype)' ${{upstream_tag}}",
              returnStdout: true,
            ).trim();
            def tagger = !objecttype.isEmpty() ? 'tagger' : 'author';

            def tagger_name = sh(
              script: "git tag -l --format='%(${{tagger}}name)' ${{upstream_tag}}",
              returnStdout: true,
            ).trim();

            def tagger_email = sh(
              script: "git tag -l --format='%(${{tagger}}email)' ${{upstream_tag}}",
              returnStdout: true,
            ).trim();
            tagger_email = tagger_email.substring(1, tagger_email.length() - 1);

            def tagger_date = sh(
              script: "git tag -l --format='%(${{tagger}}date)' ${{upstream_tag}}",
              returnStdout: true,
            ).trim();

            def hostname = sh(
              script: "hostname --fqdn",
              returnStdout: true,
            ).trim();

            def short_hostname = hostname - '.internal.softwareheritage.org';

            full_environ = [
              "DEBEMAIL=jenkins@${{hostname}}",
              "DEBFULLNAME=Software Heritage autobuilder (on ${{short_hostname}})",
              "UPSTREAM_TAG=${{upstream_tag}}",
              "UPSTREAM_TAGGER_NAME=${{tagger_name}}",
              "UPSTREAM_TAGGER_EMAIL=${{tagger_email}}",
              "UPSTREAM_TAGGER_DATE=${{tagger_date}}",
              "UPSTREAM_VERSION=${{version}}",
              "PYTHON_MODULE=${{python_module}}",
            ];
          }}
        }}
      }}
    }}
    stage('gbp import-orig') {{
      when {{
        beforeAgent true
        expression {{ !debian_upstream_tag_exists }}
      }}
      steps {{
        copyArtifacts(
          projectName: '/{name}/pypi-upload',
          parameters: 'GIT_TAG=' + params.GIT_TAG,
        )
        withEnv(full_environ) {{
          dir (repo_name) {{
            sh '''
              gbp import-orig --merge-mode=replace --no-symlink-orig -u ${{UPSTREAM_VERSION}} ../dist/${{PYTHON_MODULE}}-${{UPSTREAM_VERSION}}.tar.gz
            '''
          }}
        }}
      }}
    }}
    stage('unstable-swh changelog') {{
      when {{
        beforeAgent true
        expression {{ !debian_upstream_tag_exists }}
      }}
      steps {{
        withEnv(full_environ) {{
          dir(repo_name) {{
            sh '''
              dch -v ${{UPSTREAM_VERSION}}-1~swh1 "New upstream release ${{UPSTREAM_VERSION}}\n    - (tagged by ${{UPSTREAM_TAGGER_NAME}} <${{UPSTREAM_TAGGER_EMAIL}}> on ${{UPSTREAM_TAGGER_DATE}})"
              dch "Upstream changes:\n$(git tag -l --format='%(contents:subject)%0a%(contents:body)' ${{UPSTREAM_TAG}} | sed -E -e '/^$/d' -e 's/^ *(- *)?//' -e 's/^/    - /')"
              dch -D unstable-swh --force-distribution ''
              git add debian/changelog
              git commit --no-verify -m "Updated debian changelog for version ${{UPSTREAM_VERSION}}"
              git show
            '''
          }}
        }}
      }}
    }}
    stage('Upload changes') {{
      when {{
        beforeAgent true
        expression {{ !debian_upstream_tag_exists }}
      }}
      steps {{
        dir (repo_name) {{
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
}}
