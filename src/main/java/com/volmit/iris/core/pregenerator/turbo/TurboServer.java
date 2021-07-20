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

package com.volmit.iris.core.pregenerator.turbo;

import com.volmit.iris.core.pregenerator.PregenListener;
import com.volmit.iris.core.pregenerator.turbo.command.*;
import com.volmit.iris.engine.headless.HeadlessGenerator;
import com.volmit.iris.engine.headless.HeadlessWorld;
import com.volmit.iris.util.io.IO;
import com.volmit.iris.util.scheduling.J;
import org.apache.logging.log4j.core.tools.Generate;
import org.zeroturnaround.zip.ZTFileUtil;
import org.zeroturnaround.zip.ZipUtil;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

public class TurboServer extends Thread implements PregenListener {
    private int port;
    private String password;
    private boolean busy;
    private int tc;
    private HeadlessGenerator generator;
    private ServerSocket server;
    private File cache;
    private UUID currentId = null;
    private AtomicInteger g = new AtomicInteger(0);
    private File lastGeneratedRegion = null;

    public TurboServer(File cache, int port, String password, int tc) throws IOException {
        this.port = port;
        this.cache = cache;
        this.password = password;
        this.tc = tc;
        start();
        server = new ServerSocket(port);
        server.setSoTimeout(1000);
    }

    public void run()
    {
        while(!interrupted())
        {
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

    private void handle(Socket client, DataInputStream i, DataOutputStream o) throws Throwable
    {
        TurboCommand cmd = handle(TurboCommander.read(i), i, o);

        if(cmd != null)
        {
            TurboCommander.write(cmd, o);
        }

        o.flush();
    }

    private File getCachedDim(UUID id)
    {
        return new File(cache, id.toString().charAt(2) +"/" + id.toString().substring(0, 4)+ "/" + id);
    }

    private TurboCommand handle(TurboCommand command, DataInputStream i, DataOutputStream o) throws Throwable {
        if(command instanceof TurboInstallPack)
        {
            if(busy)
            {
                return new TurboBusy();
            }

            if(generator != null)
            {
                generator.close();
                IO.delete(generator.getWorld().getWorld().worldFolder());
                generator = null;
            }

            UUID id = ((TurboInstallPack) command).getPack();
            File cacheload = new File(cache, id.toString().charAt(2) +"/" + id.toString().substring(0, 4)+ "/" + id + ".zip");
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
            HeadlessWorld w = new HeadlessWorld("turbo/" + id.toString(), ((TurboInstallPack) command).getDimension(), ((TurboInstallPack) command).getSeed());
            w.setStudio(true);
            generator = w.generate();
            return new TurboOK();
        }

        if(command instanceof TurboGenerate)
        {
            if(busy)
            {
                return new TurboBusy();
            }

            if(generator == null || !Objects.equals(currentId, ((TurboGenerate) command).getPack())) {
                return new TurboInstallFirst();
            }

            g.set(0);
            busy = true;
            J.a(() -> {
                busy = false;
                lastGeneratedRegion = generator.generateRegionToFile(((TurboGenerate) command).getX(), ((TurboGenerate) command).getZ(), this);
            });
            return new TurboOK();
        }

        if(command instanceof TurboClose)
        {
            if(generator != null && Objects.equals(currentId, ((TurboClose) command).getPack()) && !busy)
            {
                generator.close();
                IO.delete(generator.getWorld().getWorld().worldFolder());
                generator = null;
                currentId = null;
            }
        }

        if(command instanceof TurboGetProgress)
        {
            if(generator != null && busy && Objects.equals(currentId, ((TurboGetProgress) command).getPack()))
            {
                return TurboSendProgress.builder().progress((double)g.get() / 1024D).build();
            }

            else if(generator != null && !busy && Objects.equals(currentId, ((TurboGetProgress) command).getPack()) && lastGeneratedRegion != null && lastGeneratedRegion.exists())
            {
                TurboCommander.write(TurboSendProgress
                    .builder()
                        .progress(1).available(true)
                    .build(), o);
                o.writeLong(lastGeneratedRegion.length());
                IO.writeAll(lastGeneratedRegion, o);
                return null;
            }

            else if(generator == null)
            {
                return new TurboInstallFirst();
            }

            else
            {
                return new TurboBusy();
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
    public void onClose() {

    }

    @Override
    public void onSaving() {

    }

    @Override
    public void onChunkExistsInRegionGen(int x, int z) {

    }
}
