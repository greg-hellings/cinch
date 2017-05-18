import jenkins.model.Jenkins
import jenkins.model.DownloadSettings

def jenkins = Jenkins.instance;

jenkins.setNumExecutors({{ jenkins_executors }});

def ds = jenkins.getExtensionList(DownloadSettings.class)[0];
ds.setUseBrowser(false)
ds.save()

jenkins.setSlaveAgentPort({{ jenkins_slave_agent_port }})
jenkins.save()
