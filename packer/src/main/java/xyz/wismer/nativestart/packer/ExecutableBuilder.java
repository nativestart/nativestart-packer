package xyz.wismer.nativestart.packer;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.PublicKey;

public interface ExecutableBuilder {
	void setWindowsIcon(InputStream icon);

	void setWindowsManifest(InputStream winManifest);

	void setKey(PublicKey key);

	void build(OperatingSystem os, OutputStream outputStream) throws IOException;
}
