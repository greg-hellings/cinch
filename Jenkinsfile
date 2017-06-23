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
def topologyBranch = "master"

try {
	stage("Provision") {
		node {
			dir("topology-dir") {
				git url:"${TOPOLOGY_DIR_URL}", branch: topologyBranch
			}
			// Avoid re-creating this every time we run
			if ( !fileExists( "linchpin" ) ) {
				sh "virtualenv --no-setuptools linchpin"
				sh "curl https://bootstrap.pypa.io/get-pip.py | linchpin/bin/python"
				sh "ln -s /usr/lib64/python2.7/site-packages/selinux linchpin/lib/python2.7/site-packages"
			}
			sh "linchpin/bin/pip install -U pip==9.0.1"
			sh "linchpin/bin/pip install https://github.com/CentOS-PaaS-SiG/linchpin/archive/develop.tar.gz https://github.com/RedHatQE/cinch/archive/master.tar.gz"
			dir('topology-dir/test/') {
				// Clean the cruft from previous runs, first
				sh "rm -rf inventories/*.inventory resources/*.output"
				sh "chmod 600 ../examples/linch-pin-topologies/openstack-master/keystore/ci-ops-central"
				sh "WORKSPACE=\$(pwd) ../../linchpin/bin/linchpin --creds-path credentials -v up builder"
				stash name: "output", includes: "inventories/*.inventory,resources/*"
				sh "PATH=${WORKSPACE}/linchpin/bin/:\$PATH cinch inventories/builder.inventory"
			}
		}
	}

	stage("Tier 1") {
		node("cinch-test-builder") {
			checkout scm
			sh "tox -e lint"
		}
	}
} finally {
	stage("Tear Down") {
		node {
			dir("topology-dir") {
				git url:"${TOPOLOGY_DIR_URL}", branch: topologyBranch
			}
			dir("topology-dir/test/") {
				unstash "output"
				sh "WORKSPACE=\$(pwd) ../../linchpin/bin/linchpin --creds-path credentials -v down builder"
			}
		}
	}
}
