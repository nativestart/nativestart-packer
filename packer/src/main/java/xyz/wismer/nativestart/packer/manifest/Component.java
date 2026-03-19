package xyz.wismer.nativestart.packer.manifest;

import org.apache.commons.lang3.StringUtils;

import java.io.IOException;
import java.io.StringReader;
import java.util.Properties;

public class Component {
	private String url;
	private long size;
	private Long downloadSize;
	private String checksum;
	private final String path;
	private String cachePath;

	public Component(String url, long size, String checksum, String path) {
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

	public void setCachePath(String cachePath) {
		this.cachePath = cachePath;
	}

	public String getCachePath() {
		return cachePath;
	}

	public void append(Toml toml) {
		toml.append("url", url);
		toml.append("size", size);
		if (downloadSize != null) {
			toml.append("download_size", downloadSize);
		}
		toml.append("checksum", checksum);
		toml.append("path", path);
		toml.append("cache_path", cachePath);
	}

	public String toToml() {
		Toml toml = new Toml();
		append(toml);
		return toml.toString();
	}

	public static Component fromToml(String toml) {
		// Properties are enough for the TOML subset used here
		Properties properties = new Properties();
		try {
			properties.load(new StringReader(toml));
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
		String url = removeQuotes(properties.getProperty("url"));
		String size = properties.getProperty("size");
		String downloadSize = properties.getProperty("download_size");
		String checksum = removeQuotes(properties.getProperty("checksum"));
		String path = removeQuotes(properties.getProperty("path"));
		String cachePath = removeQuotes(properties.getProperty("cache_path"));
		Component component = new Component(url, Long.parseLong(size), checksum, path);
		if (downloadSize != null) {
			component.setDownloadSize(Long.parseLong(downloadSize));
		}
		if (cachePath != null) {
			component.setCachePath(cachePath);
		}
		return component;
	}

	private static String removeQuotes(String input) {
		if (input == null) {
			return null;
		}
		return StringUtils.removeStart(StringUtils.removeEnd(input, "\""), '"');
	}
}
