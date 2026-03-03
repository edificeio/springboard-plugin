#!/usr/bin/env groovy

pipeline {
  agent any

  environment {
    NEXUS_SONATYPE_PASSWORD = credentials('nexus-sonatype-password')
    NEXUS_ODE_PASSWORD = credentials('nexus-ode-password')
  }

  stages {
    stage("Initialization") {
        when {
          environment name: 'RENAME_BUILDS', value: 'true'
        }
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