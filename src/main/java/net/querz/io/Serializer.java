package net.querz.io;

import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;

public interface Serializer<T> {

	void toStream(T object, OutputStream out) throws IOException;

	default void toFile(T object, File file) throws IOException {
		try (BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream(file))) {
			toStream(object, bos);
		}
	}

	default byte[] toBytes(T object) throws IOException {
		ByteArrayOutputStream bos = new ByteArrayOutputStream();
		toStream(object, bos);
		bos.close();
		return bos.toByteArray();
	}
}
