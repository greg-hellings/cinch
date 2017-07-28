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
def topologyBranch = "master";
def createBuild(String target) {
	return {
		node("cinch-test-builder") {
			checkout scm
			// Virtualenv lines temporary until Fedora builds available
			sh "virtualenv tox"
			sh "tox/bin/pip install pip==9.0.1"
			sh "tox/bin/pip install tox==2.7.0"
			sh "tox/bin/tox --version"
			sh "tox/bin/tox -e " + target
		}
	};
}


try {
	stage "Provision"
	node {
		dir("topology-dir") {
			git url:"${TOPOLOGY_DIR_URL}", branch: topologyBranch
		}
		dir("cinch") {
			checkout scm
		}
		// Avoid re-creating this every time we run
		if ( !fileExists( "linchpin" ) ) {
			sh "virtualenv --no-setuptools linchpin"
			sh "curl https://bootstrap.pypa.io/get-pip.py | linchpin/bin/python"
			sh "ln -s /usr/lib64/python2.7/site-packages/selinux linchpin/lib/python2.7/site-packages"
		}
		sh "linchpin/bin/pip install -U pip==9.0.1"
		// Installing from moving source target depends on the following releases
		// linchpin needs to support openstack userdata variables (v1.1?)
		// cinch needs to support the tox testing builds (v0.8?)
		// cinch needs to support discrete teardown command (v0.8?)
		sh "linchpin/bin/pip install https://github.com/CentOS-PaaS-SiG/linchpin/archive/develop.tar.gz https://github.com/greg-hellings/cinch/archive/tox.tar.gz"
		dir('topology-dir/test/') {
			// Clean the cruft from previous runs, first
			sh "rm -rf inventories/*.inventory resources/*.output"
			sh "chmod 600 ../examples/linch-pin-topologies/openstack-master/keystore/ci-ops-central"
			// Bring up the necessary hosts for our job
			sh "WORKSPACE=\"\$(pwd)\" ../../linchpin/bin/linchpin --creds-path credentials -v up builder"
			stash name: "output", includes: "inventories/*.inventory,resources/*"
			sh "PATH=${WORKSPACE}/linchpin/bin/:${PATH} cinch inventories/builder.inventory"
			// Configure the host for building and running tests
			sh "../../linchpin/bin/ansible-playbook -i inventories/builder.inventory" +
			   " ${WORKSPACE}/cinch/cinch/playbooks/builder.yml"
		}
	}


	stage "Tier 1"
	def targets = ["lint", "docs", "py27"];
	def builds = [:];
	for( String target : targets ) {
		builds[target] = createBuild(target);
	}
	parallel builds;


	stage "Build Images"
	targets = ["cent6_slave", "cent7_slave", "cent7_master", "fedora_slave"];
	for( String target : targets ) {
		builds[target] = createBuild(target);
	}
	parallel builds;

} finally {
	stage("Tear Down") {
		node {
			dir("topology-dir") {
				git url:"${TOPOLOGY_DIR_URL}", branch: topologyBranch
			}
			dir("topology-dir/test/") {
				unstash "output"
				sh "PATH=\"\${WORKSPACE}/linchpin/bin/:\$PATH\" ../../linchpin/bin/teardown inventories/builder.inventory"
				sh "WORKSPACE=\$(pwd) ../../linchpin/bin/linchpin --creds-path credentials -v down builder"
			}
		}
	}
}
