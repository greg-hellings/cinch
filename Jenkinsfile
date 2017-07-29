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
def virtualenv(String name, List deps=[]) {
	sh """virtualenv --no-setuptools "${name}"
	      . "${name}/bin/activate"
	      curl https://bootstrap.pypa.io/get-pip.py | python
	      ln -s /usr/lib64/python2.7/site-packages/selinux linchpin/lib/python2.7/site-packages
	      pip install ${deps.join(' ')}"""
}
def venvExec(String ctx, List<String> cmds) {
	sh """. "${ctx}/bin/activate"
	      ${cmds.join('\n')}"""
}
def createBuild(String target) {
	return {
		node("cinch-test-builder") {
			checkout scm
			// Virtualenv lines temporary until Fedora builds available
			sh """virtualenv tox
			      . tox/bin/activate
			      pip install pip==9.0.1
			      pip install tox==2.7.0
			      tox --version
			      tox -e """ + target
		}
	};
}
def createDeploy(String target) {
	return {
		node {
			unstash "builds";
			sh """virtualenv venv
			      venv/bin/pip install pip==9.0.1
			      venv/bin/pip install cinch
			      venv/bin/pip install dist/cinch*.whl
			      venv/bin/cinch """ + target;
		}
	}
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
		// Installing from moving source target depends on the following releases
		// linchpin needs to support openstack userdata variables (v1.1?)
		// cinch needs to support the tox testing builds (v0.8?)
		// cinch needs to support discrete teardown command (v0.8?)
		if ( !fileExists( "linchpin" ) ) {
			virtualenv 'linchpin', ["https://github.com/CentOS-PaaS-SiG/linchpin/archive/develop.tar.gz", "https://github.com/greg-hellings/cinch/archive/tox.tar.gz"]
		}
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


	stage "Build artifact"
	node {
		virtualenv "venv", ["wheel"]
		dir("cinch") {
			checkout scm
			venvExec "../venv", ["python setup.py sdist bdist_wheel"]
			stash name: "builds", includes: "dist/*"
		}
	}


	stage "Tier 1"
	def targets = ["lint", "docs", "py27"];
	def builds = [:];
	for( String target : targets ) {
		builds[target] = createBuild(target);
	}
	parallel builds;


	stage "Tier 2"
	targets = ["rhel7_rhel7_nosec_nossl"];
	build = [:];
	for( String target : targets ) {
		builds[target] = createDeploy(target);
	}
	parallel builds;


	stage "Build Images"
	targets = ["cent6_slave", "cent7_slave", "fedora_slave",
               "fedora_master", "cent7_master"];
	builds = [:];
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
				sh "WORKSPACE=\$(pwd) ../../linchpin/bin/linchpin --creds-path credentials -v down"
			}
		}
	}
}
