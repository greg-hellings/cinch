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
try {
	stage("Provision") {
		node {
			dir("topology-dir") {
				git url:"${TOPOLOGY_DIR_URL}", branch: "master"
			}
			sh "virtualenv --system-site-packages linchpin"
			sh "linchpin/bin/pip install -U pip"
			sh "linchpin/bin/pip install -U linchpin==1.0.1 cinch==0.6.0"
			dir('topology-dir/test/') {
				// Clean the cruft from previous runs, first
				sh "rm -rf inventories/*.inventory resources/*.output"
				sh "chmod 600 ../examples/linch-pin-topologies/openstack-master/keystore/ci-ops-central"
				sh "WORKSPACE=\$(pwd) ../../linchpin/bin/linchpin --creds-path credentials -v up builder"
				sh "PATH=${WORKSPACE}/linchpin/bin/:\$PATH cinch inventories/builder.inventory"
				sh "../../linchpin/bin/ansible -i inventories/builder.inventory -m package -a 'name=python3-tox,python2-virtualenv,python3-virtualenv,ShellCheck state=present' all"
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
			dir("topology-dir/test/") {
				sh "WORKSPACE=\$(pwd) ../../linchpin/bin/linchpin --creds-path credentials -v down builder"
			}
		}
	}
}
