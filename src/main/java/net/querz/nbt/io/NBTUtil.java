package net.querz.nbt.io;

import net.querz.nbt.tag.Tag;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PushbackInputStream;
import java.util.zip.GZIPInputStream;

public final class NBTUtil {

	private NBTUtil() {}

	public static void write(NamedTag tag, File file, boolean compressed) throws IOException {
		try (FileOutputStream fos = new FileOutputStream(file)) {
			new NBTSerializer(compressed).toStream(tag, fos);
		}
	}

	public static void write(NamedTag tag, String file, boolean compressed) throws IOException {
		write(tag, new File(file), compressed);
	}

	public static void write(NamedTag tag, File file) throws IOException {
		write(tag, file, true);
	}

	public static void write(NamedTag tag, String file) throws IOException {
		write(tag, new File(file), true);
	}

	public static void write(Tag<?> tag, File file, boolean compressed) throws IOException {
		write(new NamedTag(null, tag), file, compressed);
	}

	public static void write(Tag<?> tag, String file, boolean compressed) throws IOException {
		write(new NamedTag(null, tag), new File(file), compressed);
	}

	public static void write(Tag<?> tag, File file) throws IOException {
		write(new NamedTag(null, tag), file, true);
	}

	public static void write(Tag<?> tag, String file) throws IOException {
		write(new NamedTag(null, tag), new File(file), true);
	}

	public static NamedTag read(File file, boolean compressed) throws IOException {
		try (FileInputStream fis = new FileInputStream(file)) {
			return new NBTDeserializer(compressed).fromStream(fis);
		}
	}

	public static NamedTag read(String file, boolean compressed) throws IOException {
		return read(new File(file), compressed);
	}

	public static NamedTag read(File file) throws IOException {
		try (FileInputStream fis = new FileInputStream(file)) {
			return new NBTDeserializer(false).fromStream(detectDecompression(fis));
		}
	}

	public static NamedTag read(String file) throws IOException {
		return read(new File(file));
	}

	private static InputStream detectDecompression(InputStream is) throws IOException {
		PushbackInputStream pbis = new PushbackInputStream(is, 2);
		int signature = (pbis.read() & 0xFF) + (pbis.read() << 8);
		pbis.unread(signature >> 8);
		pbis.unread(signature & 0xFF);
		if (signature == GZIPInputStream.GZIP_MAGIC) {
			return new GZIPInputStream(pbis);
		}
		return pbis;
	}
}
