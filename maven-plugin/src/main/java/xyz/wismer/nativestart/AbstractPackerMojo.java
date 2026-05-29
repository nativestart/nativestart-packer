package xyz.wismer.nativestart;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugins.annotations.Parameter;
import xyz.wismer.nativestart.config.App;
import xyz.wismer.nativestart.config.Jvm;
import xyz.wismer.nativestart.config.KeyInfo;

import java.nio.file.Path;

/**
 * Abstract base class for common Maven plugin parameters.
 */
public abstract class AbstractPackerMojo extends AbstractMojo {
	/**
	 * JVM related configuration parameters.
	 */
	@Parameter(required = true)
	protected Jvm jvm;

	/**
	 * Application related configuration parameters.
	 */
	@Parameter(required = true)
	protected App app;

	/**
	 * Configuration parameters for signing and verifying the descriptor.
	 */
	@Parameter
	protected KeyInfo sign;

	/**
	 * The output directory for the generated files.
	 */
	@Parameter(defaultValue = "${project.build.directory}/nativestart")
	protected Path outputDirectory;
}
