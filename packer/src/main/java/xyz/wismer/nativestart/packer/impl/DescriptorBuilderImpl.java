package xyz.wismer.nativestart.packer.impl;

import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import xyz.wismer.nativestart.packer.Component;
import xyz.wismer.nativestart.packer.CompressionAlgorithm;
import xyz.wismer.nativestart.packer.DescriptorBuilder;
import xyz.wismer.nativestart.packer.HashAlgorithm;
import xyz.wismer.nativestart.packer.OperatingSystem;
import xyz.wismer.nativestart.packer.manifest.Descriptor;
import xyz.wismer.nativestart.packer.manifest.JvmParameters;
import xyz.wismer.nativestart.packer.util.CompressUtils;
import xyz.wismer.nativestart.packer.util.HashUtils;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.PrivateKey;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class DescriptorBuilderImpl implements DescriptorBuilder {

	private final String name;
	private final String version;
	private Component splash;
	private final OperatingSystem os;
	private final HashAlgorithm hashAlgorithm;

	private Component jvm;
	private final JvmParameters jvmParameters = new JvmParameters();
	private final List<Component> libraries = new ArrayList<>();
	private final List<Component> resources = new ArrayList<>();
	private final List<String> unmanagedPaths = new ArrayList<>();

	// smaller output, but much slower: CompressionAlgorithm.XZ with level 9
	private CompressionAlgorithm compressionAlgorithm = CompressionAlgorithm.ZSTD;
	private int compressionLevel = 12;
	private final Map<Component, File> toCompress = new HashMap<>();

	public DescriptorBuilderImpl(String name, String version, OperatingSystem os, HashAlgorithm hashAlgorithm) {
		this.name = name;
		this.version = version;
		this.os = os;
		this.hashAlgorithm = hashAlgorithm;
	}

	@Override
	public DescriptorBuilder splash(Component component) throws IOException {
		splash = createComponent(component);
		return this;
	}

	@Override
	public DescriptorBuilder jvm(Component component) throws IOException {
		jvm(dir(component.getInstallationPath()));
		jvm = createComponent(component);
		return this;
	}

	private void jvm(String installationPath) {
		jvmParameters.setInstallation(os, installationPath);
	}

	@Override
	public DescriptorBuilder main(String mainClass) {
		jvmParameters.setMainClass(mainClass.replace('.', '/'));
		return this;
	}

	@Override
	public DescriptorBuilder option(String option) {
		jvmParameters.getOptions().add(option);
		return this;
	}

	@Override
	public DescriptorBuilder library(Component component) throws IOException {
		libraries.add(createComponent(component));
		return this;
	}

	@Override
	public DescriptorBuilder resource(Component component) throws IOException {
		resources.add(createComponent(component));
		return this;
	}

	@Override
	public DescriptorBuilder unmanaged(String installationPath) {
		unmanagedPaths.add(installationPath);
		return this;
	}

	@Override
	public DescriptorBuilder compression(CompressionAlgorithm algorithm, int level) {
		compressionAlgorithm = algorithm;
		compressionLevel = level;
		return this;
	}

	@Override
	public void generate(File targetDirectory, URL baseURL, PrivateKey signatureKey) throws IOException {
		String classpath = libraries.stream()
				.map(Component::getInstallationPath)
				.collect(Collectors.joining(os.pathSeparator()));
		systemProperty("java.class.path", classpath);

		List<Component> components = new ArrayList<>();
		components.add(jvm);
		components.addAll(libraries);
		components.addAll(resources);

		// compress resources
		for (Map.Entry<Component, File> entry : toCompress.entrySet()) {
			Component component = entry.getKey();
			String filename = StringUtils.substringAfterLast(component.getRemotePath(), "/");
			if (!filename.endsWith(compressionAlgorithm.getFileExtension())) {
				filename = filename + compressionAlgorithm.getFileExtension();
				component.setRemotePath(component.getRemotePath() + compressionAlgorithm.getFileExtension());
			}
			File compressedFile = new File(targetDirectory, filename);
			if (!compressedFile.exists()) {
				CompressUtils.compress(entry.getValue(), compressedFile, compressionAlgorithm, compressionLevel);
			}
			long downloadSize = compressedFile.length();
			if (downloadSize != component.getInstallationSize()) {
				component.setRemoteSize(downloadSize);
			}
		}

		Descriptor desc = new Descriptor(name, version);
		desc.setSplash(toManifest(splash, baseURL));
		desc.setJvmParams(jvmParameters);
		desc.setComponents(components.stream().map(a -> toManifest(a, baseURL)).toList());
		desc.setUnmanagedPaths(unmanagedPaths.isEmpty() ? null : unmanagedPaths);

		if (signatureKey != null) {
			desc.setSignature("");
			byte[] manifest = desc.toToml().getBytes(StandardCharsets.UTF_8);
			desc.setSignature(HashUtils.sign(manifest, signatureKey));
		}

		Files.write(targetDirectory.toPath().resolve(name + "-" + version + "-" + os.name().toLowerCase() + ".toml"),
				desc.toToml().getBytes());
	}

	private Component createComponent(Component component) throws IOException {
		File localSource = component.getLocalSource();
		if (localSource != null) {
			HashUtils.Info info = HashUtils.hash(hashAlgorithm, localSource);
			component.setInstallationSize(info.getSize());
			component.setInstallationChecksum(info.getHash());
			if (localSource.isDirectory()) {
				toCompress.put(component, localSource);
			}
			else {
				component.setRemoteSize(info.getSize());
			}
		}
		return component;
	}

	private xyz.wismer.nativestart.packer.manifest.Component toManifest(Component component, URL baseURL) {
		String url = url(baseURL, ObjectUtils.firstNonNull(component.getRemotePath(), component.getInstallationPath()));
		xyz.wismer.nativestart.packer.manifest.Component result = new xyz.wismer.nativestart.packer.manifest.Component(url, component.getInstallationSize(),
				component.getInstallationChecksum(), component.getInstallationPath());
		if (component.getRemoteSize() != component.getInstallationSize()) {
			result.setDownloadSize(component.getRemoteSize());
		}
		result.setCachePath(component.getCachePath());
		return result;
	}

	private String url(URL baseURL, String urlOrPath) {
		if (urlOrPath.startsWith("http://") || urlOrPath.startsWith("https://")) {
			return urlOrPath;
		} else {
			return dir(baseURL.toString()) + urlOrPath;
		}
	}

	private String dir(String directory) {
		if (directory.endsWith("/")) {
			return directory;
		} else {
			return directory + "/";
		}
	}
}
