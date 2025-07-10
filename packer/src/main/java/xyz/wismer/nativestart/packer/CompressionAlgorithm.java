package xyz.wismer.nativestart.packer;

public enum CompressionAlgorithm {
	@Deprecated XZ, ZSTD;

	public String getFileExtension() {
		return ".tar." + name().toLowerCase();
	}
}
