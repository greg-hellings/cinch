import jenkins.security.*
import jenkins.*
import jenkins.model.*
import hudson.*
import hudson.model.*
import hudson.slaves.*;
import com.michelin.cio.hudson.plugins.maskpasswords.*
import org.jenkinsci.plugins.envinject.*

User swarm = User.get("{{ swarm_user }}")
def token = swarm.getProperty(ApiTokenProperty.class).getApiToken()

def jenkins = Jenkins.getActiveInstance()

def global_password_name = "SWARM_PASS"
def global_password = token

def descriptor = jenkins.getDescriptor(EnvInjectNodeProperty.EnvInjectNodePropertyDescriptor.ENVINJECT_CONFIG)
def entries = descriptor.getEnvInjectGlobalPasswordEntries()

java.lang.reflect.Field f = descriptor.getClass().getDeclaredField("envInjectGlobalPasswordEntries");
f.setAccessible(true);
EnvInjectGlobalPasswordEntry[] privateEntries = (EnvInjectGlobalPasswordEntry[]) f.get(descriptor);

ArrayList<EnvInjectGlobalPasswordEntry> newList = new
ArrayList<EnvInjectGlobalPasswordEntry>();
newList.addAll(privateEntries)

//Check if it already exists
def it = newList.iterator()
def exists = false
while (it.hasNext()) {
  def ent = it.next();
  if (ent.getName().equals(global_password_name)) {
    exists = true
    break
  }
}

if (!exists) {
  // We do this so we update an existing name/password pair
  def newEntry = new EnvInjectGlobalPasswordEntry(global_password_name, global_password)
  newList.add(newEntry)

  f.set(descriptor, newList.toArray(new EnvInjectGlobalPasswordEntry[newList.size()]));
  descriptor.save()
}

def globalNodes = jenkins.getGlobalNodeProperties().getAll(hudson.slaves.EnvironmentVariablesNodeProperty.class)
boolean isEmptyNode = (globalNodes.size() == 0)
if (isEmptyNode) {
  jenkins.globalNodeProperties.replaceBy([new EnvironmentVariablesNodeProperty(new EnvironmentVariablesNodeProperty.Entry("SWARM_USER", "{{ swarm_user }}"))])
  isEmptyNode = false
} else {
  jenkins.globalNodeProperties.get(0).getEnvVars().put("SWARM_USER", "{{ swarm_user }}")
}
