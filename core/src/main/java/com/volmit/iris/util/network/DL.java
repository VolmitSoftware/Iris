/*
 * Iris is a World Generator for Minecraft Bukkit Servers
 * Copyright (c) 2022 Arcane Arts (Volmit Software)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.volmit.iris.util.network;

import com.volmit.iris.util.collection.KSet;
import com.volmit.iris.util.io.IO;
import com.volmit.iris.util.scheduling.ChronoLatch;

import java.io.*;
import java.net.URL;
import java.net.URLConnection;
import java.util.Arrays;

public abstract class DL {
    protected File d;
    protected URL u;
    protected ChronoLatch latch;
    protected KSet<DownloadFlag> flags;
    protected MeteredOutputStream o;
    protected DownloadState state;
    protected int timeout;
    protected long size;
    protected long start;
    protected long downloaded;
    protected long currentChunk;
    protected long lastChunk;
    protected long bps;
    protected int bufferSize;
    protected long lastPull;
    protected DownloadMonitor m;

    public DL(URL u, File d, DownloadFlag... downloadFlags) {
        this.d = d;
        this.u = u;
        size = -1;
        lastPull = -1;
        downloaded = 0;
        bufferSize = 8192 * 32;
        currentChunk = 0;
        lastChunk = -1;
        bps = -1;
        start = -1;
        timeout = 10000;
        state = DownloadState.NEW;
        flags = new KSet<>();
        latch = new ChronoLatch(500);

        flags.addAll(Arrays.asList(downloadFlags));
    }

    public void monitor(DownloadMonitor m) {
        this.m = m;
    }

    public void update() {
        if (m != null) {
            m.onUpdate(state, getProgress(), getElapsed(), getTimeLeft(), bps, getDiskBytesPerSecond(), size, downloaded, bufferSize, getBufferUse());
        }
    }

    public boolean hasFlag(DownloadFlag f) {
        return flags.contains(f);
    }

    public boolean isState(DownloadState s) {
        return state.equals(s);
    }

    protected void state(DownloadState s) {
        this.state = s;
        update();
    }

    public int getBufferSize() {
        return bufferSize;
    }

    public void start() throws IOException {
        if (!isState(DownloadState.NEW)) {
            throw new DownloadException("Cannot start download while " + state.toString());
        }

        state(DownloadState.STARTING);

        if (hasFlag(DownloadFlag.CALCULATE_SIZE)) {
            size = calculateSize();
        }

        start = System.currentTimeMillis();
        downloaded = 0;
        bps = 0;
        lastChunk = System.currentTimeMillis();
        o = new MeteredOutputStream(new FileOutputStream(d), 100);
        openStream();
        state(DownloadState.DOWNLOADING);
    }

    protected abstract long download() throws IOException;

    protected abstract void openStream() throws IOException;

    protected abstract void closeStream() throws IOException;

    public void downloadChunk() throws IOException {
        if (!isState(DownloadState.DOWNLOADING)) {
            throw new DownloadException("Cannot download while " + state.toString());
        }

        long d = download();
        lastPull = d;

        if (d < 0) {
            finishDownload();
            return;
        }

        downloaded += d;
        currentChunk += d;

        double chunkTime = (double) (System.currentTimeMillis() - lastChunk) / 1000D;
        bps = (long) ((double) currentChunk / chunkTime);

        if (latch.flip()) {
            update();
        }
    }

    public double getBufferUse() {
        return (double) lastPull / (double) bufferSize;
    }

    private void finishDownload() throws IOException {
        if (!isState(DownloadState.NEW)) {
            throw new DownloadException("Cannot finish download while " + state.toString());
        }

        closeStream();
        o.close();
        state(DownloadState.COMPLETE);
    }

    public long getElapsed() {
        return System.currentTimeMillis() - start;
    }

    public long getRemaining() {
        return size - downloaded;
    }

    public long getTimeLeft() {
        return (long) (((double) getRemaining() / (double) bps) * 1000D);
    }

    public long getDiskBytesPerSecond() {
        if (o == null) {
            return -1;
        }

        return o.getBps();
    }

    public long getBytesPerSecond() {
        return bps;
    }

    public double getProgress() {
        return hasProgress() ? ((double) downloaded / (double) size) : -1D;
    }

    public boolean hasProgress() {
        return size > 0;
    }

    private long calculateSize() throws IOException {
        URLConnection c = u.openConnection();
        c.setConnectTimeout(timeout);
        c.setReadTimeout(timeout);
        c.connect();
        return c.getContentLengthLong();
    }

    public enum DownloadFlag {
        CALCULATE_SIZE
    }

    public enum DownloadState {
        NEW,
        STARTING,
        DOWNLOADING,
        FINALIZING,
        COMPLETE,
        FAILED
    }

    public static class ThrottledDownload extends Download {
        private final long mbps;

        public ThrottledDownload(URL u, File d, long mbps, DownloadFlag... downloadFlags) {
            super(u, d, downloadFlags);
            this.mbps = mbps;
        }

        @Override
        protected long download() throws IOException {
            if (getBytesPerSecond() > mbps) {
                try {
                    Thread.sleep(40);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }

                return IO.transfer(in, o, 8192, mbps / 20);
            }

            return IO.transfer(in, o, 8192, bufferSize);
        }
    }

    public static class DoubleBufferedDownload extends Download {
        protected BufferedOutputStream os;

        public DoubleBufferedDownload(URL u, File d, DownloadFlag... downloadFlags) {
            super(u, d, downloadFlags);
        }

        @Override
        protected void openStream() throws IOException {
            os = new BufferedOutputStream(o, 8192 * 16);
            in = new BufferedInputStream(u.openStream(), 8192 * 16);
            buf = new byte[8192 * 2];
        }
    }

    public static class Download extends DL {
        protected InputStream in;
        protected byte[] buf;

        public Download(URL u, File d, DownloadFlag... downloadFlags) {
            super(u, d, downloadFlags);
        }

        @Override
        protected long download() throws IOException {
            return IO.transfer(in, o, buf, bufferSize);
        }

        @Override
        protected void openStream() throws IOException {
            in = u.openStream();
            buf = new byte[8192];
        }

        @Override
        protected void closeStream() throws IOException {
            in.close();
        }
    }
}