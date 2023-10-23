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

package com.volmit.iris.core.gui;

import com.volmit.iris.Iris;
import com.volmit.iris.core.IrisSettings;
import com.volmit.iris.core.pregenerator.IrisPregenerator;
import com.volmit.iris.core.pregenerator.PregenListener;
import com.volmit.iris.core.pregenerator.PregenTask;
import com.volmit.iris.core.pregenerator.PregeneratorMethod;
import com.volmit.iris.engine.framework.Engine;
import com.volmit.iris.util.collection.KList;
import com.volmit.iris.util.format.Form;
import com.volmit.iris.util.format.MemoryMonitor;
import com.volmit.iris.util.function.Consumer2;
import com.volmit.iris.util.mantle.Mantle;
import com.volmit.iris.util.math.M;
import com.volmit.iris.util.math.Position2;
import com.volmit.iris.util.scheduling.ChronoLatch;
import com.volmit.iris.util.scheduling.J;

import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.image.BufferedImage;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;

public class PregeneratorJob implements PregenListener {
    private static final Color COLOR_EXISTS = parseColor("#4d7d5b");
    private static final Color COLOR_BLACK = parseColor("#4d7d5b");
    private static final Color COLOR_MANTLE = parseColor("#3c2773");
    private static final Color COLOR_GENERATING = parseColor("#66967f");
    private static final Color COLOR_NETWORK = parseColor("#a863c2");
    private static final Color COLOR_NETWORK_GENERATING = parseColor("#836b8c");
    private static final Color COLOR_GENERATED = parseColor("#65c295");
    private static final Color COLOR_CLEANED = parseColor("#34eb93");
    public static PregeneratorJob instance;
    private final MemoryMonitor monitor;
    private final PregenTask task;
    private final boolean saving;
    private final KList<Consumer<Double>> onProgress = new KList<>();
    private final KList<Runnable> whenDone = new KList<>();
    private final IrisPregenerator pregenerator;
    private final Position2 min;
    private final Position2 max;
    private final ChronoLatch cl = new ChronoLatch(TimeUnit.MINUTES.toMillis(1));
    private final Engine engine;
    private JFrame frame;
    private PregenRenderer renderer;
    private int rgc = 0;
    private String[] info;

    public PregeneratorJob(PregenTask task, PregeneratorMethod method, Engine engine) {
        this.engine = engine;
        instance = this;
        monitor = new MemoryMonitor(50);
        saving = false;
        info = new String[]{"Initializing..."};
        this.task = task;
        this.pregenerator = new IrisPregenerator(task, method, this);
        max = new Position2(0, 0);
        min = new Position2(0, 0);
        task.iterateRegions((xx, zz) -> {
            min.setX(Math.min(xx << 5, min.getX()));
            min.setZ(Math.min(zz << 5, min.getZ()));
            max.setX(Math.max((xx << 5) + 31, max.getX()));
            max.setZ(Math.max((zz << 5) + 31, max.getZ()));
        });

        if (IrisSettings.get().getGui().isUseServerLaunchedGuis()) {
            open();
        }

        J.a(this.pregenerator::start, 20);
    }

    public static boolean shutdownInstance() {
        if (instance == null) {
            return false;
        }

        J.a(() -> instance.pregenerator.close());
        return true;
    }

    public static PregeneratorJob getInstance() {
        return instance;
    }

    public static boolean pauseResume() {
        if (instance == null) {
            return false;
        }

        if (isPaused()) {
            instance.pregenerator.resume();
        } else {
            instance.pregenerator.pause();
        }
        return true;
    }

    public static boolean isPaused() {
        if (instance == null) {
            return true;
        }

        return instance.paused();
    }

    private static Color parseColor(String c) {
        String v = (c.startsWith("#") ? c : "#" + c).trim();
        try {
            return Color.decode(v);
        } catch (Throwable e) {
            Iris.reportError(e);
            Iris.error("Error Parsing 'color', (" + c + ")");
        }

        return Color.RED;
    }

    public Mantle getMantle() {
        return pregenerator.getMantle();
    }

    public PregeneratorJob onProgress(Consumer<Double> c) {
        onProgress.add(c);
        return this;
    }

    public PregeneratorJob whenDone(Runnable r) {
        whenDone.add(r);
        return this;
    }

    public void drawRegion(int x, int z, Color color) {
        J.a(() -> PregenTask.iterateRegion(x, z, (xx, zz) -> {
            draw(xx, zz, color);
            J.sleep(3);
        }));
    }

    public void draw(int x, int z, Color color) {
        try {
            if (renderer != null && frame != null && frame.isVisible()) {
                renderer.func.accept(new Position2(x, z), color);
            }
        } catch (Throwable ignored) {
            Iris.error("Failed to draw pregen");
        }
    }

    public void stop() {
        J.a(() -> {
            pregenerator.close();
            close();
            instance = null;
        });
    }

    public void close() {
        J.a(() -> {
            try {
                monitor.close();
                J.sleep(3000);
                frame.setVisible(false);
            } catch (Throwable ignored) {
                Iris.error("Error closing pregen gui");
            }
        });
    }

    public void open() {
        J.a(() -> {
            try {
                frame = new JFrame("Pregen View");
                renderer = new PregenRenderer();
                frame.addKeyListener(renderer);
                renderer.l = new ReentrantLock();
                renderer.frame = frame;
                renderer.job = this;
                renderer.func = (c, b) -> {
                    renderer.l.lock();
                    renderer.order.add(() -> renderer.draw(c, b, renderer.bg));
                    renderer.l.unlock();
                };
                frame.add(renderer);
                frame.setSize(1000, 1000);
                frame.setVisible(true);
            } catch (Throwable ignored) {
                Iris.error("Error opening pregen gui");
            }
        });
    }

    @Override
    public void onTick(double chunksPerSecond, double chunksPerMinute, double regionsPerMinute, double percent, int generated, int totalChunks, int chunksRemaining, long eta, long elapsed, String method) {
        info = new String[]{
                (paused() ? "PAUSED" : (saving ? "Saving... " : "Generating")) + " " + Form.f(generated) + " of " + Form.f(totalChunks) + " (" + Form.pc(percent, 0) + " Complete)",
                "Speed: " + Form.f(chunksPerSecond, 0) + " Chunks/s, " + Form.f(regionsPerMinute, 1) + " Regions/m, " + Form.f(chunksPerMinute, 0) + " Chunks/m",
                Form.duration(eta, 2) + " Remaining " + " (" + Form.duration(elapsed, 2) + " Elapsed)",
                "Generation Method: " + method,
                "Memory: " + Form.memSize(monitor.getUsedBytes(), 2) + " (" + Form.pc(monitor.getUsagePercent(), 0) + ") Pressure: " + Form.memSize(monitor.getPressure(), 0) + "/s",

        };

        for (Consumer<Double> i : onProgress) {
            i.accept(percent);
        }
    }

    @Override
    public void onChunkGenerating(int x, int z) {
        draw(x, z, COLOR_GENERATING);
    }

    @Override
    public void onChunkGenerated(int x, int z) {
        if (engine != null) {
            draw(x, z, engine.draw((x << 4) + 8, (z << 4) + 8));
            return;
        }

        draw(x, z, COLOR_GENERATED);
    }

    @Override
    public void onRegionGenerated(int x, int z) {
        shouldGc();
        rgc++;
    }

    private void shouldGc() {
        if (cl.flip() && rgc > 16) {
            System.gc();
        }
    }

    @Override
    public void onRegionGenerating(int x, int z) {

    }

    @Override
    public void onChunkCleaned(int x, int z) {
        //draw(x, z, COLOR_CLEANED);
    }

    @Override
    public void onRegionSkipped(int x, int z) {

    }

    @Override
    public void onNetworkStarted(int x, int z) {
        drawRegion(x, z, COLOR_NETWORK);
    }

    @Override
    public void onNetworkFailed(int x, int z) {

    }

    @Override
    public void onNetworkReclaim(int revert) {

    }

    @Override
    public void onNetworkGeneratedChunk(int x, int z) {
        draw(x, z, COLOR_NETWORK_GENERATING);
    }

    @Override
    public void onNetworkDownloaded(int x, int z) {
        drawRegion(x, z, COLOR_NETWORK);
    }

    @Override
    public void onClose() {
        close();
        instance = null;
        whenDone.forEach(Runnable::run);
    }

    @Override
    public void onSaving() {

    }

    @Override
    public void onChunkExistsInRegionGen(int x, int z) {
        if (engine != null) {
            draw(x, z, engine.draw((x << 4) + 8, (z << 4) + 8));
            return;
        }

        draw(x, z, COLOR_EXISTS);
    }

    private Position2 getMax() {
        return max;
    }

    private Position2 getMin() {
        return min;
    }

    private boolean paused() {
        return pregenerator.paused();
    }

    private String[] getProgress() {
        return info;
    }

    public static class PregenRenderer extends JPanel implements KeyListener {
        private static final long serialVersionUID = 2094606939770332040L;
        private final KList<Runnable> order = new KList<>();
        private final int res = 512;
        private final BufferedImage image = new BufferedImage(res, res, BufferedImage.TYPE_INT_RGB);
        Graphics2D bg;
        private PregeneratorJob job;
        private ReentrantLock l;
        private Consumer2<Position2, Color> func;
        private JFrame frame;

        public PregenRenderer() {

        }

        public void paint(int x, int z, Color c) {
            func.accept(new Position2(x, z), c);
        }

        @Override
        public void paint(Graphics gx) {
            Graphics2D g = (Graphics2D) gx;
            bg = (Graphics2D) image.getGraphics();
            l.lock();

            while (order.isNotEmpty()) {
                try {
                    order.pop().run();
                } catch (Throwable e) {
                    Iris.reportError(e);

                }
            }

            l.unlock();
            g.drawImage(image, 0, 0, getParent().getWidth(), getParent().getHeight(), (img, infoflags, x, y, width, height) -> true);
            g.setColor(Color.WHITE);
            g.setFont(new Font("Hevetica", Font.BOLD, 13));
            String[] prog = job.getProgress();
            int h = g.getFontMetrics().getHeight() + 5;
            int hh = 20;

            if (job.paused()) {
                g.drawString("PAUSED", 20, hh += h);

                g.drawString("Press P to Resume", 20, hh += h);
            } else {
                for (String i : prog) {
                    g.drawString(i, 20, hh += h);
                }

                g.drawString("Press P to Pause", 20, hh += h);
            }

            J.sleep(IrisSettings.get().getGui().isMaximumPregenGuiFPS() ? 4 : 250);
            repaint();
        }

        private void draw(Position2 p, Color c, Graphics2D bg) {
            double pw = M.lerpInverse(job.getMin().getX(), job.getMax().getX(), p.getX());
            double ph = M.lerpInverse(job.getMin().getZ(), job.getMax().getZ(), p.getZ());
            double pwa = M.lerpInverse(job.getMin().getX(), job.getMax().getX(), p.getX() + 1);
            double pha = M.lerpInverse(job.getMin().getZ(), job.getMax().getZ(), p.getZ() + 1);
            int x = (int) M.lerp(0, res, pw);
            int z = (int) M.lerp(0, res, ph);
            int xa = (int) M.lerp(0, res, pwa);
            int za = (int) M.lerp(0, res, pha);
            bg.setColor(c);
            bg.fillRect(x, z, xa - x, za - z);
        }

        @Override
        public void keyTyped(KeyEvent e) {

        }

        @Override
        public void keyPressed(KeyEvent e) {

        }

        @Override
        public void keyReleased(KeyEvent e) {
            if (e.getKeyCode() == KeyEvent.VK_P) {
                PregeneratorJob.pauseResume();
            }
        }

        public void close() {
            frame.setVisible(false);
        }
    }
}
