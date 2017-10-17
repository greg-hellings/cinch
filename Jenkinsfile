#!groovy
// Things annotated with this are accessible from within methods as well as in
// the global script
import groovy.transform.Field;
/**
* Spin up resources
* 1. Fedora machine for tox and shell check and docker images
* 2. 2x RHEL 7.4 machines for master/slave without security
* 3. 2x RHEL 7.4 machines for master/slave without security + SSL
* 4. 2x RHEL 7.4 machines for master/slave with security
* 5. 2x RHEL 7.4 machines for master/slave with security + SSL
* 6. 1x RHEL 7.4 machine for master, 1x RHEL 6 machine for slave with security + SSL
* N. 2x Fedora machines for master/slave with security + SSL
*
* Build RHEL-based Docker containers for internal use
* Build out systems from provisioning step
*
* Teardown resources
*/
// Load helper scripts
@Library('cinch@tox')
import com.redhat.qe.cinch.Virtualenv;
Virtualenv.script = this;

// Trying to avoid "magic strings"
def cinchTargets = ["rhel7_nosec_nossl",
                    "rhel7_nosec_ssl",
                    "rhel7_sec_nossl",
                    "rhel7_sec_ssl",
                    "rhel7_sec_ssl_rhel6_slave"];
def toxTargets = ["lint",
                  "docs",
                  "py27"];
def images = ["cent6_slave",
              "cent7_slave",
              "fedora_slave",
              "cent7_master"];
@Field def successfulProvisions = [];

@Field def linchpinPackages = ["https://github.com/CentOS-PaaS-SiG/linchpin/archive/develop.tar.gz"];
@Field def linchpinPath = "linchpin-venv";
@Field def cinchPackages = ["https://github.com/greg-hellings/cinch/archive/tox.tar.gz"];
@Field def cinchPath = "cinch-venv";

@Field def topologyCheckoutDir = "topology-dir";
@Field def topologyWorkspaceDir = "${topologyCheckoutDir}/test";
@Field def topologyBranch = "master";

// Generate parallel build stages for Tier 1
def createBuild(String target) {
	return {
		node("cinch-test-builder") {
			dir("cinch") {
				checkout scm;
				sh "tox -e \"${target}\"";
			}
		}
	};
}
// Generate parallel deploy stages for Tier 2
def createDeploy(String target) {
	return {
		node("cinch-test-builder") {
			def venv = new Virtualenv(pwd(), cinchPath, ["${WORKSPACE}/dist/cinch*.whl"]);
			cleanWs();
			unstash "build";
			// Check out the files related to topologies
			dir(topologyCheckoutDir) {
				git url: "${TOPOLOGY_DIR_URL}", branch: topologyBranch;
			}
			// Test running cinch from the new install on the target machines
			dir(topologyWorkspaceDir) {
				unstash target;
				venv.exec(["cinch inventories/${target}.inventory"]);
			}
		}
	}
}
// Execute a parallel provision step
def createProvision(String target,
                    String direction,
                    String nodeName="cinch-test-builder",
                    boolean doStash=true) {
	return {
		node(nodeName) {
			def linchpin = new Virtualenv(pwd(), linchpinPath, linchpinPackages);
			// Fetch the topology files
			dir(topologyCheckoutDir) {
				git url: "${TOPOLOGY_DIR_URL}", branch: topologyBranch;
			}
			// Inside of the linchpin subdir, bring up the specified systems
			dir(topologyWorkspaceDir) {
				linchpin.exec(["ansible-playbook --version",
						'WORKSPACE="$(pwd)" linchpin --creds-path credentials -v '
							+ direction + ' ' + target]);
				if (doStash) {
					stash name: target, includes: "inventories/${target}.inventory,resources/${target}*";
					successfulProvisions << target;
				}
			}
		}
	};
}

try {
	stage("Provision Builder") {
		node {
			// Clean up from previous runs
			cleanWs();
			// Initialize the virtualenvs
			def linchpin = new Virtualenv(WORKSPACE, linchpinPath, linchpinPackages);
			def cinch = new Virtualenv(WORKSPACE, cinchPath, cinchPackages);
			// This repository contains the topology files that are needed to spin up
			// our instances with linchpin
			dir(topologyCheckoutDir) {
				git url:"${TOPOLOGY_DIR_URL}", branch: topologyBranch;
			}
			// Necessary at this step for calling Ansible to create the lint/build host
			dir("cinch") {
				checkout scm
			}
			dir(topologyWorkspaceDir) {
				// Spin up new instances for our testing
				linchpin.exec(['WORKSPACE="$(pwd)" linchpin --creds-path credentials -v up builder']);
				stash name: "builder", includes: "inventories/builder.inventory,resources/builder*";
				successfulProvisions << "builder";
				cinch.exec(["cinch inventories/builder.inventory",
				          // This will actually test the pending builder.yml playbook, not the one installed
				          // from pip, so we make sure the current code is able to test itself
				          "ansible-playbook -i inventories/builder.inventory ${WORKSPACE}/cinch/cinch/playbooks/builder.yml"]);
			}
		}
	}


	stage("Tier 0 - Build artifact") {
		node("cinch-test-builder") {
			// Clean from previous runs
			cleanWs();
			dir("cinch") {
				checkout scm;
				sh "python setup.py sdist bdist_wheel";
				stash name: "build", includes: "dist/*";
			}
		}
	}


	stage("Tier 1 - lint, unit tests, etc") {
		def builds = [:];
		for( String target : toxTargets ) {
			builds[target] = createBuild(target);
		}
		parallel builds;
	}


	stage("Provision deploy tier") {
		def provisions = [:];
		for( String target : cinchTargets ) {
			provisions[target] = createProvision(target, "up");
		}
		parallel provisions;
	}

	stage("Tier 2 - Deploys") {
		// First, we create a list of all the provision and all the deploy (test)
		// steps that we must tackle
		def deploys = [:];
		for( String target : cinchTargets ) {
			deploys[target] = createDeploy(target);
		}
		parallel deploys;
	}

} finally {
	stage("Tear Down") {
		node {
			// Checkout our topologies and credentials, just to be sure
			dir(topologyCheckoutDir) {
				git url:"${TOPOLOGY_DIR_URL}", branch: topologyBranch;
			}
			// Create a teardown step for each target that was successfully
			// provisioned, and unstash the resulting build files
			def teardowns = [:];
			for (String target : successfulProvisions) {
				teardowns[target] = createProvision(target, "down", "master", false);
				dir(topologyWorkspaceDir) {
					unstash target;
				}
			}
			// Attempt to shut down the Swarm process on the builder, if one
			// was created
			dir(topologyWorkspaceDir) {
				if(fileExists("inventories/builder.inventory")) {
					def linchpin = new Virtualenv(WORKSPACE, linchpinPath, linchpinPackages);
					linchpin.exec(["teardown inventories/builder.inventory || echo 'Teardown failed'"]);
				}
			}
			// Perform actual teardown steps in parallel
			parallel teardowns;
		}
	}
}
