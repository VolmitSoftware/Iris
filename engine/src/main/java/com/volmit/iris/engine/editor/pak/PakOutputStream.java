package com.volmit.iris.engine.editor.pak;

import lombok.Getter;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

public class PakOutputStream extends OutputStream {
    @Getter
    private final File folder;
    @Getter
    private final String name;
    private OutputStream currentPakOutput;
    private int currentPakNumber;
    @Getter
    private final long pakSize;
    @Getter
    private int written;
    @Getter
    private final List<File> writtenFiles;

    public PakOutputStream(File folder, String name, long pakSize) throws IOException {
        folder.mkdirs();
        this.writtenFiles = new ArrayList<>();
        this.written = 0;
        this.name = name;
        this.currentPakNumber = 0;
        this.currentPakOutput = writePakFile(0);
        this.pakSize = pakSize;
        this.folder = folder;
    }

    private OutputStream writePakFile(int number) throws IOException {
        File f = new File(folder, name + number + ".pak");
        writtenFiles.add(f);
        return new FileOutputStream(f);
    }

    @Override
    public void write(int b) throws IOException {
        if(written++ == pakSize) {
            currentPakOutput.close();
            currentPakOutput = writePakFile(++currentPakNumber);
            written = 1;
        }

        currentPakOutput.write(b);
    }

    public void close() throws IOException {
        currentPakOutput.close();
    }
}
