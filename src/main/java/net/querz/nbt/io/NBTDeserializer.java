package net.querz.nbt.io;

import net.querz.io.Deserializer;
import net.querz.nbt.tag.Tag;
import java.io.IOException;
import java.io.InputStream;
import java.util.zip.GZIPInputStream;

public class NBTDeserializer implements Deserializer<NamedTag> {

	private boolean compressed;

	public NBTDeserializer() {
		this(true);
	}

	public NBTDeserializer(boolean compressed) {
		this.compressed = compressed;
	}

	@Override
	public NamedTag fromStream(InputStream stream) throws IOException {
		NBTInputStream nbtIn;
		if (compressed) {
			nbtIn = new NBTInputStream(new GZIPInputStream(stream));
		} else {
			nbtIn = new NBTInputStream(stream);
		}
		return nbtIn.readTag(Tag.DEFAULT_MAX_DEPTH);
	}
}
