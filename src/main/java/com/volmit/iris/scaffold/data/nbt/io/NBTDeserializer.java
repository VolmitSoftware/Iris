package com.volmit.iris.scaffold.data.nbt.io;

import com.volmit.iris.scaffold.data.io.Deserializer;
import com.volmit.iris.scaffold.data.nbt.tag.Tag;

import java.io.IOException;
import java.io.InputStream;
import java.util.zip.GZIPInputStream;

public class NBTDeserializer implements Deserializer<NamedTag> {

    private final boolean compressed;

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
