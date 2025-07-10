package xyz.wismer.nativestart.packer;


import xyz.wismer.nativestart.packer.impl.DescriptorBuilderImpl;
import xyz.wismer.nativestart.packer.impl.ExecutableBuilderImpl;

public class Packer {

	public static ExecutableBuilder executableBuilder(String name, String url) {
		return new ExecutableBuilderImpl(name, url);
	}

	public static DescriptorBuilder descriptorBuilder(String name, String version, OperatingSystem os) {
		return new DescriptorBuilderImpl(name, version, os, HashAlgorithm.SHA256);
	}

	public static DescriptorBuilder descriptorBuilder(String name, String version, OperatingSystem os, HashAlgorithm hashAlgorithm) {
		return new DescriptorBuilderImpl(name, version, os, hashAlgorithm);
	}
}
