#!/usr/bin/env groovy

pipeline {
  agent any
    stages {
      stage("Initialization") {
        steps {
          script {
            def version = sh(returnStdout: true, script: 'grep \'version=\' gradle.properties  | cut -d\'=\' -f2')
            buildName "${env.GIT_BRANCH.replace("origin/", "")}@${version}"
          }
        }
      }
      stage('Build') {
        steps {
          checkout scm
          sh './build.sh clean install publish'
        }
      }
    }
  post {
    cleanup {
      sh 'docker-compose down'
    }
  }
}