package xyz.wismer.nativestart.packer.manifest;

import xyz.wismer.nativestart.packer.OperatingSystem;

import java.util.ArrayList;
import java.util.List;

public class JvmParameters {
	private String jvmPath;
	private String jvmLibrary;
	private String mainClass;
	private List<String> options = new ArrayList<>();

	public void setInstallation(OperatingSystem os, String installationPath) {
		switch (os) {
			case WINDOWS:
				setJvmPath(installationPath + "bin");
				setJvmLibrary("server/jvm.dll");
				break;
			case LINUX:
				setJvmPath(".");
				setJvmLibrary(installationPath + "lib/server/libjvm.so");
				break;
			case MAC:
				setJvmPath(installationPath + "lib");
				// Java 11 would actually be "jli/libjli.dylib" (not supported)
				setJvmLibrary("libjli.dylib");
				break;
		}
	}

	public String getJvmPath() {
		return jvmPath;
	}

	public void setJvmPath(String jvmPath) {
		this.jvmPath = jvmPath;
	}

	public String getJvmLibrary() {
		return jvmLibrary;
	}

	public void setJvmLibrary(String jvmLibrary) {
		this.jvmLibrary = jvmLibrary;
	}

	public String getMainClass() {
		return mainClass;
	}

	public void setMainClass(String mainClass) {
		this.mainClass = mainClass;
	}

	public List<String> getOptions() {
		return options;
	}

	public void setOptions(List<String> options) {
		this.options = options;
	}

	public void append(Toml toml) {
		toml.append("path", jvmPath);
		toml.append("library", jvmLibrary);
		toml.append("main", mainClass);
		toml.append("options", options);
	}
}
