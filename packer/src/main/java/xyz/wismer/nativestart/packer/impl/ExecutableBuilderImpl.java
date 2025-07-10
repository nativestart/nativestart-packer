package xyz.wismer.nativestart.packer.impl;

import com.kichik.pecoff4j.ImageDataDirectory;
import com.kichik.pecoff4j.PE;
import com.kichik.pecoff4j.ResourceDirectory;
import com.kichik.pecoff4j.ResourceDirectoryTable;
import com.kichik.pecoff4j.ResourceEntry;
import com.kichik.pecoff4j.SectionData;
import com.kichik.pecoff4j.SectionHeader;
import com.kichik.pecoff4j.SectionTable;
import com.kichik.pecoff4j.constant.ImageDataDirectoryType;
import com.kichik.pecoff4j.constant.ResourceType;
import com.kichik.pecoff4j.io.DataReader;
import com.kichik.pecoff4j.io.PEParser;
import com.kichik.pecoff4j.resources.FixedFileInfo;
import com.kichik.pecoff4j.resources.GroupIconDirectory;
import com.kichik.pecoff4j.resources.GroupIconDirectoryEntry;
import com.kichik.pecoff4j.resources.IconDirectoryEntry;
import com.kichik.pecoff4j.resources.IconImage;
import com.kichik.pecoff4j.resources.Manifest;
import com.kichik.pecoff4j.resources.StringFileInfo;
import com.kichik.pecoff4j.resources.StringPair;
import com.kichik.pecoff4j.resources.StringTable;
import com.kichik.pecoff4j.resources.Var;
import com.kichik.pecoff4j.resources.VarFileInfo;
import com.kichik.pecoff4j.resources.VersionInfo;
import com.kichik.pecoff4j.util.IconFile;
import com.kichik.pecoff4j.util.PaddingType;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.bouncycastle.crypto.params.Ed25519PublicKeyParameters;
import org.bouncycastle.crypto.util.PublicKeyFactory;
import xyz.wismer.nativestart.packer.ExecutableBuilder;
import xyz.wismer.nativestart.packer.OperatingSystem;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.security.PublicKey;
import java.util.ArrayList;
import java.util.List;

import static com.kichik.pecoff4j.util.Alignment.align;

public class ExecutableBuilderImpl implements ExecutableBuilder {
	private final String name;
	private final String url;
	private InputStream icon;
	private InputStream winManifest;
	private PublicKey key;

	public ExecutableBuilderImpl(String name, String url) {
		this.name = name;
		this.url = url;
	}

	@Override
	public void setWindowsIcon(InputStream icon) {
		this.icon = icon;
	}

	@Override
	public void setWindowsManifest(InputStream winManifest) {
		this.winManifest = winManifest;
	}

	@Override
	public void setKey(PublicKey key) {
		try {
			Ed25519PublicKeyParameters key1 = (Ed25519PublicKeyParameters) PublicKeyFactory.createKey(key.getEncoded());
			if (key1.getEncoded().length != 32) {
				throw new IllegalArgumentException("Key must be of 256 bit length");
			}
		} catch (IOException e) {
			throw new RuntimeException(e);
		}

		this.key = key;
	}

	@Override
	public void build(OperatingSystem os, OutputStream outputStream) throws IOException {
		InputStream input = getGenericInput(os, key != null);

		byte[] bytes;
		if (os == OperatingSystem.WINDOWS) {
			PE pe = PEParser.parse(input);
			addResourceSection(pe);

			ResourceDirectory directory = new ResourceDirectory();
			directory.setTable(createResourceDirectoryTable());

			if (icon != null) {
				IconFile iconFile = IconFile.read(new DataReader(icon));
				List<ResourceEntry> iconEntries = new ArrayList<>();
				for (IconDirectoryEntry entry : iconFile.getDirectory().getEntries()) {
					IconImage image = iconFile.getImage(entry);
					iconEntries.add(entry(iconEntries.size() + 1,
							directory(entry(2057, image.toByteArray()))));
				}
				directory.getEntries().add(entry(ResourceType.ICON,
						directory(iconEntries.toArray(new ResourceEntry[0]))));

				byte[] iconDirData = createIconDirectory(iconFile.getImages()).toByteArray();
				directory.getEntries().add(entry(ResourceType.GROUP_ICON,
						directory(entry(1, directory(entry(2057, iconDirData))))));
			}

			// add version info
			VersionInfo versionInfo = createVersionInfo(name, 1, 0);
			versionInfo.rebuild();
			directory.getEntries().add(entry(ResourceType.VERSION_INFO,
					directory(entry(1, directory(entry(2057, versionInfo.toByteArray()))))));

			// add manifest
			Manifest manifest = new Manifest();
			InputStream manifestInput = winManifest != null ? winManifest : getClass().getResourceAsStream("/windows/manifest.xml");
			manifest.set(IOUtils.toString(manifestInput, StandardCharsets.UTF_8));
			directory.getEntries().add(entry(ResourceType.MANIFEST,
					directory(entry(1, directory(entry(1033, manifest.toByteArray()))))));

			// we know that .rdata section is at second position
			customize(pe.getSectionTable().getSection(1).getData());

			pe.getImageData().setResourceTable(directory);
			pe.rebuild(PaddingType.PATTERN);
			bytes = pe.toByteArray();
		} else {
			bytes = IOUtils.toByteArray(input);
			customize(bytes);
		}

		outputStream.write(bytes);
	}

	private void customize(byte[] bytes) {
		byte[] nameGeneric = StringUtils.rightPad("APPLICATION_NAME", 64, " ").getBytes();
		byte[] urlGeneric = StringUtils.rightPad("APPLICATION_DESCRIPTOR_URL", 256, " ").getBytes();
		byte[] keyGeneric = "$REPLACE_APPLICATION_PUBLIC_KEY$".getBytes();

		byte[] nameSpecific = StringUtils.rightPad(name, 64, " ").getBytes();
		byte[] urlSpecific = StringUtils.rightPad(url, 256, " ").getBytes();

		replace(bytes, nameGeneric, nameSpecific);
		replace(bytes, urlGeneric, urlSpecific);
		if (key != null) {
			try {
				Ed25519PublicKeyParameters key1 = (Ed25519PublicKeyParameters) PublicKeyFactory.createKey(key.getEncoded());
				byte[] keySpecific = key1.getEncoded();
				replace(bytes, keyGeneric, keySpecific);
			} catch (IOException e) {
				throw new RuntimeException(e);
			}
		}
	}

	private InputStream getGenericInput(OperatingSystem os, boolean signSupport) {
		String executable = (signSupport ? "" : "un") + "signed" + os.getExecutablePostfix();
		return ExecutableBuilder.class.getResourceAsStream("/" + os.name().toLowerCase() + "/" + executable);
	}

	private void addResourceSection(PE pe) {
		ImageDataDirectory resTable = pe.getOptionalHeader().getDataDirectory(ImageDataDirectoryType.RESOURCE_TABLE);

		int maxVirtualAddress = maxVirtualAddress(pe);
		resTable.setVirtualAddress(maxVirtualAddress);
		int maxRawAddress = maxRawAddress(pe);

		SectionHeader resHeader = new SectionHeader();
		resHeader.setName(".rsrc");
		resHeader.setVirtualAddress(maxVirtualAddress);
		resHeader.setPointerToRawData(maxRawAddress);
		resHeader.setCharacteristics(1073741888);
		pe.getSectionTable().add(resHeader);

		SectionData resSection = new SectionData();
		pe.getSectionTable().put(pe.getSectionTable().getNumberOfSections() - 1, resSection);

		pe.getCoffHeader().setNumberOfSections(pe.getCoffHeader().getNumberOfSections() + 1);
	}

	private int maxVirtualAddress(PE pe) {
		SectionTable sectionTable = pe.getSectionTable();
		// find the highest address used in any section (virtual address space)
		int max = 0;
		for (int i = 0; i < sectionTable.getNumberOfSections(); i++) {
			SectionHeader header = sectionTable.getHeader(i);
			max = Math.max(max, header.getVirtualAddress() + header.getVirtualSize());
		}
		return align(max, pe.getOptionalHeader().getSectionAlignment());
	}

	private int maxRawAddress(PE pe) {
		SectionTable sectionTable = pe.getSectionTable();
		// find the highest raw data address used in any section
		int max = 0;
		for (int i = 0; i < sectionTable.getNumberOfSections(); i++) {
			SectionHeader header = sectionTable.getHeader(i);
			max = Math.max(max, header.getPointerToRawData() + header.getSizeOfRawData());
		}
		return align(max, pe.getOptionalHeader().getFileAlignment());
	}

	private ResourceDirectoryTable createResourceDirectoryTable() {
		ResourceDirectoryTable table = new ResourceDirectoryTable();
		table.setMajorVersion(4);
		return table;
	}

	private ResourceEntry entry(int id, ResourceDirectory directory) {
		ResourceEntry entry = new ResourceEntry();
		entry.setId(id);
		entry.setDirectory(directory);
		return entry;
	}

	private ResourceEntry entry(int id, byte[] data) {
		ResourceEntry entry = new ResourceEntry();
		entry.setId(id);
		entry.setCodePage(1252);
		entry.setData(data);
		return entry;
	}

	private ResourceDirectory directory(ResourceEntry... entries) {
		ResourceDirectory dir = new ResourceDirectory();
		ResourceDirectoryTable table = new ResourceDirectoryTable();
		table.setMajorVersion(4);
		dir.setTable(table);
		for (ResourceEntry entry : entries) {
			dir.getEntries().add(entry);
		}
		return dir;
	}

	private GroupIconDirectory createIconDirectory(IconImage[] icons) {
		GroupIconDirectory directory = new GroupIconDirectory();
		directory.setReserved(0);
		directory.setType(1);

		int id = 1;
		for (IconImage icon : icons) {
			GroupIconDirectoryEntry entry = new GroupIconDirectoryEntry();
			entry.setWidth(icon.getHeader() != null ? icon.getHeader().getWidth() : 0);
			entry.setHeight(icon.getHeader() != null ? icon.getHeader().getHeight() / 2 : 0);
			entry.setColorCount(0);
			entry.setReserved(0);
			entry.setPlanes(icon.getHeader() != null ? icon.getHeader().getPlanes() : 1);
			entry.setBitCount(icon.getHeader() != null ? icon.getHeader().getBitCount() : 32);
			entry.setBytesInRes(icon.sizeOf());
			entry.setId(id++);
			directory.getEntries().add(entry);
		}

		return directory;
	}

	private VersionInfo createVersionInfo(String productName, int majorVersion, int minorVersion) {
		VersionInfo versionInfo = new VersionInfo();
		versionInfo.setKey("VS_VERSION_INFO");

		FixedFileInfo fixedFileInfo = new FixedFileInfo();
		fixedFileInfo.setSignature(0xFEEF04BD);
		fixedFileInfo.setStrucVersion(1 << 16);
		fixedFileInfo.setFileVersionMS(majorVersion << 16 | minorVersion);
		fixedFileInfo.setProductVersionMS(majorVersion << 16 | minorVersion);
		fixedFileInfo.setFileFlagMask((1 << 5) - 1);
		fixedFileInfo.setFileFlags(0);
		fixedFileInfo.setFileOS(0x00040004); // Windows NT
		fixedFileInfo.setFileType(1); // application
		fixedFileInfo.setFileSubtype(0);
		// skip timestamp
		versionInfo.setFixedFileInfo(fixedFileInfo);

		versionInfo.setStringFileInfo(createStringFileInfo(productName, majorVersion, minorVersion));

		versionInfo.setVarFileInfo(createVarFileInfo());
		return versionInfo;
	}

	private StringFileInfo createStringFileInfo(String productName, int majorVersion, int minorVersion) {
		StringFileInfo stringFileInfo = new StringFileInfo();
		stringFileInfo.setKey("StringFileInfo");
		stringFileInfo.setType(1);

		StringTable stringTable = new StringTable();
		stringTable.setType(1);
		stringTable.setKey("000004b0");
		stringTable.getStrings().add(createStringPair("ProductVersion", majorVersion + "." + minorVersion + ".0"));
		stringTable.getStrings().add(createStringPair("ProductName", productName));
		stringTable.getStrings().add(createStringPair("FileVersion", majorVersion + "." + minorVersion + ".0"));

		stringFileInfo.getTables().add(stringTable);
		return stringFileInfo;
	}

	private StringPair createStringPair(String key, String value) {
		StringPair stringPair = new StringPair();
		stringPair.setType(1);
		stringPair.setKey(key);
		stringPair.setValue(value);
		return stringPair;
	}

	private VarFileInfo createVarFileInfo() {
		VarFileInfo varFileInfo = new VarFileInfo();
		varFileInfo.setKey("VarFileInfo");
		Var v = new Var();
		v.setType(0);
		v.setKey("Translation");
		v.getValues().add(1200 << 16); // code page 1200 is "utf-16"
		varFileInfo.getVars().add(v);
		return varFileInfo;
	}

	private static void replace(byte[] data, byte[] search, byte[] replace) {
		int pos = indexOf(data, search);
		System.arraycopy(replace, 0, data, pos, replace.length);
	}

	/**
	 * Copied from https://stackoverflow.com/a/25659067
	 * <p>
	 * Search the data byte array for the first occurrence
	 * of the byte array pattern.
	 */
	public static int indexOf(byte[] data, byte[] pattern) {
		int[] failure = computeFailure(pattern);

		int j = 0;

		for (int i = 0; i < data.length; i++) {
			while (j > 0 && pattern[j] != data[i]) {
				j = failure[j - 1];
			}
			if (pattern[j] == data[i]) {
				j++;
			}
			if (j == pattern.length) {
				return i - pattern.length + 1;
			}
		}
		return -1;
	}

	/**
	 * Computes the failure function using a boot-strapping process,
	 * where the pattern is matched against itself.
	 */
	private static int[] computeFailure(byte[] pattern) {
		int[] failure = new int[pattern.length];

		int j = 0;
		for (int i = 1; i < pattern.length; i++) {
			while (j > 0 && pattern[j] != pattern[i]) {
				j = failure[j - 1];
			}
			if (pattern[j] == pattern[i]) {
				j++;
			}
			failure[i] = j;
		}

		return failure;
	}
}
