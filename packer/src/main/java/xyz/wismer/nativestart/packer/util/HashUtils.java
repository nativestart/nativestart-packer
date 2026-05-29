package xyz.wismer.nativestart.packer.util;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.function.FailableConsumer;
import org.bouncycastle.crypto.Digest;
import org.bouncycastle.crypto.digests.Blake3Digest;
import org.bouncycastle.crypto.digests.SHA256Digest;
import xyz.wismer.nativestart.packer.Architecture;
import xyz.wismer.nativestart.packer.HashAlgorithm;
import xyz.wismer.nativestart.packer.OperatingSystem;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.SignatureException;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;


public class HashUtils {
	private static Path hashUtilExecutable;

	public static Info hash(HashAlgorithm hashAlgorithm, File file, boolean recompressJars) throws IOException {
		if (recompressJars) {
			return hashRecompressedJar(out -> {
				CompressUtils.recompressJar(file, out);
				out.close();
			});
		} else if (file.isFile()) {
			try (InputStream stream = new BufferedInputStream(Files.newInputStream(file.toPath()))) {
				return new Info(file.length(), hashStream(hashAlgorithm, stream));
			}
		} else if (file.isDirectory()) {
			try (Stream<Path> paths = Files.walk(file.toPath())) {
				List<Path> files = paths.collect(Collectors.toList());
				long size = 0;
				Map<String, String> hashes = new TreeMap<>();
				for (Path f : files) {
					if (f.toFile().isFile()) {
						String filename = file.toPath().relativize(f).toString().replace("\\", "/");
						if (Files.isSymbolicLink(f)) {
							hashes.put(filename, hashStream(hashAlgorithm, new ByteArrayInputStream(
									Files.readSymbolicLink(f).toString().getBytes(StandardCharsets.UTF_8))));
						} else {
							size += f.toFile().length();
							hashes.put(filename, hash(hashAlgorithm, f.toFile(), false).getHash());
						}
					}
				}
				return new Info(size, hashIndex(hashAlgorithm, hashes));
			}
		} else if (!file.exists()) {
			throw new IOException(file.getAbsolutePath() + " does not exist");
		}
		throw new IOException("Only files and directories are supported");
	}

	private static Info hashRecompressedJar(FailableConsumer<OutputStream, IOException> jarProvider) throws IOException {
		unpackHashUtilExecutable();
		Process process = new ProcessBuilder(hashUtilExecutable.toAbsolutePath().toString()).start();
		jarProvider.accept(process.getOutputStream());
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		IOUtils.copy(process.getInputStream(), out);
		String[] lines = out.toString().split("\n");
		return new Info(Long.parseLong(lines[0]), lines[1]);
	}

	private static synchronized void unpackHashUtilExecutable() throws IOException {
		if (hashUtilExecutable == null) {
			OperatingSystem os = OperatingSystem.current();
			Architecture arch = Architecture.current();
			Path executable = Files.createTempFile("nativestart", os.getExecutablePostfix());
			String folder = "/" + os.name().toLowerCase() + "-" + arch.name().toLowerCase();
			Files.copy(CompressUtils.class.getResourceAsStream(folder + "/checksum"), executable, StandardCopyOption.REPLACE_EXISTING);
			executable.toFile().setExecutable(true);
			hashUtilExecutable = executable;
		}
	}

	private static String hashStream(HashAlgorithm hashAlgorithm, InputStream input) throws IOException {
		Digest digest = createDigest(hashAlgorithm);
		byte[] buffer = new byte[4 * 1024];
		int len;
		while (-1 != (len = input.read(buffer))) {
			digest.update(buffer, 0, len);
		}
		return toHex(digest);
	}

	private static String hashIndex(HashAlgorithm hashAlgorithm, Map<String, String> hashes) {
		Digest digest = createDigest(hashAlgorithm);
		for (Map.Entry<String, String> entry : hashes.entrySet()) {
			String line = entry.getKey() + "\t" + entry.getValue() + "\n";
			byte[] bytes = line.getBytes(StandardCharsets.UTF_8);
			digest.update(bytes, 0, bytes.length);
		}
		return toHex(digest);
	}

	private static Digest createDigest(HashAlgorithm hashAlgorithm) {
		if (hashAlgorithm == HashAlgorithm.BLAKE3) {
			return new Blake3Digest();
		}
		else {
			return new SHA256Digest();
		}
	}

	private static String toHex(Digest digest) {
		byte[] out = new byte[digest.getDigestSize()];
		digest.doFinal(out, 0);
		return toHex(out);
	}

	private static String toHex(byte[] data) {
		StringBuilder hexString = new StringBuilder(2 * data.length);
		for (byte datum : data) {
			String hex = Integer.toHexString(0xff & datum);
			if (hex.length() == 1) {
				hexString.append('0');
			}
			hexString.append(hex);
		}
		return hexString.toString();
	}

	public static class Info {
		private final long size;
		private final String hash;

		public Info(long size, String hash) {
			this.size = size;
			this.hash = hash;
		}

		public long getSize() {
			return size;
		}

		public String getHash() {
			return hash;
		}
	}

	public static String sign(byte[] data, PrivateKey privateKey) {
		try {
			Signature signature = Signature.getInstance("Ed25519");
			signature.initSign(privateKey);

			signature.update(data);
			byte[] digitalSignature = signature.sign();
			return toHex(digitalSignature).toLowerCase();
		} catch (NoSuchAlgorithmException | SignatureException | InvalidKeyException e) {
			throw new IllegalArgumentException("Ed25519 private key expected.");
		}
	}
}
