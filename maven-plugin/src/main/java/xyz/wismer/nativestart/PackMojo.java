package xyz.wismer.nativestart;

import org.apache.commons.io.FileUtils;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.resolver.filter.AndArtifactFilter;
import org.apache.maven.artifact.resolver.filter.ArtifactFilter;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.model.Plugin;
import org.apache.maven.plugin.BuildPluginManager;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;
import org.apache.maven.shared.artifact.filter.PatternExcludesArtifactFilter;
import org.apache.maven.shared.artifact.filter.PatternIncludesArtifactFilter;
import xyz.wismer.nativestart.config.Locations;
import xyz.wismer.nativestart.packer.Component;
import xyz.wismer.nativestart.packer.CompressionAlgorithm;
import xyz.wismer.nativestart.packer.DescriptorBuilder;
import xyz.wismer.nativestart.packer.HashAlgorithm;
import xyz.wismer.nativestart.packer.OperatingSystem;
import xyz.wismer.nativestart.packer.Packer;
import xyz.wismer.nativestart.packer.util.CompressUtils;
import xyz.wismer.nativestart.packer.util.HashUtils;

import javax.inject.Inject;
import java.io.File;
import java.io.IOException;
import java.lang.module.ModuleFinder;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.MessageDigest;
import java.security.PrivateKey;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import static org.twdata.maven.mojoexecutor.MojoExecutor.*;

/**
 * Mojo to generate the distribution files including the descriptor.
 */
@Mojo(name = "pack", defaultPhase = LifecyclePhase.PACKAGE, requiresDependencyResolution = ResolutionScope.TEST)
public class PackMojo extends AbstractPackerMojo {
	private final MavenProject mavenProject;
	private final MavenSession mavenSession;
	private final BuildPluginManager pluginManager;

	/**
	 * The directory to cache common distribution files like optimized JDKs.
	 */
	@Parameter(name = "cacheDirectory", defaultValue = "${settings.localRepository}/.cache/nativestart")
	private Path cacheDirectory;

	/**
	 * The algorithm to compress the distribution files.
	 */
	@Parameter(name = "compressionAlgorithm", defaultValue = "ZSTD")
	private CompressionAlgorithm compressionAlgorithm;

	/**
	 * The level for compressing the distribution files.
	 */
	@Parameter(name = "compressionLevel", defaultValue = "12")
	private int compressionLevel;

	/**
	 * The directory where to put the generated distribution files.
	 */
	@Parameter(name = "localRepo", defaultValue = "${project.build.directory}/nativestart/repo")
	public File localRepo;

	/**
	 * The URL where the distribution files will be hosted.
	 */
	@Parameter(name = "remoteRepo", required = true)
	protected URL remoteRepo;

	@Inject
	public PackMojo(MavenProject mavenProject, MavenSession mavenSession, BuildPluginManager pluginManager) {
		this.mavenProject = mavenProject;
		this.mavenSession = mavenSession;
		this.pluginManager = pluginManager;
	}

	@Override
	public void execute() throws MojoExecutionException, MojoFailureException {
		// set defaults if missing
		app.setSplash(fillDefaults(app.getSplash(), null, "splash/"));
		app.setLib(fillDefaults(app.getLib(), "lib", "lib"));

		Set<String> modules = getModules();
		Component jvmComponent = packJdk(modules);
		packApp(jvmComponent, modules);
	}

	private void packApp(Component jvmComponent, Set<String> modules) throws MojoExecutionException {
		getLog().info("Packing " + app.getAppId() + " to " + localRepo);

		OperatingSystem operatingSystem = OperatingSystem.valueOf(jvm.getOs().toUpperCase());

		DescriptorBuilder builder = Packer.descriptorBuilder(app.getAppId(), app.getVersion(), operatingSystem, HashAlgorithm.BLAKE3);

		try {
			Files.createDirectories(outputDirectory.resolve(app.getSplash().getDistribution()).getParent());
			builder.splash(new Component(app.getSplash().getSource(), app.getSplash().getDistribution(), app.getSplash().getTarget()));
		} catch (IOException e) {
			throw new MojoExecutionException("Error in splash configuration", e);
		}

		Map<String, String> cachePaths = new HashMap<>();
		if (app.getCachePaths() != null) {
			for (String entry : app.getCachePaths().split(",")) {
				String[] parts = entry.split("=");
				cachePaths.put(parts[0], parts[1]);
			}
		}

		try {
			builder.main(app.getMainClass());
			jvmComponent.setCachePath(cachePaths.get("jvm"));
			builder.jvm(jvmComponent);
			for (String jvmOption : app.getJvmOptions()) {
				builder.option(jvmOption);
			}
		} catch (IOException e) {
			throw new MojoExecutionException("Error in JVM configuration", e);
		}

		try {
			Files.createDirectories(outputDirectory.resolve(app.getLib().getDistribution()));

			for (Artifact artifact : getArtifacts(null)) {
				File file = artifact.getFile();
				if (modules.contains(getModuleName(artifact))) {
					continue;
				}

				Component component = new Component(file,
						dir(app.getLib().getDistribution()) + file.getName(),
						dir(app.getLib().getTarget()) + file.getName());
				component.setCachePath(cachePaths.get(artifact.getArtifactId()));
				builder.library(component);
				if (!app.isRecompressJars()) {
					Files.copy(file.toPath(), outputDirectory.resolve(app.getLib().getDistribution()).resolve(file.getName()), StandardCopyOption.REPLACE_EXISTING);
				}
			}
			builder.recompressLibraries(app.isRecompressJars());

			for (Locations resource : app.getResources()) {
				builder.resource(new Component(resource.getSource(), resource.getDistribution(), resource.getTarget()));
			}

			if (app.getUnmanagedPaths() != null) {
				for (String path : app.getUnmanagedPaths().split(",")) {
					builder.unmanaged(path);
				}

			}
		} catch (IOException e) {
			throw new MojoExecutionException("Error in JVM configuration", e);
		}

		PrivateKey signatureKey = null;
		if (sign != null) {
			try {
				KeyStore ks = KeyStore.getInstance(sign.getKeystore(), sign.getStorepass().toCharArray());
				String password = sign.getKeypass() != null ? sign.getKeypass() : sign.getStorepass();
				signatureKey = (PrivateKey) ks.getKey(sign.getAlias(), password.toCharArray());
			} catch (IOException | GeneralSecurityException e) {
				throw new MojoExecutionException("Error in signing configuration", e);
			}
		}

		try {
			Files.createDirectories(outputDirectory);
			builder.generate(outputDirectory.toFile(), remoteRepo, signatureKey);
		} catch (IOException e) {
			throw new MojoExecutionException("Error when generating distribution", e);
		}
	}

	private Set<String> getModules() throws MojoExecutionException {
		Set<String> modules = new TreeSet<>(runJdeps(jvm.getModules().getJdepRoot()));
		modules.addAll(jvm.getModules().getJdkModules());
		for (String dependencyModule : jvm.getModules().getDependencyModules()) {
			for (Artifact artifact : getArtifacts(dependencyModule)) {
				String name = getModuleName(artifact);
				if (name != null) {
					modules.add(name);
				}
			}
		}
		if (jvm.getModules().getLocales() != null) {
			modules.add("jdk.localedata");
		}

		return modules;
	}

	private List<Artifact> getArtifacts(String artifactPattern) {
		List<Artifact> artifacts = new ArrayList<>();
		artifacts.add(mavenProject.getArtifact());
		artifacts.addAll(mavenProject.getArtifacts());

		List<ArtifactFilter> filters = new ArrayList<>();
		if (artifactPattern != null) {
			filters.add(new PatternIncludesArtifactFilter(List.of(artifactPattern.split(","))));
		}
		if (app.getDependencyExcludes() != null) {
			filters.add(new PatternExcludesArtifactFilter(List.of(app.getDependencyExcludes().split(","))));
		}
		ArtifactFilter filter = new AndArtifactFilter(filters);
		artifacts.removeIf(a -> !filter.include(a));
		return artifacts;
	}

	private String getModuleName(Artifact artifact) {
		return ModuleFinder.of(artifact.getFile().toPath()).findAll().stream()
				.map(ref -> ref.descriptor().name())
				.findFirst().orElse(null);
	}

	private ExecutionEnvironment createExecutionEnvironment() {
		return executionEnvironment(
				mavenProject,
				mavenSession,
				pluginManager
		);
	}

	private Plugin createJlinkPlugin() {
		return plugin(
				groupId("com.igormaznitsa"),
				artifactId("mvn-jlink-wrapper"),
				version("1.2.4")
		);
	}

	private Set<String> runJdeps(String artifactCoordinate) throws MojoExecutionException {
		Path tempDir = outputDirectory.resolve("jdeps");

		try {
			// Copy all artifacts to temporary directory
			if (Files.exists(tempDir)) {
				FileUtils.deleteDirectory(tempDir.toFile());
			}
			Files.createDirectories(tempDir);
			List<Artifact> allArtifacts = getArtifacts(null);
			for (Artifact artifact : allArtifacts) {
				Files.copy(artifact.getFile().toPath(), tempDir.resolve(artifact.getFile().getName()));
			}
			Artifact mainArtifact = getArtifacts(artifactCoordinate).stream().findFirst().orElse(allArtifacts.get(0));

			String outputDir = "${project.build.directory}/nativestart";
			ExecutionEnvironment executionEnvironment = createExecutionEnvironment();
			Plugin jlinkPlugin = createJlinkPlugin();
			executeMojo(
					jlinkPlugin,
					goal("jdeps"),
					configuration(
							element(name("output"), outputDir + "/jdeps.txt"),
							element(name("options"),
									element(name("option"), "--multi-release"),
									element(name("option"), jvm.getFeatureVersion()),
									element(name("option"), "--ignore-missing-deps"),
									element(name("option"), "--print-module-deps"),
									element(name("option"), "-cp"),
									element(name("option"), outputDir + "/jdeps/*"),
									element(name("option"), outputDir + "/jdeps/" + mainArtifact.getFile().getName())
							)
					),
					executionEnvironment
			);
			Path modulesFile = outputDirectory.resolve("jdeps.txt");
			Set<String> modules = Set.of(Files.readString(modulesFile).trim().split("\\s*,\\s*"));
			Files.delete(modulesFile);

			FileUtils.deleteDirectory(tempDir.toFile());
			return modules;
		} catch (IOException e) {
			throw new MojoExecutionException(e);
		}
	}

	private String dir(String directory) {
		if (directory.endsWith("/")) {
			return directory;
		} else {
			return directory + "/";
		}
	}

	private Component packJdk(Set<String> modules) throws MojoExecutionException {
		String jdkName = String.join("-", List.of("jvm", jvm.getVersion(), jvm.getOs(), jvm.getArch()));

		Path jdkCacheBaseDir = cacheDirectory.resolve("jdk");
		Path jdkCache;
		String descriptor;
		try {
			MessageDigest digest = MessageDigest.getInstance("SHA-1");
			descriptor = String.join(",", modules) + "\n" + jvm.getModules().getLocales();
			byte[] hash = digest.digest(descriptor.getBytes(StandardCharsets.UTF_8));
			HexFormat hex = HexFormat.of();

			String cache = String.join("-", List.of("jvm", jvm.getVersion(), jvm.getOs(), jvm.getArch(), hex.formatHex(hash)));

			jdkCache = jdkCacheBaseDir.resolve(cache);
		} catch (Exception e) {
			throw new MojoExecutionException(e);
		}
		app.setJvm(fillDefaults(app.getJvm(), "runtime/" + jdkName, "runtime/"));

		File compressed = jdkCache.resolve("jdk.tar" + compressionAlgorithm.getFileExtension()).toFile();
		Component result;
		if (!Files.exists(jdkCache)) {
			ExecutionEnvironment executionEnvironment = createExecutionEnvironment();
			Plugin jlinkPlugin = createJlinkPlugin();
			executeMojo(
					jlinkPlugin,
					goal("cache-jdk"),
					configuration(
							element(name("jdkPathProperty"), "jlink.jdk.path"),
							element(name("jdkCachePath"), jdkCacheBaseDir.toString()),
							element(name("provider"), "ADOPTIUM_API"),
							element(name("providerConfig"),
									element(name("releaseName"), "jdk-" + jvm.getVersion()),
									element(name("arch"), jvm.getArch()),
									element(name("os"), jvm.getOs())
							)
					),
					executionEnvironment
			);
			executeMojo(
					jlinkPlugin,
					goal("cache-jdk"),
					configuration(
							element(name("jdkPathProperty"), "jlink.jmods.path"),
							element(name("jdkCachePath"), jdkCacheBaseDir.toString()),
							element(name("provider"), "ADOPTIUM_API"),
							element(name("providerConfig"),
									element(name("releaseName"), "jdk-" + jvm.getVersion()),
									element(name("imageType"), "jmods"),
									element(name("arch"), jvm.getArch()),
									element(name("os"), jvm.getOs())
							)
					),
					executionEnvironment
			);
			Path jmodDirectory = outputDirectory.resolve("jmods");
			List<String> modulePaths = new ArrayList<>();
			modulePaths.add("${jlink.jmods.path}");
			for (String jmodPath : jvm.getModules().getJmods()) {
				if (jmodPath.startsWith("https://")) {
					download(jmodPath, jmodDirectory);
					if (!modulePaths.contains(jmodDirectory.toString())) {
						modulePaths.add(jmodDirectory.toString());
					}
				} else {
					modulePaths.add(jmodPath);
				}
			}

			List<String> options = new ArrayList<>();
			options.add("--compress=1");
			options.add("--no-header-files");
			options.add("--no-man-pages");
			if (jvm.getModules().getLocales() != null) {
				options.add("--include-locales=" + jvm.getModules().getLocales());
			}
			executeMojo(
					jlinkPlugin,
					goal("jlink"),
					configuration(
							//element(name("jdepsReportPath"), outputDir + "/jdeps.out"),
							element(name("output"), outputDirectory.resolve(jdkName).toString()),
							element(name("modulePaths"),
									modulePaths.stream().map(path -> element(name("modulePath"), path)).toArray(Element[]::new)
							),
							element(name("addModules"),
									modules.stream().map(mod -> element(name("module"), mod)).toArray(Element[]::new)
							),
							element(name("options"),
									options.stream().map(mod -> element(name("option"), mod)).toArray(Element[]::new)
							)
					),
					executionEnvironment
			);
			try {
				//Files.delete(outputPath.resolve("jdeps.out"));

				if (Files.exists(jmodDirectory)) {
					FileUtils.deleteDirectory(jmodDirectory.toFile());
				}

				Files.createDirectories(jdkCache);
				HashUtils.Info hashInfo = HashUtils.hash(HashAlgorithm.BLAKE3, outputDirectory.resolve(jdkName).toFile(), false);
				CompressUtils.compress(outputDirectory.resolve(jdkName).toFile(), compressed, compressionAlgorithm, compressionLevel);

				xyz.wismer.nativestart.packer.manifest.Component component = new xyz.wismer.nativestart.packer.manifest.Component(jdkName, hashInfo.getSize(), hashInfo.getHash(), null);
				component.setDownloadSize(compressed.length());
				Files.writeString(jdkCache.resolve("artifact.toml"), component.toToml());
				Files.writeString(jdkCache.resolve("modules.txt"), descriptor);

				result = new Component(app.getJvm().getDistribution() + ".tar" + compressionAlgorithm.getFileExtension(), compressed.length(), hashInfo.getHash(), hashInfo.getSize(), app.getJvm().getTarget());
			} catch (IOException e) {
				try {
					FileUtils.deleteDirectory(jdkCache.toFile());
				} catch (IOException ex) {
					getLog().warn("Could not cleanup temporary directory " + compressed);
				}
				throw new MojoExecutionException(e);
			} finally {
				try {
					FileUtils.deleteDirectory(outputDirectory.resolve(jdkName).toFile());
				} catch (IOException ex) {
					getLog().warn("Could not cleanup temporary directory " + compressed);
				}
			}
		}
		else {
			try {
				String toml = Files.readString(jdkCache.resolve("artifact.toml"));
				xyz.wismer.nativestart.packer.manifest.Component component = xyz.wismer.nativestart.packer.manifest.Component.fromToml(toml);
				result = new Component(app.getJvm().getDistribution() + ".tar" + compressionAlgorithm.getFileExtension(),
						component.getDownloadSize(), component.getChecksum(), component.getSize(), app.getJvm().getTarget());
			} catch (IOException e) {
				throw new MojoExecutionException(e);
			}
		}
		try {
			Path targetPath = outputDirectory.resolve(result.getRemotePath());
			Files.createDirectories(targetPath.getParent());
			Files.copy(compressed.toPath(), targetPath, StandardCopyOption.REPLACE_EXISTING);
		} catch (IOException e) {
			throw new MojoExecutionException(e);
		}
		return result;
	}

	private void download(String url, Path directory) throws MojoExecutionException {
		if (!Files.exists(directory)) {
			try {
				Files.createDirectories(directory);
			} catch (IOException e) {
				throw new MojoExecutionException(e);
			}
		}
		executeMojo(
				plugin(
						groupId("io.github.download-maven-plugin"),
						artifactId("download-maven-plugin"),
						version("2.1.0")
				),
				goal("wget"),
				configuration(
						element(name("uri"), url),
						element(name("unpack"), "true"),
						element(name("outputDirectory"), directory.toString()),
						element(name("fileMappers"),
								element("fileMapper", attribute("implementation",
										"org.codehaus.plexus.components.io.filemappers.FlattenFileMapper"))
						)
				),
				createExecutionEnvironment()
		);
	}

	private Locations fillDefaults(Locations locations, String distribution, String target) {
		if (locations == null) {
			locations = new Locations();
		}
		if (locations.getDistribution() == null) {
			locations.setDistribution(distribution);
		}
		if (locations.getTarget() == null) {
			locations.setTarget(target);
		}
		return locations;
	}
}
