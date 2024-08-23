/*
 *  Iris is a World Generator for Minecraft Bukkit Servers
 *  Copyright (c) 2024 Arcane Arts (Volmit Software)
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

package com.volmit.iris.core.pregenerator;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.volmit.iris.Iris;
import com.volmit.iris.core.tools.IrisPackBenchmarking;
import com.volmit.iris.core.tools.IrisToolbelt;
import com.volmit.iris.util.collection.KList;
import com.volmit.iris.util.collection.KSet;
import com.volmit.iris.util.format.C;
import com.volmit.iris.util.format.Form;
import com.volmit.iris.util.mantle.Mantle;
import com.volmit.iris.util.math.M;
import com.volmit.iris.util.math.Position2;
import com.volmit.iris.util.math.RollingSequence;
import com.volmit.iris.util.nbt.mca.Chunk;
import com.volmit.iris.util.nbt.mca.MCAFile;
import com.volmit.iris.util.nbt.mca.MCAUtil;
import com.volmit.iris.util.parallel.BurstExecutor;
import com.volmit.iris.util.parallel.MultiBurst;
import com.volmit.iris.util.scheduling.ChronoLatch;
import com.volmit.iris.util.scheduling.J;
import com.volmit.iris.util.scheduling.Looper;
import org.bukkit.World;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;

import java.io.*;
import java.lang.reflect.Type;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


public class IrisPregenerator {
    private static AtomicInteger generated;
    private static AtomicInteger totalChunks;
    private final String saveFile = "regions.json";
    private final PregenTask task;
    private final PregeneratorMethod generator;
    private final PregenListener listener;
    private final Looper ticker;
    private final AtomicBoolean paused;
    private final AtomicBoolean shutdown;
    private final RollingSequence chunksPerSecond;
    private final RollingSequence chunksPerMinute;
    private final RollingSequence regionsPerMinute;
    private final KList<Integer> chunksPerSecondHistory;
    private final AtomicInteger generatedLast;
    private final AtomicInteger generatedLastMinute;
    private final AtomicLong startTime;
    private final ChronoLatch minuteLatch;
    private final AtomicReference<String> currentGeneratorMethod;
    private final KSet<Position2> retry;
    private final KSet<Position2> net;
    private final ChronoLatch cl;
    private final ChronoLatch saveLatch = new ChronoLatch(30000);
    private Set<Position2> generatedRegions;

    public IrisPregenerator(PregenTask task, PregeneratorMethod generator, PregenListener listener) {
        generatedRegions = ConcurrentHashMap.newKeySet();
        this.listener = listenify(listener);
        cl = new ChronoLatch(5000);
        this.shutdown = new AtomicBoolean(false);
        this.paused = new AtomicBoolean(false);
        this.task = task;
        this.generator = generator;
        retry = new KSet<>();
        net = new KSet<>();
        currentGeneratorMethod = new AtomicReference<>("Void");
        minuteLatch = new ChronoLatch(60000, false);
        chunksPerSecond = new RollingSequence(10);
        chunksPerMinute = new RollingSequence(10);
        regionsPerMinute = new RollingSequence(10);
        chunksPerSecondHistory = new KList<>();
        generated = new AtomicInteger(0);
        generatedLast = new AtomicInteger(0);
        generatedLastMinute = new AtomicInteger(0);
        totalChunks = new AtomicInteger(0);
        if (!IrisPackBenchmarking.benchmarkInProgress) {
            loadCompletedRegions();
            IrisToolbelt.access(generator.getWorld()).getEngine().saveEngineData();
        }
        task.iterateRegions((_a, _b) -> totalChunks.addAndGet(1024));
        startTime = new AtomicLong(M.ms());
        ticker = new Looper() {
            @Override
            protected long loop() {
                long eta = computeETA();
                int secondGenerated = generated.get() - generatedLast.get();
                generatedLast.set(generated.get());
                chunksPerSecond.put(secondGenerated);
                chunksPerSecondHistory.add(secondGenerated);

                if (minuteLatch.flip()) {
                    int minuteGenerated = generated.get() - generatedLastMinute.get();
                    generatedLastMinute.set(generated.get());
                    chunksPerMinute.put(minuteGenerated);
                    regionsPerMinute.put((double) minuteGenerated / 1024D);
                }

                listener.onTick(chunksPerSecond.getAverage(), chunksPerMinute.getAverage(),
                        regionsPerMinute.getAverage(),
                        (double) generated.get() / (double) totalChunks.get(),
                        generated.get(), totalChunks.get(),
                        totalChunks.get() - generated.get(),
                        eta, M.ms() - startTime.get(), currentGeneratorMethod.get());

                if (cl.flip() && !paused.get()) {
                    double percentage = ((double) generated.get() / (double) totalChunks.get()) * 100;
                    if (!IrisPackBenchmarking.benchmarkInProgress) {
                        Iris.info("Pregen: " + Form.f(generated.get()) + " of " + Form.f(totalChunks.get()) + " (%.0f%%) " + Form.f((int) chunksPerSecond.getAverage()) + "/s ETA: " + Form.duration(eta, 2), percentage);
                    } else {
                        Iris.info("Benchmarking: " + Form.f(generated.get()) + " of " + Form.f(totalChunks.get()) + " (%.0f%%) " + Form.f((int) chunksPerSecond.getAverage()) + "/s ETA: " + Form.duration(eta, 2), percentage);
                    }
                }
                return 1000;
            }
        };
    }

    private long computeETA() {
        long currentTime = M.ms();
        long elapsedTime = currentTime - startTime.get();
        int generatedChunks = generated.get();
        int remainingChunks = totalChunks.get() - generatedChunks;

        if (generatedChunks <= 12_000) {
            // quick
            return (long) (remainingChunks * ((double) elapsedTime / generatedChunks));
        } else {
            //smooth
            return (long) (remainingChunks / chunksPerSecond.getAverage() * 1000);
        }
    }


    public void close() {
        shutdown.set(true);
    }

    public void start() {
        init();
        ticker.start();
        checkRegions();
        task.iterateRegions((x, z) -> visitRegion(x, z, true));
        task.iterateRegions((x, z) -> visitRegion(x, z, false));
        shutdown();
        if (!IrisPackBenchmarking.benchmarkInProgress) {
            Iris.info(C.IRIS + "Pregen stopped.");
            // todo: optimizer just takes too long.
//            if (totalChunks.get() == generated.get() && task.isOptimizer()) {
//                Iris.info("Starting World Optimizer..");
//                ChunkUpdater updater = new ChunkUpdater(generator.getWorld());
//                updater.start();
//            }
        } else {
            IrisPackBenchmarking.getInstance().finishedBenchmark(chunksPerSecondHistory);
        }
    }

    private void checkRegions() {
        task.iterateRegions(this::checkRegion);
    }

    private void init() {
        generator.init();
        generator.save();
    }

    private void shutdown() {
        listener.onSaving();
        generator.close();
        ticker.interrupt();
        listener.onClose();
        saveCompletedRegions();
        Mantle mantle = getMantle();
        if (mantle != null) {
            mantle.trim(0, 0);
        }
    }

    private void getGeneratedRegions() {
        World world = generator.getWorld();
        File[] region = new File(world.getWorldFolder(), "region").listFiles();
        BurstExecutor b = MultiBurst.burst.burst(region.length);
        b.setMulticore(true);
        b.queue(() -> {
            for (File file : region) {
                try {
                    String regex = "r\\.(\\d+)\\.(-?\\d+)\\.mca";
                    Pattern pattern = Pattern.compile(regex);
                    Matcher matcher = pattern.matcher(file.getName());
                    if (!matcher.find()) continue;
                    int x = Integer.parseInt(matcher.group(1));
                    int z = Integer.parseInt(matcher.group(2));
                    Position2 pos = new Position2(x, z);
                    generatedRegions.add(pos);

                    MCAFile mca = MCAUtil.read(file, 0);

                    boolean notFull = false;
                    for (int i = 0; i < 1024; i++) {
                        Chunk chunk = mca.getChunk(i);
                        if (chunk == null) {
                            generatedRegions.remove(pos);
                            notFull = true;
                            break;
                        }
                    }
                    Iris.info("Completed MCA region: " + file.getName());
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });
        b.complete();
    }

    private void visitRegion(int x, int z, boolean regions) {
        while (paused.get() && !shutdown.get()) {
            J.sleep(50);
        }

        if (shutdown.get()) {
            listener.onRegionSkipped(x, z);
            return;
        }

        Position2 pos = new Position2(x, z);

        if (generatedRegions.contains(pos)) {
            if (regions) {
                listener.onRegionGenerated(x, z);
                generated.addAndGet(1024);
            }
            return;
        }

        currentGeneratorMethod.set(generator.getMethod(x, z));
        boolean hit = false;
        if (generator.supportsRegions(x, z, listener) && regions) {
            hit = true;
            listener.onRegionGenerating(x, z);
            generator.generateRegion(x, z, listener);
        } else if (!regions) {
            hit = true;
            listener.onRegionGenerating(x, z);
            PregenTask.iterateRegion(x, z, (xx, zz) -> {
                while (paused.get() && !shutdown.get()) {
                    J.sleep(50);
                }

                generator.generateChunk(xx, zz, listener);
            });
        }

        if (hit) {
            listener.onRegionGenerated(x, z);

            if (saveLatch.flip()) {
                listener.onSaving();
                generator.save();
            }

            generatedRegions.add(pos);
            checkRegions();
        }
    }

    private void checkRegion(int x, int z) {
        if (generatedRegions.contains(new Position2(x, z))) {
            return;
        }

        generator.supportsRegions(x, z, listener);
    }

    public void saveCompletedRegions() {
        if (IrisPackBenchmarking.benchmarkInProgress) return;
        Gson gson = new Gson();
        try (Writer writer = new FileWriter(generator.getWorld().getWorldFolder().getPath() + "/" + saveFile)) {
            gson.toJson(new HashSet<>(generatedRegions), writer);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void loadCompletedRegions() {
        if (task.isResetCache()) {
            File test = new File(generator.getWorld().getWorldFolder().getPath() + "/" + saveFile);
            if (!test.delete()) {
                Iris.info(C.RED + "Failed to reset region cache ");
            }
        }
        Gson gson = new Gson();
        try (Reader reader = new FileReader(generator.getWorld().getWorldFolder().getPath() + "/" + saveFile)) {
            Type setType = new TypeToken<HashSet<Position2>>() {
            }.getType();
            Set<Position2> loadedSet = gson.fromJson(reader, setType);
            if (loadedSet != null) {
                generatedRegions.clear();
                generatedRegions.addAll(loadedSet);
            }
        } catch (FileNotFoundException e) {
            // all fine
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void pause() {
        paused.set(true);
    }

    public void resume() {
        paused.set(false);
    }

    private PregenListener listenify(PregenListener listener) {
        return new PregenListener() {
            @Override
            public void onTick(double chunksPerSecond, double chunksPerMinute, double regionsPerMinute, double percent, int generated, int totalChunks, int chunksRemaining, long eta, long elapsed, String method) {
                listener.onTick(chunksPerSecond, chunksPerMinute, regionsPerMinute, percent, generated, totalChunks, chunksRemaining, eta, elapsed, method);
            }

            @Override
            public void onChunkGenerating(int x, int z) {
                listener.onChunkGenerating(x, z);
            }

            @Override
            public void onChunkGenerated(int x, int z) {
                listener.onChunkGenerated(x, z);
                generated.addAndGet(1);
            }

            @Override
            public void onRegionGenerated(int x, int z) {
                generatedRegions.add(new Position2(x, z));
                saveCompletedRegions();
                listener.onRegionGenerated(x, z);
            }

            @Override
            public void onRegionGenerating(int x, int z) {
                listener.onRegionGenerating(x, z);
            }

            @Override
            public void onChunkCleaned(int x, int z) {
                listener.onChunkCleaned(x, z);
            }

            @Override
            public void onRegionSkipped(int x, int z) {
                listener.onRegionSkipped(x, z);
            }

            @Override
            public void onNetworkStarted(int x, int z) {
                net.add(new Position2(x, z));
            }

            @Override
            public void onNetworkFailed(int x, int z) {
                retry.add(new Position2(x, z));
            }

            @Override
            public void onNetworkReclaim(int revert) {
                generated.addAndGet(-revert);
            }

            @Override
            public void onNetworkGeneratedChunk(int x, int z) {
                generated.addAndGet(1);
            }

            @Override
            public void onNetworkDownloaded(int x, int z) {
                net.remove(new Position2(x, z));
            }

            @Override
            public void onClose() {
                listener.onClose();
            }

            @Override
            public void onSaving() {
                listener.onSaving();
            }

            @Override
            public void onChunkExistsInRegionGen(int x, int z) {
                listener.onChunkExistsInRegionGen(x, z);
            }
        };
    }

    public boolean paused() {
        return paused.get();
    }

    public Mantle getMantle() {
        return generator.getMantle();
    }
}
