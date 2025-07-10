package xyz.wismer.nativestart.packer.impl;

public class Artifact {
	private String url;
	private long size;
	private Long downloadSize;
	private String checksum;
	private final String path;

	public Artifact(String url, long size, String checksum, String path) {
		this.url = url;
		this.size = size;
		this.checksum = checksum;
		this.path = path;
	}

	public String getUrl() {
		return url;
	}

	public void setUrl(String url) {
		this.url = url;
	}

	public long getSize() {
		return size;
	}

	public void setSize(long size) {
		this.size = size;
	}

	public void setDownloadSize(Long downloadSize) {
		this.downloadSize = downloadSize;
	}

	public Long getDownloadSize() {
		return downloadSize;
	}

	public String getChecksum() {
		return checksum;
	}

	public void setChecksum(String checksum) {
		this.checksum = checksum;
	}

	public String getPath() {
		return path;
	}

}
