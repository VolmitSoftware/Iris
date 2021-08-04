/*
 * Iris is a World Generator for Minecraft Bukkit Servers
 * Copyright (c) 2021 Arcane Arts (Volmit Software)
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

package com.volmit.iris.core.pregenerator.syndicate;

import com.volmit.iris.core.pregenerator.PregenListener;
import com.volmit.iris.core.pregenerator.syndicate.command.*;
import com.volmit.iris.engine.framework.headless.HeadlessGenerator;
import com.volmit.iris.engine.framework.headless.HeadlessWorld;
import com.volmit.iris.util.io.IO;
import com.volmit.iris.util.scheduling.J;
import org.zeroturnaround.zip.ZipUtil;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

public class SyndicateServer extends Thread implements PregenListener {
    private final int port;
    private final String password;
    private boolean busy;
    private final int tc;
    private HeadlessGenerator generator;
    private final ServerSocket server;
    private final File cache;
    private UUID currentId = null;
    private final AtomicInteger g = new AtomicInteger(0);
    private File lastGeneratedRegion = null;

    public SyndicateServer(File cache, int port, String password, int tc) throws IOException {
        this.port = port;
        this.cache = cache;
        this.password = password;
        this.tc = tc;
        start();
        server = new ServerSocket(port);
        server.setSoTimeout(1000);
    }

    public void run() {
        while (!interrupted()) {
            try {
                Socket client = server.accept();
                DataInputStream i = new DataInputStream(client.getInputStream());
                DataOutputStream o = new DataOutputStream(client.getOutputStream());
                try {
                    handle(client, i, o);
                    o.flush();
                } catch (Throwable throwable) {
                    throwable.printStackTrace();
                }
                client.close();
            } catch (SocketTimeoutException ignored) {

            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private void handle(Socket client, DataInputStream i, DataOutputStream o) throws Throwable {
        SyndicateCommand cmd = handle(SyndicateCommandIO.read(i), i, o);

        if (cmd != null) {
            SyndicateCommandIO.write(cmd, o);
        }

        o.flush();
    }

    private File getCachedDim(UUID id) {
        return new File(cache, id.toString().charAt(2) + "/" + id.toString().substring(0, 4) + "/" + id);
    }

    private SyndicateCommand handle(SyndicateCommand command, DataInputStream i, DataOutputStream o) throws Throwable {
        if (command instanceof SyndicateInstallPack) {
            if (busy) {
                return new SyndicateBusy();
            }

            if (generator != null) {
                generator.close();
                IO.delete(generator.getWorld().getWorld().worldFolder());
                generator = null;
            }

            UUID id = ((SyndicateInstallPack) command).getPack();
            File cacheload = new File(cache, id.toString().charAt(2) + "/" + id.toString().substring(0, 4) + "/" + id + ".zip");
            File cachestore = getCachedDim(id);
            IO.delete(cachestore);
            int len = i.readInt();
            cacheload.getParentFile().mkdirs();
            byte[] buf = new byte[8192];
            FileOutputStream fos = new FileOutputStream(cacheload);
            IO.transfer(i, fos, buf, len);
            fos.close();
            ZipUtil.unpack(cacheload, cachestore);
            cacheload.deleteOnExit();
            HeadlessWorld w = new HeadlessWorld("turbo/" + id, ((SyndicateInstallPack) command).getDimension(), ((SyndicateInstallPack) command).getSeed());
            w.setStudio(true);
            generator = w.generate();
            return new SyndicateOK();
        }

        if (command instanceof SyndicateGenerate) {
            if (busy) {
                return new SyndicateBusy();
            }

            if (generator == null || !Objects.equals(currentId, ((SyndicateGenerate) command).getPack())) {
                return new SyndicateInstallFirst();
            }

            g.set(0);
            busy = true;
            J.a(() -> {
                busy = false;
                lastGeneratedRegion = generator.generateRegionToFile(((SyndicateGenerate) command).getX(), ((SyndicateGenerate) command).getZ(), this);
            });
            return new SyndicateOK();
        }

        if (command instanceof SyndicateClose) {
            if (generator != null && Objects.equals(currentId, ((SyndicateClose) command).getPack()) && !busy) {
                generator.close();
                IO.delete(generator.getWorld().getWorld().worldFolder());
                generator = null;
                currentId = null;
            }
        }

        if (command instanceof SyndicateGetProgress) {
            if (generator != null && busy && Objects.equals(currentId, ((SyndicateGetProgress) command).getPack())) {
                return SyndicateSendProgress.builder().progress((double) g.get() / 1024D).build();
            } else if (generator != null && !busy && Objects.equals(currentId, ((SyndicateGetProgress) command).getPack()) && lastGeneratedRegion != null && lastGeneratedRegion.exists()) {
                SyndicateCommandIO.write(SyndicateSendProgress
                        .builder()
                        .progress(1).available(true)
                        .build(), o);
                o.writeLong(lastGeneratedRegion.length());
                IO.writeAll(lastGeneratedRegion, o);
                return null;
            } else if (generator == null) {
                return new SyndicateInstallFirst();
            } else {
                return new SyndicateBusy();
            }
        }

        throw new IllegalStateException("Unexpected value: " + command.getClass());
    }

    public void close() throws IOException {
        interrupt();
        generator.close();
        server.close();
    }

    @Override
    public void onTick(double chunksPerSecond, double chunksPerMinute, double regionsPerMinute, double percent, int generated, int totalChunks, int chunksRemaining, long eta, long elapsed, String method) {

    }

    @Override
    public void onChunkGenerating(int x, int z) {

    }

    @Override
    public void onChunkGenerated(int x, int z) {
        g.incrementAndGet();
    }

    @Override
    public void onRegionGenerated(int x, int z) {

    }

    @Override
    public void onRegionGenerating(int x, int z) {

    }

    @Override
    public void onRegionSkipped(int x, int z) {

    }

    @Override
    public void onNetworkStarted(int x, int z) {

    }

    @Override
    public void onNetworkFailed(int x, int z) {

    }

    @Override
    public void onNetworkReclaim(int revert) {

    }

    @Override
    public void onNetworkGeneratedChunk(int x, int z) {

    }

    @Override
    public void onNetworkDownloaded(int x, int z) {

    }

    @Override
    public void onClose() {

    }

    @Override
    public void onSaving() {

    }

    @Override
    public void onChunkExistsInRegionGen(int x, int z) {

    }
}
