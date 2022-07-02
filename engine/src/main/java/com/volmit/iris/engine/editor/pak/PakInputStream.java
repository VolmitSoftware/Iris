package com.volmit.iris.engine.editor.pak;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;

public class PakInputStream extends InputStream {
    private final File folder;
    private final String name;
    private final int pakSize;
    private int read;
    private int currentPakNumber;
    private InputStream currentPakStream;

    public PakInputStream(File folder, String name, int pakSize) throws IOException {
        this.pakSize = pakSize;
        this.folder = folder;
        this.name = name;
        this.read = 0;
        this.currentPakNumber = 0;
        this.currentPakStream = readPakFile(currentPakNumber);
    }

    private InputStream readPakFile(int number) throws IOException {
        File f = new File(folder, name + number + ".pak");

        if(!f.exists()) {
            return null;
        }

        return new FileInputStream(f);
    }

    @Override
    public int read() throws IOException {
        if(currentPakStream == null) {
            return -1;
        }

        if(read++ == pakSize) {
            currentPakStream.close();
            currentPakStream = readPakFile(++currentPakNumber);

            if(currentPakStream == null)
            {
                return -1;
            }

            read = 1;
        }

        return currentPakStream.read();
    }

    public void close() throws IOException {
        if(currentPakStream != null) {
            currentPakStream.close();
        }
    }
}
