package xyz.wismer.nativestart.packer.impl;

import java.util.ArrayList;
import java.util.List;

public class Descriptor {
	private final String name;
	private final String version;
	private String signature;
	private Artifact splash;
	private JvmParameters jvmParams;
	private List<Artifact> artifacts = new ArrayList<>();
	private List<String> unmanagedPaths = new ArrayList<>();

	public Descriptor(String name, String version) {
		this.name = name;
		this.version = version;
	}

	public String getName() {
		return name;
	}

	public void setSplash(Artifact splash) {
		this.splash = splash;
	}

	public void setSignature(String signature) {
		this.signature = signature;
	}

	public void setArtifacts(List<Artifact> artifacts) {
		this.artifacts = artifacts;
	}

	public void addArtifact(Artifact artifact) {
		artifacts.add(artifact);
	}

	public void setJvmParams(JvmParameters jvmParams) {
		this.jvmParams = jvmParams;
	}

	public void setUnmanagedPaths(List<String> unmanagedPaths) {
		this.unmanagedPaths = unmanagedPaths;
	}
}
