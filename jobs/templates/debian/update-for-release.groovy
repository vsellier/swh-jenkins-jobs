def upstream_tag = params.GIT_TAG
def version = upstream_tag.substring(1)
def repo_name = '{display-name}'
def python_module = repo_name.replace('-', '.')

def full_environ

def skip_steps = false

pipeline {{
  agent {{ label 'debian' }}
  stages {{
    stage('Checkout') {{
      steps {{
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
            git fetch --tags
            git checkout -B pristine-tar origin/pristine-tar
            git checkout -B debian/upstream origin/debian/upstream
            git checkout -B debian/unstable-swh origin/debian/unstable-swh
          '''
          script {{
            def tag_exists_retval = sh(
              script: "git rev-parse debian/upstream/${{version}}",
              returnStatus: true
            )
            skip_steps = (tag_exists_retval == 0)
          }}
        }}
      }}
    }}
    stage('Build sdist from tag') {{
      when {{
        expression {{ !skip_steps }}
      }}
      agent {{ label 'swh-tox' }}
      steps {{
        checkout([
          $class: 'GitSCM',
          branches: [[name: $upstream_tag]],
          userRemoteConfigs: [[
            url: "https://forge.softwareheritage.org/source/${{repo_name}}.git",
          ]],
          extensions: [[
            $class: 'RelativeTargetDirectory',
            relativeTargetDir: 'tag-export',
          ]],
          poll: true,
        ])

        dir ('tag-export') {{
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

            full_environ = [
              "DEBEMAIL=${{tagger_email}}",
              "DEBFULLNAME=${{tagger_name}}",
              "GIT_AUTHOR_NAME=${{tagger_name}}",
              "GIT_AUTHOR_EMAIL=${{tagger_email}}",
              "GIT_AUTHOR_DATE=${{tagger_date}}",
              "GIT_COMMITTER_NAME=Jenkins for Software Heritage",
              "GIT_COMMITTER_EMAIL=jenkins@softwareheritage.org",
              "GIT_COMMITTER_DATE=${{tagger_date}}",
              "UPSTREAM_TAG=${{upstream_tag}}",
              "UPSTREAM_VERSION=${{version}}",
              "PYTHON_MODULE=${{python_module}}",
            ];
          }}
          sh 'python3 setup.py sdist'
          dir ('dist') {{
            stash(
              name: 'sdist',
              includes: "${{python_module}}-${{version}}.tar.gz",
            )
          }}
        }}
      }}
    }}
    stage('gbp import-orig') {{
      when {{
        expression {{ !skip_steps }}
      }}
      steps {{
        unstash(name: 'sdist')
        withEnv(full_environ) {{
          dir (repo_name) {{
            sh '''
              gbp import-orig --merge-mode=replace --no-symlink-orig -u ${{UPSTREAM_VERSION}} ../${{PYTHON_MODULE}}-${{UPSTREAM_VERSION}}.tar.gz
            '''
          }}
        }}
      }}
    }}
    stage('unstable-swh changelog') {{
      when {{
        expression {{ !skip_steps }}
      }}
      steps {{
        withEnv(full_environ) {{
          dir(repo_name) {{
            sh '''
              dch -v ${{version}}-1~swh1 ''
              git tag -l --format='%(contents:subject)%0a%(contents:body)' ${{UPSTREAM_TAG}} | sed -E -e '/^$/d' -e 's/^ *(- *)?//' | while read line; do dch "$line"; done
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
        expression {{ !skip_steps }}
        expression {{ !params.DRY_RUN }}
      }}
      steps {{
        echo 'Uploading'
      }}
    }}
  }}
}}
