package net.querz.io;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.StringWriter;
import java.io.Writer;

public interface StringSerializer<T> extends Serializer<T> {

	void toWriter(T object, Writer writer) throws IOException;

	default String toString(T object) throws IOException {
		Writer writer = new StringWriter();
		toWriter(object, writer);
		writer.flush();
		return writer.toString();
	}

	@Override
	default void toStream(T object, OutputStream stream) throws IOException {
		Writer writer = new OutputStreamWriter(stream);
		toWriter(object, writer);
		writer.flush();
	}

	@Override
	default void toFile(T object, File file) throws IOException {
		try (Writer writer = new FileWriter(file)) {
			toWriter(object, writer);
		}
	}
}
