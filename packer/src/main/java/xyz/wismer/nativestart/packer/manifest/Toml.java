package xyz.wismer.nativestart.packer.manifest;

import java.util.List;

public class Toml {
	private final String LF = "\n";
	private final StringBuilder toml = new StringBuilder();

	public void append(String key, String value) {
		if (value != null) {
			toml.append(key).append(" = \"").append(value).append("\"").append(LF);
		}
	}

	public void append(String key, List<String> values) {
		toml.append(key).append(" = [").append(LF);
		for (String value : values) {
			toml.append("  \"").append(value).append("\",").append(LF);
		}
		toml.append("]").append(LF);
	}

	public void append(String key, long value) {
		toml.append(key).append(" = ").append(value).append(LF);
	}

	public void appendTable(String key) {
		toml.append(LF);
		toml.append("[").append(key).append("]").append(LF);
	}

	public void appendArrayOfTable(String key) {
		toml.append(LF);
		toml.append("[[").append(key).append("]]").append(LF);
	}

	@Override
	public String toString() {
		return toml.toString();
	}
}
