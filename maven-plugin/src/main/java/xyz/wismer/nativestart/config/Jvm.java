package xyz.wismer.nativestart.config;

public class Jvm {
	/** The Java feature version (e.g. 25) */
	private String featureVersion;
	/** The full Java version including the build (e.g. 25.0.4+7) */
	private String version;
	/** The CPU architecture (x86_64 or aarch64) */
	private String arch;
	/** The operating system (windows, linux mac) */
	private String os;
	/** The modules to integrate in the JVM */
	private Modules modules;

	public String getFeatureVersion() {
		return featureVersion;
	}

	public void setFeatureVersion(String featureVersion) {
		this.featureVersion = featureVersion;
	}

	public String getVersion() {
		return version;
	}

	public void setVersion(String version) {
		this.version = version;
	}

	public String getArch() {
		return arch;
	}

	public void setArch(String arch) {
		this.arch = arch;
	}

	public String getOs() {
		return os;
	}

	public void setOs(String os) {
		this.os = os;
	}

	public Modules getModules() {
		return modules;
	}

	public void setModules(Modules modules) {
		this.modules = modules;
	}
}
