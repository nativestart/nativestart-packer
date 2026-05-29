package xyz.wismer.nativestart.packer.util;

import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.apache.commons.compress.archivers.tar.TarConstants;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream;
import org.apache.commons.compress.archivers.zip.ZipFile;
import org.apache.commons.compress.compressors.xz.XZCompressorOutputStream;
import org.apache.commons.compress.compressors.zstandard.ZstdCompressorOutputStream;
import org.apache.commons.io.IOUtils;
import xyz.wismer.nativestart.packer.CompressionAlgorithm;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.util.Enumeration;
import java.util.zip.ZipEntry;

public class CompressUtils {

	public static void compress(File file, File target, CompressionAlgorithm algorithm, int level) throws IOException {
		if (file.isFile()) {
			try (OutputStream cos = compressedStream(Files.newOutputStream(target.toPath()), algorithm, level)) {
				if (file.getName().endsWith(".jar")) {
					recompressJar(file, cos);
				} else {
					try (FileInputStream in = new FileInputStream(file)) {
						IOUtils.copy(in, cos);
					}
				}
			}
		} else if (file.isDirectory()) {
			try (TarArchiveOutputStream taos = new TarArchiveOutputStream(
					compressedStream(Files.newOutputStream(target.toPath()), algorithm, level))) {
				taos.setLongFileMode(TarArchiveOutputStream.LONGFILE_POSIX);
				addToArchive(taos, file, null);
			}
		} else {
			throw new UnsupportedOperationException("Compressing " + file + " is not supported");
		}
	}

	private static OutputStream compressedStream(OutputStream out, CompressionAlgorithm algorithm, int level)
			throws IOException {
		switch (algorithm) {
			case XZ: return new XZCompressorOutputStream(out, level);
			case ZSTD: return new ZstdCompressorOutputStream(out, level);
		}
		throw new IllegalArgumentException("Unknown compression algorithm");
	}

	// based on https://memorynotfound.com/java-tar-example-compress-decompress-tar-tar-gz-files/
	private static void addToArchive(TarArchiveOutputStream out, File file, String dir) throws IOException {
		String entry;
		if (dir == null) {
			entry = "";
		} else if (dir.isEmpty()) {
			entry = file.getName();
		} else {
			entry = dir + "/" + file.getName();
		}
		if (file.isFile()) {
			if (Files.isSymbolicLink(file.toPath())) {
				TarArchiveEntry symLinkEntry = new TarArchiveEntry(entry, TarConstants.LF_SYMLINK);
				symLinkEntry.setLinkName(Files.readSymbolicLink(file.toPath()).toString());
				symLinkEntry.setUserId(0);
				symLinkEntry.setGroupId(0);
				out.putArchiveEntry(symLinkEntry);
			} else {
				TarArchiveEntry tarEntry = new TarArchiveEntry(file, entry);
				tarEntry.setUserId(0);
				tarEntry.setGroupId(0);
				out.putArchiveEntry(tarEntry);
				try (FileInputStream in = new FileInputStream(file)) {
					IOUtils.copy(in, out);
				}
				out.closeArchiveEntry();
			}
		} else if (file.isDirectory()) {
			File[] children = file.listFiles();
			if (children != null) {
				for (File child : children) {
					addToArchive(out, child, entry);
				}
			}
		} else {
			throw new UnsupportedOperationException(file + " is not supported");
		}
	}

	static void recompressJar(File file, OutputStream out) throws IOException {
		ZipArchiveOutputStream zipStream = new ZipArchiveOutputStream(out);
		try (ZipFile zipFile = ZipFile.builder().setFile(file).get()) {
			Enumeration<ZipArchiveEntry> entries = zipFile.getEntries();
			while (entries.hasMoreElements()) {
				ZipArchiveEntry zipEntry = entries.nextElement();
				final ZipArchiveEntry archiveEntry = new ZipArchiveEntry(zipEntry);
				archiveEntry.setMethod(ZipEntry.STORED);
				zipStream.putArchiveEntry(archiveEntry);
				if (zipEntry.getSize() > 0) {
					try (InputStream in = zipFile.getInputStream(zipEntry)) {
						IOUtils.copy(in, zipStream);
					}
				}
				zipStream.closeArchiveEntry();
			}
		}
		zipStream.finish();
	}
}
