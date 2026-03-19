package xyz.wismer.nativestart.packer.manifest;

import java.util.ArrayList;
import java.util.List;

public class Descriptor {
	private final String name;
	private final String version;
	private String signature;
	private Component splash;
	private JvmParameters jvmParams;
	private List<Component> components = new ArrayList<>();
	private List<String> unmanagedPaths = new ArrayList<>();

	public Descriptor(String name, String version) {
		this.name = name;
		this.version = version;
	}

	public String getName() {
		return name;
	}

	public void setSplash(Component splash) {
		this.splash = splash;
	}

	public void setSignature(String signature) {
		this.signature = signature;
	}

	public void setComponents(List<Component> components) {
		this.components = components;
	}

	public void addComponent(Component component) {
		components.add(component);
	}

	public void setJvmParams(JvmParameters jvmParams) {
		this.jvmParams = jvmParams;
	}

	public void setUnmanagedPaths(List<String> unmanagedPaths) {
		this.unmanagedPaths = unmanagedPaths;
	}

	public String toToml() {
		Toml toml = new Toml();
		toml.append("name", name);
		toml.append("version", version);
		toml.append("signature", signature);
		if (unmanagedPaths != null) {
			toml.append("unmanaged", unmanagedPaths);
		}
		toml.appendTable("splash");
		splash.append(toml);
		toml.appendTable("jvm");
		jvmParams.append(toml);
		for (Component component : components) {
			toml.appendArrayOfTable("component");
			component.append(toml);
		}
		return toml.toString();
	}
}
