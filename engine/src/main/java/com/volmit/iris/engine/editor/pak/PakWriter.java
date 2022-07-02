package com.volmit.iris.engine.editor.pak;

import art.arcane.amulet.io.nbt.nbt.io.NBTUtil;
import art.arcane.amulet.io.nbt.nbt.io.NamedTag;
import art.arcane.amulet.io.nbt.objects.NBTObjectSerializer;
import art.arcane.amulet.io.nbt.objects.UnserializableClassException;

import java.io.DataOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import static art.arcane.amulet.MagicalSugar.*;

public class PakWriter {
    private final List<PakResourceInput> resources;
    private final File folder;
    private final PakOutputStream output;
    private final String name;
    private final long pakSize;

    public PakWriter(File folder, String name, long pakSize) throws IOException {
        folder.mkdirs();
        this.name = name;
        this.folder = folder;
        this.pakSize = pakSize;
        output = new PakOutputStream(folder, name, pakSize);
        resources = new ArrayList<>();
    }

    public PakWriter(File folder, String name) throws IOException {
        this(folder, name, 1LMB);
    }

    public void write() throws IOException, UnserializableClassException, IllegalAccessException {
        PakMetadata.PakMetadataBuilder meta = PakMetadata.builder().namespace(name).pakSize(pakSize);
        long totalWritten = 0;

        for(PakResourceInput i : resources) {
            long written = i.write(output);
            meta.resource(PakResourceMetadata.builder()
                .key(i.getKey().toString())
                .type(i.getType().getCanonicalName())
                .start(totalWritten)
                .length(written)
                .build());
            totalWritten += written;
        }

        NBTUtil.write(new NamedTag("Package " + name, NBTObjectSerializer.serialize(meta.build())), new File(folder, name + ".dat"), true);
        output.close();
    }

    public PakWriter resource(PakResourceInput input)
    {
        resources.add(input);
        return this;
    }
}
