package xyz.wismer.nativestart.packer;

public enum Architecture {
	X86_64, AARCH64;

	public static Architecture current() {
		String arch = System.getProperty("os.arch").toLowerCase();
		if (arch.contains("aarch64")) {
			return Architecture.AARCH64;
		} else {
			return Architecture.X86_64;
		}
	}
}
