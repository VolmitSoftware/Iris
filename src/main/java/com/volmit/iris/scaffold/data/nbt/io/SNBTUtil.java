package com.volmit.iris.scaffold.data.nbt.io;

import com.volmit.iris.scaffold.data.nbt.tag.Tag;

import java.io.IOException;

public class SNBTUtil {

	public static String toSNBT(Tag<?> tag) throws IOException {
		return new SNBTSerializer().toString(tag);
	}

	public static Tag<?> fromSNBT(String string) throws IOException {
		return new SNBTDeserializer().fromString(string);
	}
}
