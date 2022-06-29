# Jenkins jobs configuration

This repository manages the jenkins jobs configuration.

A [jenkins jobs] applies the configuration on jenkins when a commit is done in this repository

[jenkins jobs]: https://jenkins.softwareheritage.org/job/jenkins-tools/job/swh-jenkins-job-builder


# Testing

To test locally the configuration , simply run ``tox``:

```
tox
```

The output displays the jenkins configuration files as they will be applied on the server.

# Run on docker

TODO improve this part

- launch jenkins
```
docker-compose build
docker-compose up
```
- get the admin password in the logs
- connect to localhost:8080
- change the admin password to `admin123`
- create a jenkins directory `jenkins-tools`
- create a new free-style job named `job-builder` inside the `jenkins-tools` pointing this git repository
  - configure the right branch (ci here)
  - Add a step `Execute a script shell` with this content
```
tox update -- --delete-old
```
- save and launch \o/
