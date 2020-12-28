package net.querz.mca;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;
import java.util.zip.InflaterInputStream;

public enum CompressionType {

	NONE(0, t -> t, t -> t),
	GZIP(1, GZIPOutputStream::new, GZIPInputStream::new),
	ZLIB(2, DeflaterOutputStream::new, InflaterInputStream::new);

	private byte id;
	private ExceptionFunction<OutputStream, ? extends OutputStream, IOException> compressor;
	private ExceptionFunction<InputStream, ? extends InputStream, IOException> decompressor;

	CompressionType(int id,
					ExceptionFunction<OutputStream, ? extends OutputStream, IOException> compressor,
					ExceptionFunction<InputStream, ? extends InputStream, IOException> decompressor) {
		this.id = (byte) id;
		this.compressor = compressor;
		this.decompressor = decompressor;
	}

	public byte getID() {
		return id;
	}

	public OutputStream compress(OutputStream out) throws IOException {
		return compressor.accept(out);
	}

	public InputStream decompress(InputStream in) throws IOException {
		return decompressor.accept(in);
	}

	public static CompressionType getFromID(byte id) {
		for (CompressionType c : CompressionType.values()) {
			if (c.id == id) {
				return c;
			}
		}
		return null;
	}
}
