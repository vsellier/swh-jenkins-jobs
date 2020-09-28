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
