package xyz.wismer.nativestart.packer;

public enum OperatingSystem {
	WINDOWS(";", ".exe"),
	LINUX(":", ""),
	MAC(":", ".app");

	private final String pathSeparator;
	private final String executablePostfix;

	OperatingSystem(String pathSeparator, String executablePostfix) {
		this.pathSeparator = pathSeparator;
		this.executablePostfix = executablePostfix;
	}

	public String pathSeparator() {
		return pathSeparator;
	}

	public String getExecutablePostfix() {
		return executablePostfix;
	}

	public static OperatingSystem current() {
		String os = System.getProperty("os.name").toLowerCase();
		if (os.contains("win")) {
			return OperatingSystem.WINDOWS;
		} else if (os.contains("mac")) {
			return OperatingSystem.MAC;
		} else {
			return OperatingSystem.LINUX;
		}
	}
}
