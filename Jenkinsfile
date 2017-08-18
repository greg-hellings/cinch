#!groovy
/**
* Spin up resources
* 1. Fedora machine for tox and shell check and docker images
* 2. 2x RHEL 7.4 machines for master/slave without security
* 3. 2x RHEL 7.4 machines for master/slave without security + SSL
* 4. 2x RHEL 7.4 machines for master/slave with security
* 5. 2x RHEL 7.4 machines for master/slave with security + SSL
* 6. 2x Fedora machines for master/slave with security + SSL
*
* Build RHEL-based Docker containers for internal use
* Build out systems from provisioning step
*
* Teardown resources
*/
// Trying to avoid "magic strings"
def topologyBranch = "master";

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

// Generate parallel build stages
def createBuild(String target) {
	return {
		node("cinch-test-builder") {
			checkout scm
			// Virtualenv lines temporary until Fedora builds available
			virtualenv "tox", ["tox==2.7.0"]
			venvExec "tox", ["tox -e \"${target}\""]
		}
	};
}
def createDeploy(String target) {
	def ansible_cfg = """
[defaults]
host_key_checking=False""";
	return {
		node {
			// Need to be able to ignore ansible hosts
			writeFile file: "~/ansible.cfg", text: ansible_cfg;
			// Clean the environment. Pipeline jobs don't seem to do that
			sh "rm -rf dist/* ${WORKSPACE}/venv"
			// Fetch the build artifacts and install them
			unstash "build";
			virtualenv "${WORKSPACE}/venv", ["dist/cinch*.whl"];
			dir("topology-dir") {
				git url: "${TOPOLOGY_DIR_URL}", branch: topologyBranch;
			}
			dir("topology-dir/tests") {
				unstash "output";
				venvExec "${WORKSPACE}/venv", ["cinch \"inventories/${target}.inventory\""]
			}
		}
	}
}


try {
	stage("Provision") {
		node {
			dir("topology-dir") {
				git url:"${TOPOLOGY_DIR_URL}", branch: topologyBranch
			}
			dir("cinch") {
				checkout scm
			}
			// Installing from moving source target depends on the following releases
			// linchpin needs to support openstack userdata variables (v1.1?)
			// cinch needs to support the tox testing builds (v0.8?)
			// cinch needs to support discrete teardown command (v0.8?)
			virtualenv 'linchpin', ["https://github.com/CentOS-PaaS-SiG/linchpin/archive/develop.tar.gz", "https://github.com/greg-hellings/cinch/archive/tox.tar.gz"]
			dir('topology-dir/test/') {
				// Clean the cruft from previous runs, first
				sh """rm -rf inventories/*.inventory resources/*.output
				      chmod 600 ../examples/linch-pin-topologies/openstack-master/keystore/ci-ops-central
				      # Bring up the necessary hosts for our job
				      . ../../linchpin/bin/activate
				      WORKSPACE="\$(pwd)" linchpin --creds-path credentials -v up"""
				stash name: "output", includes: "inventories/*.inventory,resources/*"
				venvExec "../../linchpin",
				         ["cinch inventories/builder.inventory",
				          "ansible-playbook -i inventories/builder.inventory ${WORKSPACE}/cinch/cinch/playbooks/builder.yml"]
			}
		}
	}


	stage("Build artifact") {
		node {
			virtualenv "venv", ["wheel"]
			dir("cinch") {
				checkout scm
				venvExec "../venv", ["python setup.py sdist bdist_wheel"]
				stash name: "build", includes: "dist/*"
			}
		}
	}


	stage("Tier 1") {
		def targets = ["lint", "docs", "py27"];
		def builds = [:];
		for( String target : targets ) {
			builds[target] = createBuild(target);
		}
		parallel builds;
	}


	stage("Tier 2") {
		targets = ["rhel7_rhel7_nosec_nossl"];
		builds = [:];
		for( String target : targets ) {
			builds[target] = createDeploy(target);
		}
		parallel builds;
	}


	stage("Build Images") {
		targets = ["cent6_slave", "cent7_slave", "fedora_slave",
		           "cent7_master"];
		builds = [:];
		for( String target : targets ) {
			builds[target] = createBuild(target);
		}
		parallel builds;
	}

} finally {
	stage("Tear Down") {
		node {
			dir("topology-dir") {
				git url:"${TOPOLOGY_DIR_URL}", branch: topologyBranch
			}
			dir("topology-dir/test/") {
				unstash "output"
				venvExec "../../linchpin",
				    ["teardown inventories/builder.inventory",
				     "WORKSPACE=\$(pwd) linchpin --creds-path credentials -v down"]
			}
		}
	}
}
