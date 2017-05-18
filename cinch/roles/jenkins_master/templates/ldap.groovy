import jenkins.model.*
import hudson.security.*
import org.jenkinsci.plugins.*

def instance = Jenkins.getInstance();
def securityRealm = instance.getSecurityRealm();
LDAPSecurityRealm ldap;
boolean changed = false;
// Arguments
String server = "{{ jenkins_ldap.server }}";
String rootDN = "{{ jenkins_ldap.root_dn }}";
String userSearchBase = "{{ jenkins_ldap.user_search_base }}";
String userSearch = "{{ jenkins_ldap.user_search }}";
String groupSearchBase = "{{ jenkins_ldap.group_search_base }}";
String managerDN = "{{ jenkins_ldap.manager_dn }}";
String managerPassword = "{{ jenkins_ldap.manager_password }}";
// Check that LDAP is even configured
try {
	ldap = (LDAPSecurityRealm) securityRealm;
} catch(ClassCastException cce) {
	ldap = null;
}
// Check that LDAP settings are correct
if ( ldap == null ||
     !ldap.server.equals(server) ||
     !ldap.rootDN.equals(rootDN) ||
     !ldap.userSearchBase.equals(userSearchBase) ||
     !ldap.userSearch.equals(userSearch) ||
     !ldap.groupSearchBase.equals(gruopSearchBase) ||
     !ldap.managerDN.equals(managerDN) ||
     !ldap.managerPassword.equals(managerPassword)
   ) {
	ldap = new LDAPSecurityRealm(server,
	                             rootDN,
	                             userSearchBase,
	                             userSearch,
	                             groupSearchBase,
	                             managerDN,
	                             managerPassword,
	                             false);
	println "CHANGED: Updated security realm to LDAP"
	instance.setSecurityRealm(ldap);
	instance.save();
} else {
	println "No changes to LDAP necessary"
}
