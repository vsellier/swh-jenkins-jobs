FROM jenkins/jenkins:lts-jdk11

USER root

RUN apt-get update && apt-get -y install python3-pip && \
    pip install tox

# Adapt this group if the docker gid is not 999 on your local environment
ARG DOCKER_GROUP 998

# Install docker tools
RUN addgroup --gid ${DOCKER_GROUP} docker && \
    mkdir -p /etc/apt/keyrings /etc/apt/sources.list.d && \
    apt-get update && apt-get -y install ca-certificates curl gnupg lsb-release && \
    curl -fsSL https://download.docker.com/linux/debian/gpg | gpg --dearmor -o /etc/apt/keyrings/docker.gpg && \
    echo "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.gpg] https://download.docker.com/linux/debian $(lsb_release -cs) stable" > /etc/apt/sources.list.d/docker.list && \
    apt-get update && \
    apt-get install -y docker-ce docker-ce-cli containerd.io docker-compose-plugin

COPY --chown=jenkins:jenkins . /docker/
COPY jenkins_jobs.ini /etc/jenkins_jobs/jenkins_jobs.ini

RUN adduser jenkins docker && \
    chmod a+x /docker/entrypoint.sh

USER jenkins

ENTRYPOINT /docker/entrypoint.sh
