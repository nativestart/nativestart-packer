package xyz.wismer.nativestart.config;

import java.util.ArrayList;
import java.util.List;

public class Modules {
	/** The Maven GA coordinate for the root artifact for JDeps analysis */
	private String jdepRoot;
	/** Modules to add to the JDK in addition to the ones found by JDeps analysis */
	private List<String> jdkModules = new ArrayList<>();
	/** The Maven GA coordinate patterns for artifacts that will be integrated as modules (see jmods) */
	private List<String> dependencyModules = new ArrayList<>();
	/** A comma separated list of locales to be included in the JDK */
	private String locales;
	/** The locations of the JMODs for module linking (can either be a file or an HTTPS URL) */
	private List<String> jmods = new ArrayList<>();

	public String getJdepRoot() {
		return jdepRoot;
	}

	public void setJdepRoot(String jdepRoot) {
		this.jdepRoot = jdepRoot;
	}

	public List<String> getJdkModules() {
		return jdkModules;
	}

	public void setJdkModules(List<String> jdkModules) {
		this.jdkModules = jdkModules;
	}

	public List<String> getDependencyModules() {
		return dependencyModules;
	}

	public void setDependencyModules(List<String> dependencyModules) {
		this.dependencyModules = dependencyModules;
	}

	public String getLocales() {
		return locales;
	}

	public void setLocales(String locales) {
		this.locales = locales;
	}

	public List<String> getJmods() {
		return jmods;
	}

	public void setJmods(List<String> jmods) {
		this.jmods = jmods;
	}
}
