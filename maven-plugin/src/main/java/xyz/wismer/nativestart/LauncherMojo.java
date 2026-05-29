package xyz.wismer.nativestart;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import xyz.wismer.nativestart.packer.Architecture;
import xyz.wismer.nativestart.packer.ExecutableBuilder;
import xyz.wismer.nativestart.packer.OperatingSystem;
import xyz.wismer.nativestart.packer.Packer;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.PublicKey;
import java.security.cert.Certificate;

/**
 * Mojo to create a customized native executable provided by NativeStart.
 * This includes setting the distribution URL, the certificate to verify the descriptor and an icon for Windows
 * executables.
 */
@Mojo(name = "launcher", defaultPhase = LifecyclePhase.PACKAGE)
public class LauncherMojo extends AbstractPackerMojo {

	/**
	 * The path to the ICO file used for the Windows executable.
	 */
	@Parameter
	private File icon;

	/**
	 * The URL for downloading the NativeStart descriptor.
	 */
	@Parameter(required = true)
	private String distributionURL;

	@Override
	public void execute() throws MojoExecutionException {
		OperatingSystem operatingSystem = OperatingSystem.valueOf(jvm.getOs().toUpperCase());
		Architecture architecture = Architecture.valueOf(jvm.getArch().toUpperCase());

		ExecutableBuilder executable = Packer.executableBuilder(app.getAppId(), distributionURL);

		if (sign != null) {
			try {
				KeyStore ks = KeyStore.getInstance(sign.getKeystore(), sign.getStorepass().toCharArray());
				Certificate certificate = ks.getCertificate(sign.getAlias());
				executable.setKey(certificate.getPublicKey());
			} catch (IOException | GeneralSecurityException e) {
				throw new MojoExecutionException("Error in signing configuration", e);
			}
		}

		try {
			Files.createDirectories(outputDirectory);
		} catch (IOException e) {
			throw new MojoExecutionException("Could not create output directory", e);
		}

		if (icon != null && operatingSystem == OperatingSystem.WINDOWS) {
			try {
				executable.setWindowsIcon(Files.newInputStream(icon.toPath()));
			} catch (IOException e) {
				throw new MojoExecutionException("Could not set icon", e);
			}
		}

		String filename = app.getAppId() + "-" + jvm.getOs() + "-" + jvm.getArch() + operatingSystem.getExecutablePostfix();
		Path outputFile = outputDirectory.resolve(filename);
		try (OutputStream outputStream = Files.newOutputStream(outputFile)) {
			executable.build(operatingSystem, architecture, outputStream);
		} catch (IOException e) {
			throw new MojoExecutionException("Error in customizing launcher", e);
		}
	}
}
