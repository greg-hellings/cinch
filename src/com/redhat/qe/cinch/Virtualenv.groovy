package com.redhat.qe.cinch;

public class Virtualenv implements Serializable {
	public static def script;
	private String basePath;
	private List deps;

	public Virtualenv(String workspace, String name, List deps) {
		this.basePath = workspace + '/venvs/' + name;
		this.deps = deps;
	}

	// Python virtualenv helper files
	public void install() {
		script.sh """if [ ! -d "${this.basePath}" ]; then virtualenv --no-setuptools "${this.basePath}"; fi
			  . "${this.basePath}/bin/activate"
			  if [ ! -d "${this.basePath}/bin/pip" ]; then curl https://bootstrap.pypa.io/get-pip.py | python; fi
			  ln -sf /usr/lib64/python2.7/site-packages/selinux "${this.basePath}/lib/python2.7/site-packages"
			  ln -sf /usr/lib64/python2.7/site-packages/_selinux.so "${this.basePath}/lib64/python2.7/site-packages/"
			  pip install ${this.deps.join(' ')}"""
	}

	public void exec(List<String> cmds) {
		this.install();
		script.sh """. "${this.basePath}/bin/activate"
			  ${cmds.join('\n')}"""
	}
}
