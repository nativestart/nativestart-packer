package xyz.wismer.nativestart.packer;

import java.io.File;

public class Component {
	/**
	 * The file or folder on the host building the component.
	 * Can be null for components that are already present in the remote repository.
	 */
	private File localSource;


	/**
	 * The absolute (including URL schema) or relative path to download this component.
	 * The file extension can be omitted if component gets compressed and will be determined by the compression method
	 */
	private String remotePath;

	/**
	 * The size in bytes of this component when downloading it.
	 */
	private long remoteSize;

	/**
	 * The checksum of this component in hex notation.
	 */
	private String installationChecksum;


	/**
	 * The size in bytes of this component when installed.
	 */
	private long installationSize;

	/**
	 * The relative path to the base installation folder where this component is installed.
	 */
	private String installationPath;

	/**
	 * The relative path to a folder for caches used by this component. Can be null.
	 */
	private String cachePath;

	public Component(File localSource, String remotePath, String installationPath) {
		this.localSource = localSource;
		this.remotePath = remotePath;
		this.installationPath = installationPath;
	}

	public Component(File localSource) {
		this.localSource = localSource;
		this.installationPath = localSource.getName();
	}

	public Component(String remotePath, long remoteSize, String installationChecksum, long installationSize, String installationPath) {
		this.remotePath = remotePath;
		this.remoteSize = remoteSize;
		this.installationChecksum = installationChecksum;
		this.installationSize = installationSize;
		this.installationPath = installationPath;
	}


	public File getLocalSource() {
		return localSource;
	}

	public void setLocalSource(File localSource) {
		this.localSource = localSource;
	}

	public String getRemotePath() {
		return remotePath;
	}

	public void setRemotePath(String remotePath) {
		this.remotePath = remotePath;
	}

	public long getRemoteSize() {
		return remoteSize;
	}

	public void setRemoteSize(long remoteSize) {
		this.remoteSize = remoteSize;
	}

	public String getInstallationChecksum() {
		return installationChecksum;
	}

	public void setInstallationChecksum(String installationChecksum) {
		this.installationChecksum = installationChecksum;
	}

	public long getInstallationSize() {
		return installationSize;
	}

	public void setInstallationSize(long installationSize) {
		this.installationSize = installationSize;
	}

	public String getInstallationPath() {
		return installationPath;
	}

	public void setInstallationPath(String installationPath) {
		this.installationPath = installationPath;
	}

	public String getCachePath() {
		return cachePath;
	}

	public void setCachePath(String cachePath) {
		this.cachePath = cachePath;
	}
}
