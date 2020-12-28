package net.querz.io;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;

public interface Deserializer<T> {

	T fromStream(InputStream stream) throws IOException;

	default T fromFile(File file) throws IOException {
		try (BufferedInputStream bis = new BufferedInputStream(new FileInputStream(file))) {
			return fromStream(bis);
		}
	}

	default T fromBytes(byte[] data) throws IOException {
		ByteArrayInputStream stream = new ByteArrayInputStream(data);
		return fromStream(stream);
	}

	default T fromResource(Class<?> clazz, String path) throws IOException {
		try (InputStream stream = clazz.getClassLoader().getResourceAsStream(path)) {
			if (stream == null) {
				throw new IOException("resource \"" + path + "\" not found");
			}
			return fromStream(stream);
		}
	}

	default T fromURL(URL url) throws IOException {
		try (InputStream stream = url.openStream()) {
			return fromStream(stream);
		}
	}


}
