buildMvn {
  publishModDescriptor = true
  mvnDeploy = true
  doKubeDeploy = true
  buildNode = 'jenkins-agent-java17'

  doDocker = {
    buildJavaDocker {
      publishMaster = true
      // healthChk in ModFqmManagerIT.java
    }
  }
}
