package xyz.wismer.nativestart.packer;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.security.PrivateKey;

/**
 * A builder for descriptors including the application resources.
 */
public interface DescriptorBuilder {

	/**
	 * Set the splash screen.
	 * @param folder the folder containing all resources for the splash screen
	 * @param downloadPath the relative path to download the resources. The file extension can be omitted and will be
	 *                     determined by the compression method
	 * @param installationPath the relative path to the base installation folder where to put the splash resources
	 * @return this instance
	 */
	DescriptorBuilder splash(File folder, String downloadPath, String installationPath) throws IOException;

	/**
	 * Set the JVM.
	 * @param folder the folder containing all resources for the JVM
	 * @param downloadPath the relative path to download the resources. The file extension can be omitted and will be
	 *                     determined by the compression method
	 * @param installationPath the relative path to the base installation folder where to put the JVM
	 * @return this instance
	 */
	DescriptorBuilder jvm(File folder, String downloadPath, String installationPath) throws IOException;

	/**
	 * Set the main class of the application.
	 * @param mainClass the fully qualified name of the class
	 * @return this instance
	 */
	DescriptorBuilder main(String mainClass);

	/**
	 * Set a system property for the JVM.
	 * @param property name of the system property
	 * @param value the value to assign
	 * @return this instance
	 */
	default DescriptorBuilder systemProperty(String property, String value) {
		return option("-D" + property + "=" + value);
	}

	/**
	 * Set a JVM option.
	 * @param option the JVM command line option
	 * @return this instance
	 */
	DescriptorBuilder option(String option);

	default DescriptorBuilder library(File file) throws IOException {
		return library(file, file.getName());
	}

	default DescriptorBuilder library(File file, String installationPath) throws IOException {
		return library(file, null, installationPath);
	}

	/**
	 * Add a library file.
	 * @param file the library file
	 * @param downloadPath the relative path to download the file, including the extension (e.g. ".jar")
	 * @param installationPath the relative path to the base installation folder where to put the library
	 * @return this instance
	 */
	DescriptorBuilder library(File file, String downloadPath, String installationPath) throws IOException;

	default DescriptorBuilder resource(File file) throws IOException {
		return resource(file, file.getName());
	}

	default DescriptorBuilder resource(File file, String installationPath) throws IOException {
		return resource(file, null, installationPath);
	}

	/**
	 * Add a generic resource file.
	 * @param file the resource file
	 * @param downloadPath the relative path to download the file, including the extension
	 * @param installationPath the relative path to the base installation folder where to put the resource file
	 * @return this instance
	 */
	DescriptorBuilder resource(File file, String downloadPath, String installationPath) throws IOException;

	/**
	 * Mark a folder in the installation as "unmanaged", so the launcher will not delete any files from there
	 * @param installationPath the relative path to the base installation folder
	 * @return this instance
	 */
	DescriptorBuilder unmanaged(String installationPath);

	/**
	 * Set the compression to use for the JVM and splash screen resources
	 * @param algorithm the compression algorithm
	 * @param level the level of compression, depending on the chosen algorithm
	 */
	DescriptorBuilder compression(CompressionAlgorithm algorithm, int level);

	/**
	 * Generate the descriptor file and all resources to be served.
	 * @param targetDirectory the folder where to generate the files
	 * @param baseURL the base URL where the files will be served
	 * @param signatureKey the key to sign the descriptor or null
	 */
	void generate(File targetDirectory, URL baseURL, PrivateKey signatureKey) throws IOException;

}
