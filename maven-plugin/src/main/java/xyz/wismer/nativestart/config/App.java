package xyz.wismer.nativestart.config;

import java.util.ArrayList;
import java.util.List;

public class App {
	/** The application ID. Will be used as installation folder name */
	private String appId;
	/** The version of the application */
	private String version;

	// main components
	/** The location for the splash files */
	private Locations splash;
	/** The location for the JVM files */
	private Locations jvm;
	/** The location for the library files */
	private Locations lib;
	/** A comma separated list of GAV patterns describing artifacts to exclude from distribution */
	private String dependencyExcludes;
	/** The location for the general purpose resource files */
	private List<Locations> resources = new ArrayList<>();

	// running
	/** The fully qualified main class of the application */
	private String mainClass;
	/** The JVM options (in format suitable for JNI, see <a href="https://docs.oracle.com/en/java/javase/21/docs/specs/jni/invocation.html#jni_createjavavm">JNI_CreateJavaVM</a>)*/
	private List<String> jvmOptions = new ArrayList<>();
	/** A comma separated list of paths inside the application installation directory that are left untouched by NativeStart */
	private String unmanagedPaths;
	/**
	 * A comma separated list of key=value pairs specifying which Maven artifact ID (key) uses which folder (value)
	 * as cache directory inside the application installation. The directory gets cleared whenever the artifact changes.
	 */
	private String cachePaths;

	public String getAppId() {
		return appId;
	}

	public void setAppId(String appId) {
		this.appId = appId;
	}

	public String getVersion() {
		return version;
	}

	public void setVersion(String version) {
		this.version = version;
	}

	public Locations getSplash() {
		return splash;
	}

	public void setSplash(Locations splash) {
		this.splash = splash;
	}

	public Locations getJvm() {
		return jvm;
	}

	public void setJvm(Locations jvm) {
		this.jvm = jvm;
	}

	public Locations getLib() {
		return lib;
	}

	public void setLib(Locations lib) {
		this.lib = lib;
	}

	public String getDependencyExcludes() {
		return dependencyExcludes;
	}

	public void setDependencyExcludes(String dependencyExcludes) {
		this.dependencyExcludes = dependencyExcludes;
	}

	public List<Locations> getResources() {
		return resources;
	}

	public void setResources(List<Locations> resources) {
		this.resources = resources;
	}

	public String getMainClass() {
		return mainClass;
	}

	public void setMainClass(String mainClass) {
		this.mainClass = mainClass;
	}

	public List<String> getJvmOptions() {
		return jvmOptions;
	}

	public void setJvmOptions(List<String> jvmOptions) {
		this.jvmOptions = jvmOptions;
	}

	public String getUnmanagedPaths() {
		return unmanagedPaths;
	}

	public void setUnmanagedPaths(String unmanagedPaths) {
		this.unmanagedPaths = unmanagedPaths;
	}

	public String getCachePaths() {
		return cachePaths;
	}

	public void setCachePaths(String cachePaths) {
		this.cachePaths = cachePaths;
	}
}
