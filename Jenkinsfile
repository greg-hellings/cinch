#!groovy
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
// Trying to avoid "magic strings"
def topologyBranch = "master";
def toxVersion = "2.7.0";
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

// Python virtualenv helper files
def virtualenv(String name, List deps=[]) {
	sh """if [ ! -d "${name}" ]; then virtualenv --no-setuptools "${name}"; fi
	      . "${name}/bin/activate"
	      if [ ! -d "${name}/bin/pip" ]; then curl https://bootstrap.pypa.io/get-pip.py | python; fi
	      ln -sf /usr/lib64/python2.7/site-packages/selinux "${name}/lib/python2.7/site-packages"
	      pip install ${deps.join(' ')}"""
}
def venvExec(String ctx, List<String> cmds) {
	sh """. "${ctx}/bin/activate"
	      ${cmds.join('\n')}"""
}

// Generate parallel build stages for Tier 1
def createBuild(String target) {
	return {
		node("cinch-test-builder") {
			checkout scm
			// Virtualenv lines temporary until Fedora builds available
			virtualenv "tox", ["tox==${toxVersion}"]
			venvExec "tox", ["tox -e \"${target}\""]
		}
	};
}
// Generate parallel deploy stages for Tier 2
def createDeploy(String target, String topologyBranch) {
	return {
		node {
			// Clean the environment. Pipeline jobs don't seem to do that
			sh "rm -rf dist/* venv secrets";
			dir("secrets") {
				git url: "${TOPOLOGY_DIR_URL}", branch: topologyBranch;
			}
			// Create a virtualenv with the new test cinch instance in it
			unstash "build";
			virtualenv "${WORKSPACE}/venv", ["dist/cinch*.whl"];
			// Test running cinch from the new install on the target machines
			dir("secrets/tests/") {
				unstash target;
				venvExec "${WORKSPACE}/venv",
				        ["cinch inventories/${target}.inventory -e @${WORKSPACE}/secrets/test/vars"];
			}
		}
	}
}
// Execute a parallel provision step
def createProvision(String target, String direction) {
	return {
		venvExec "${WORKSPACE}/linchpin", ['WORKSPACE="$(pwd)" linchpin --creds-path credentials -v '
		                                   + direction + ' ' + target];
		stash name: target, includes: "inventories/*.inventory,resources/*";
	};
}

try {
	stage("Provision") {
		node {
			// Clean up from previous runs
			sh "rm -rf secrets cinch";
			// Installing from moving source target depends on the following releases
			// linchpin needs to support openstack userdata variables (v1.1?)
			// cinch needs to support the tox testing builds (v0.8?)
			// cinch needs to support discrete teardown command (v0.8?)
			virtualenv "${WORKSPACE}/linchpin", ["https://github.com/CentOS-PaaS-SiG/linchpin/archive/develop.tar.gz", "https://github.com/greg-hellings/cinch/archive/tox.tar.gz"];
			// This repository contains the topology files that are needed to spin up
			// our instances with linchpin
			dir("secrets") {
				git url:"${TOPOLOGY_DIR_URL}", branch: topologyBranch;
			}
			// Necessary at this step for calling Ansible to create the lint/build host
			dir("cinch") {
				checkout scm
			}
			dir('cinch/tests/') {
				// Spin up new instances for our testing
				def provisions = [:];
				for( String target : cinchTargets) {
					provisions[target] = createProvision(target, "up");
				}
				provisions["builder"] = createProvision("builder", "up");
				parallel provisions
				unstash "builder";
				venvExec "${WORKSPACE}/linchpin",
				         ["cinch inventories/builder.inventory -e @${WORKSPACE}/secrets/tests/vars",
				          "ansible-playbook -i inventories/builder.inventory ${WORKSPACE}/cinch/cinch/playbooks/builder.yml"]
			}
		}
	}


	stage("Build artifact") {
		node("cinch-test-builder") {
			// Clean from previous runs
			sh "rm -rf cinch";
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


	stage("Tier 2 - deploys") {
		builds = [:];
		for( String target : cinchTargets ) {
			builds[target] = createDeploy(target, topologyBranch);
		}
		parallel builds;
	}

} finally {
	stage("Tear Down") {
		node {
			dir("cinch/tests/") {
				unstash "builder";
				// Try to stop the swarm service on the Jenkins build slave, so that it
				// does not stay on as a stale instance for a long time in the Jenkins
				// master. But, don't actually bother with anything if it fails, because
				// maybe this finally block here happened before the slave connected. We
				// still need to try and de-provision the dynamic hosts
				try {
					venvExec "${WORKSPACE}/linchpin",
						["teardown inventories/builder.inventory || echo 'Teardown failed'"];
				} finally {
					// nop
				}
				builds = [:];
				for(String target : cinchTargets) {
					builds[target] = createProvision(target, "down");
				}
				builds["builder"] = createProvision("builder", "down");
				parallel builds;
			}
		}
	}
}
