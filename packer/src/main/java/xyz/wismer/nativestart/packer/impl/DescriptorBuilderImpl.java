package xyz.wismer.nativestart.packer.impl;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.apache.commons.lang3.StringUtils;
import xyz.wismer.nativestart.packer.CompressionAlgorithm;
import xyz.wismer.nativestart.packer.DescriptorBuilder;
import xyz.wismer.nativestart.packer.HashAlgorithm;
import xyz.wismer.nativestart.packer.OperatingSystem;
import xyz.wismer.nativestart.packer.util.CompressUtils;
import xyz.wismer.nativestart.packer.util.HashUtils;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
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
	private Artifact splash;
	private final OperatingSystem os;
	private final HashAlgorithm hashAlgorithm;

	private Artifact jvm;
	private final JvmParameters jvmParameters = new JvmParameters();
	private final List<Artifact> libraries = new ArrayList<>();
	private final List<Artifact> resources = new ArrayList<>();
	private final List<String> unmanagedPaths = new ArrayList<>();

	// smaller output, but much slower: CompressionAlgorithm.XZ with level 9
	private CompressionAlgorithm compressionAlgorithm = CompressionAlgorithm.ZSTD;
	private int compressionLevel = 12;
	private final Map<Artifact, File> toCompress = new HashMap<>();

	public DescriptorBuilderImpl(String name, String version, OperatingSystem os, HashAlgorithm hashAlgorithm) {
		this.name = name;
		this.version = version;
		this.os = os;
		this.hashAlgorithm = hashAlgorithm;
	}

	@Override
	public DescriptorBuilder splash(File folder, String downloadPath, String installationPath) throws IOException {
		splash = createArtifact(folder, downloadPath, installationPath);
		return this;
	}

	@Override
	public DescriptorBuilder jvm(File folder, String downloadPath, String installationPath) throws IOException {
		jvm(installationPath);
		jvm = createArtifact(folder, downloadPath, dir(installationPath));
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
	public DescriptorBuilder library(File file, String downloadPath, String installationPath) throws IOException {
		libraries.add(createArtifact(file, downloadPath, installationPath));
		return this;
	}

	@Override
	public DescriptorBuilder resource(File file, String downloadPath, String installationPath) throws IOException {
		resources.add(createArtifact(file, downloadPath, installationPath));
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
				.map(Artifact::getPath)
				.collect(Collectors.joining(os.pathSeparator()));
		systemProperty("java.class.path", classpath);

		List<Artifact> artifacts = new ArrayList<>();
		artifacts.add(jvm);
		artifacts.addAll(libraries);
		artifacts.addAll(resources);

		List<Artifact> artifactsAndSplash = new ArrayList<>(artifacts);
		artifactsAndSplash.add(splash);

		// derive URL from path if not set
		for (Artifact artifact : artifactsAndSplash) {
			if (artifact.getUrl() == null) {
				artifact.setUrl(url(baseURL, artifact.getPath()));
			} else {
				artifact.setUrl(url(baseURL, artifact.getUrl()));
			}
		}

		// compress resources
		for (Map.Entry<Artifact, File> entry : toCompress.entrySet()) {
			Artifact artifact = entry.getKey();
			String filename = StringUtils.substringAfterLast(artifact.getUrl(), "/");
			if (!filename.endsWith(compressionAlgorithm.getFileExtension())) {
				filename = filename + compressionAlgorithm.getFileExtension();
				artifact.setUrl(artifact.getUrl() + compressionAlgorithm.getFileExtension());
			}
			File compressedFile = new File(targetDirectory, filename);
			if (!compressedFile.exists()) {
				CompressUtils.compress(entry.getValue(), compressedFile, compressionAlgorithm, compressionLevel);
			}
			long downloadSize = compressedFile.length();
			if (downloadSize != artifact.getSize()) {
				artifact.setDownloadSize(downloadSize);
			}
		}

		Descriptor desc = new Descriptor(name, version);
		desc.setSplash(splash);
		desc.setJvmParams(jvmParameters);
		desc.setArtifacts(artifacts);
		desc.setUnmanagedPaths(unmanagedPaths.isEmpty() ? null : unmanagedPaths);

		Gson gson = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
		if (signatureKey != null) {
			desc.setSignature("");
			byte[] manifest = gson.toJson(desc).getBytes(StandardCharsets.UTF_8);
			desc.setSignature(HashUtils.sign(manifest, signatureKey));
		}

		Files.write(targetDirectory.toPath().resolve(name + "-" + os.name().toLowerCase() + ".json"),
				gson.toJson(desc).getBytes());
	}

	private Artifact createArtifact(File file, String downloadPath, String installationPath) throws IOException {
		HashUtils.Info info = HashUtils.hash(hashAlgorithm, file);
		Artifact artifact = new Artifact(downloadPath, info.getSize(), info.getHash(), installationPath);
		if (file.isDirectory()) {
			toCompress.put(artifact, file);
		}
		return artifact;
	}

	private String url(URL baseURL, String urlOrPath) throws MalformedURLException {
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
