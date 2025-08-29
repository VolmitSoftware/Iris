package com.volmit.iris.util.io;

import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;

public class CountingDataInputStream extends DataInputStream {
    private final Counter counter;

    private CountingDataInputStream(@NotNull InputStream in) {
        super(in);
        if (!(in instanceof Counter c))
            throw new IllegalArgumentException("Underlying stream must be a Counter");
        this.counter = c;
    }

    public static CountingDataInputStream wrap(@NotNull InputStream in) {
        return new CountingDataInputStream(new Counter(in));
    }

    public long count() {
        return counter.count;
    }

    public void skipTo(long target) throws IOException {
        skipNBytes(Math.max(target - counter.count, 0));
    }

    @RequiredArgsConstructor
    private static class Counter extends InputStream {
        private final InputStream in;
        private long count;
        private long mark = -1;
        private int markLimit = 0;

        @Override
        public int read() throws IOException {
            int i = in.read();
            if (i != -1) count(1);
            return i;
        }

        @Override
        public int read(byte @NotNull [] b, int off, int len) throws IOException {
            int i = in.read(b, off, len);
            if (i != -1) count(i);
            return i;
        }

        private void count(int i) {
            count = Math.addExact(count, i);
            if (mark == -1)
                return;

            markLimit -= i;
            if (markLimit <= 0)
                mark = -1;
        }

        @Override
        public boolean markSupported() {
            return in.markSupported();
        }

        @Override
        public synchronized void mark(int readlimit) {
            if (!in.markSupported()) return;
            in.mark(readlimit);
            if (readlimit <= 0) {
                mark = -1;
                markLimit = 0;
                return;
            }

            mark = count;
            markLimit = readlimit;
        }

        @Override
        public synchronized void reset() throws IOException {
            in.reset();
            count = mark;
        }

        @Override
        public void close() throws IOException {
            in.close();
        }
    }
}
