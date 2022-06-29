#!/bin/bash -x

PLUGINS_FILE=/docker/plugins.txt

if [ -f "$PLUGINS_FILE" ]; then
    echo "Installing plugins from $PLUGINS_FILE ..."
    jenkins-plugin-cli -f $PLUGINS_FILE
fi

/usr/bin/tini -- /usr/local/bin/jenkins.sh
