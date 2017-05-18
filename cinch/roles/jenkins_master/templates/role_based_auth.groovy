import hudson.*;
import hudson.model.*;
import hudson.security.*;
import jenkins.*;
import jenkins.model.*;
import java.util.*;
import com.michelin.cio.hudson.plugins.rolestrategy.*;
import java.lang.reflect.*;

def instance = Jenkins.getInstance();
def strategy = Hudson.instance.getAuthorizationStrategy();

def boolean createRole(String roleName,
               Set<Permission> permissions,
               RoleBasedAuthorizationStrategy strategy) {
	// First, check if role exists
	RoleMap map = strategy.getRoleMap(RoleBasedAuthorizationStrategy.GLOBAL);
	boolean add = false;
	Role role = map.getRole(roleName);
	if (role == null) {
		role = new Role(roleName, permissions);
		strategy.addRole(RoleBasedAuthorizationStrategy.GLOBAL, role);
		println "CHANGED: Added role '" + roleName + "'";
	} else {
		// Make sure the permissions are in line
		Set<Permission> currentPermissions = role.getPermissions();
		// First, insert any that are missing
		for ( Permission permission : permissions ) {
			if ( !currentPermissions.contains( permission ) ) {
				currentPermissions.add( permission );
				println "CHANGED: Added permission '" + permission.getId() +
                        "' to role '" + role.getName() + "'";
			}
		}
		// Remove extraneous ones
		for ( Permission permission : currentPermissions ) {
			if ( !permissions.contains(permission) ) {
				currentPermissions.remove( permission );
				println "CHANGED: Removed permission '" + permission.getId() +
                        "' from role '" + role.getName() + "'";
			}
		}
	}
}
if( ! (strategy instanceof RoleBasedAuthorizationStrategy) ) {
	println "CHANGED: Created RoleBasedAuthorizationStrategy";
	strategy = new RoleBasedAuthorizationStrategy();
	instance.setAuthorizationStrategy(strategy);
}

// Some Java/Groovy reflection magic to make sure we can access the portions of the
// scripts that are needed for sanity's sake
Constructor[] constructors = Role.class.getConstructors();
for ( Constructor<?> c : constructors ) {
	c.setAccessible(true);
}
Method assignRoleMethod = RoleBasedAuthorizationStrategy.class.getDeclaredMethod("assignRole", String.class, Role.class, String.class);
assignRoleMethod.setAccessible(true);
Method getRoleMapMethod = RoleBasedAuthorizationStrategy.class.getDeclaredMethod("getRoleMap", String.class);
getRoleMapMethod.setAccessible(true);

Set<Permission> rolePermissions;

// Make sure each role is created
{% for role in jenkins_security_roles %}
// Construct the list of permissions for this user
rolePermissions = new HashSet<Permission>();
{% for permission in role.permissions %}
rolePermissions.add(Permission.fromId("{{ permission }}"));
{% endfor %}
createRole("{{ role.name }}", rolePermissions, strategy);
{% endfor %}

instance.save();
