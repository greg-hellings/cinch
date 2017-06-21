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
			properties([[$class: 'ParametersDefinitionProperty', parameterDefinitions: [[$class: 'StringParameterDefinition', defaultValue: 'https://example.com/topologies.git', description: 'The URL to a repository containing a directory named "test" which contains the topology files for Linchpin to spin up systems defined at the top of the Jenkinsfile.', name: 'TOPOLOGY_DIR_URL']]]])

			dir("topology-dir") {
				git url:"${TOPOLOGY_GIT_URL}", branch: "master"
			}
			sh "virtualenv --system-site-packages ~/venv/linchpin"
			sh "~/venv/linchpin/bin/pip install -U pip"
			sh "~/venv/linchpin/bin/pip install -U linchpin==1.0.1 cinch==0.6.0"
			dir('topology-dir/test/') {
				sh "WORKSPACE=\$(pwd) ~/venv/linchpin/bin/linchpin --creds-path credentials -v up builder"
				sh "~/venv/linchpin/bin/cinch inventories/builder.inventory"
				sh "~/venv/linchpin/bin/ansible -i inventories/builder.inventory -m package -a 'name=python3-tox,python2-virtualenv,python3-virtualenv,ShellCheck state=present'"
			}
		}
	}

	stage("Tier 1") {
		node('cinch-test-builder') {
			checkout scm
			sh "tox -e lint"
		}
	}
} finally {
	stage("Tear Down") {
		node {
			dir("cinch-docs-internal/text/") {
				sh "WORKSPACE=\$(pwd) ~/venv/linchpin/bin/linchpin down builder"
			}
		}
	}
}
