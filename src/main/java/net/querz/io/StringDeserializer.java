package net.querz.io;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.StringReader;

public interface StringDeserializer<T> extends Deserializer<T> {

	T fromReader(Reader reader) throws IOException;

	default T fromString(String s) throws IOException {
		return fromReader(new StringReader(s));
	}

	@Override
	default T fromStream(InputStream stream) throws IOException {
		try (Reader reader = new InputStreamReader(stream)) {
			return fromReader(reader);
		}
	}

	@Override
	default T fromFile(File file) throws IOException {
		try (Reader reader = new FileReader(file)) {
			return fromReader(reader);
		}
	}

	@Override
	default T fromBytes(byte[] data) throws IOException {
		return fromReader(new StringReader(new String(data)));
	}
}
