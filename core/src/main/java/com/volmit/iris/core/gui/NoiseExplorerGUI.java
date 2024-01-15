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
import com.volmit.iris.core.events.IrisEngineHotloadEvent;
import com.volmit.iris.engine.object.NoiseStyle;
import com.volmit.iris.util.collection.KList;
import com.volmit.iris.util.function.Function2;
import com.volmit.iris.util.math.M;
import com.volmit.iris.util.math.RNG;
import com.volmit.iris.util.math.RollingSequence;
import com.volmit.iris.util.noise.CNG;
import com.volmit.iris.util.parallel.BurstExecutor;
import com.volmit.iris.util.parallel.MultiBurst;
import com.volmit.iris.util.scheduling.J;
import com.volmit.iris.util.scheduling.PrecisionStopwatch;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

public class NoiseExplorerGUI extends JPanel implements MouseWheelListener, Listener {

    private static final long serialVersionUID = 2094606939770332040L;

    static JComboBox<String> combo;
    @SuppressWarnings("CanBeFinal")
    static boolean hd = false;
    static double ascale = 10;
    static double oxp = 0;
    static double ozp = 0;
    static double mxx = 0;
    static double mzz = 0;
    @SuppressWarnings("CanBeFinal")
    static boolean down = false;
    @SuppressWarnings("CanBeFinal")
    RollingSequence r = new RollingSequence(20);
    @SuppressWarnings("CanBeFinal")
    boolean colorMode = true;
    double scale = 1;
    CNG cng = NoiseStyle.STATIC.create(new RNG(RNG.r.nextLong()));
    @SuppressWarnings("CanBeFinal")
    MultiBurst gx = MultiBurst.burst;
    ReentrantLock l = new ReentrantLock();
    BufferedImage img;
    int w = 0;
    int h = 0;
    Function2<Double, Double, Double> generator;
    Supplier<Function2<Double, Double, Double>> loader;
    double ox = 0; //Offset X
    double oz = 0; //Offset Y
    double mx = 0;
    double mz = 0;
    double lx = Double.MAX_VALUE; //MouseX
    double lz = Double.MAX_VALUE; //MouseY
    double t;
    double tz;

    public NoiseExplorerGUI() {
        Iris.instance.registerListener(this);
        addMouseWheelListener(this);
        addMouseMotionListener(new MouseMotionListener() {
            @Override
            public void mouseMoved(MouseEvent e) {
                Point cp = e.getPoint();

                lx = (cp.getX());
                lz = (cp.getY());
                mx = lx;
                mz = lz;
            }

            @Override
            public void mouseDragged(MouseEvent e) {
                Point cp = e.getPoint();
                ox += (lx - cp.getX()) * scale;
                oz += (lz - cp.getY()) * scale;
                lx = cp.getX();
                lz = cp.getY();
            }
        });
    }

    private static void createAndShowGUI(Supplier<Function2<Double, Double, Double>> loader, String genName) {
        JFrame frame = new JFrame("Noise Explorer: " + genName);
        NoiseExplorerGUI nv = new NoiseExplorerGUI();
        frame.setDefaultCloseOperation(JFrame.HIDE_ON_CLOSE);
        JLayeredPane pane = new JLayeredPane();
        nv.setSize(new Dimension(1440, 820));
        pane.add(nv, 1, 0);
        nv.loader = loader;
        nv.generator = loader.get();
        frame.add(pane);
        File file = Iris.getCached("Iris Icon", "https://raw.githubusercontent.com/VolmitSoftware/Iris/master/icon.png");

        if (file != null) {
            try {
                frame.setIconImage(ImageIO.read(file));
            } catch (IOException e) {
                Iris.reportError(e);
            }
        }
        frame.setSize(1440, 820);
        frame.setVisible(true);
    }

    private static void createAndShowGUI() {
        JFrame frame = new JFrame("Noise Explorer");
        NoiseExplorerGUI nv = new NoiseExplorerGUI();
        frame.setDefaultCloseOperation(JFrame.HIDE_ON_CLOSE);
        KList<String> li = new KList<>(NoiseStyle.values()).toStringList().sort();
        combo = new JComboBox<>(li.toArray(new String[0]));
        combo.setSelectedItem("STATIC");
        combo.setFocusable(false);
        combo.addActionListener(e -> {
            @SuppressWarnings("unchecked")
            String b = (String) (((JComboBox<String>) e.getSource()).getSelectedItem());
            NoiseStyle s = NoiseStyle.valueOf(b);
            nv.cng = s.create(RNG.r.nextParallelRNG(RNG.r.imax()));
        });

        combo.setSize(500, 30);
        JLayeredPane pane = new JLayeredPane();
        nv.setSize(new Dimension(1440, 820));
        pane.add(nv, 1, 0);
        pane.add(combo, 2, 0);
        frame.add(pane);
        File file = Iris.getCached("Iris Icon", "https://raw.githubusercontent.com/VolmitSoftware/Iris/master/icon.png");

        if (file != null) {
            try {
                frame.setIconImage(ImageIO.read(file));
            } catch (IOException e) {
                Iris.reportError(e);
            }
        }
        frame.setSize(1440, 820);
        frame.setVisible(true);
        frame.addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowClosing(java.awt.event.WindowEvent windowEvent) {
                Iris.instance.unregisterListener(nv);
            }
        });
    }

    public static void launch(Supplier<Function2<Double, Double, Double>> gen, String genName) {
        EventQueue.invokeLater(() -> createAndShowGUI(gen, genName));
    }

    public static void launch() {
        EventQueue.invokeLater(NoiseExplorerGUI::createAndShowGUI);
    }

    @EventHandler
    public void on(IrisEngineHotloadEvent e) {
        if (generator != null)
            generator = loader.get();
    }

    public void mouseWheelMoved(MouseWheelEvent e) {

        int notches = e.getWheelRotation();
        if (e.isControlDown()) {
            t = t + ((0.0025 * t) * notches);
            return;
        }

        scale = scale + ((0.044 * scale) * notches);
        scale = Math.max(scale, 0.00001);
    }

    @Override
    public void paint(Graphics g) {
        if (scale < ascale) {
            ascale -= Math.abs(scale - ascale) * 0.16;
        }

        if (scale > ascale) {
            ascale += Math.abs(ascale - scale) * 0.16;
        }

        if (t < tz) {
            tz -= Math.abs(t - tz) * 0.29;
        }

        if (t > tz) {
            tz += Math.abs(tz - t) * 0.29;
        }

        if (ox < oxp) {
            oxp -= Math.abs(ox - oxp) * 0.16;
        }

        if (ox > oxp) {
            oxp += Math.abs(oxp - ox) * 0.16;
        }

        if (oz < ozp) {
            ozp -= Math.abs(oz - ozp) * 0.16;
        }

        if (oz > ozp) {
            ozp += Math.abs(ozp - oz) * 0.16;
        }

        if (mx < mxx) {
            mxx -= Math.abs(mx - mxx) * 0.16;
        }

        if (mx > mxx) {
            mxx += Math.abs(mxx - mx) * 0.16;
        }

        if (mz < mzz) {
            mzz -= Math.abs(mz - mzz) * 0.16;
        }

        if (mz > mzz) {
            mzz += Math.abs(mzz - mz) * 0.16;
        }

        PrecisionStopwatch p = PrecisionStopwatch.start();
        int accuracy = hd ? 1 : M.clip((r.getAverage() / 12D), 2D, 128D).intValue();
        accuracy = down ? accuracy * 4 : accuracy;
        int v = 1000;

        if (g instanceof Graphics2D gg) {

            if (getParent().getWidth() != w || getParent().getHeight() != h) {
                w = getParent().getWidth();
                h = getParent().getHeight();
                img = null;
            }

            if (img == null) {
                img = new BufferedImage(w / accuracy, h / accuracy, BufferedImage.TYPE_INT_RGB);
            }

            BurstExecutor e = gx.burst(w);

            for (int x = 0; x < w / accuracy; x++) {
                int xx = x;

                int finalAccuracy = accuracy;
                e.queue(() -> {
                    for (int z = 0; z < h / finalAccuracy; z++) {
                        double n = generator != null ? generator.apply(((xx * finalAccuracy) * ascale) + oxp, ((z * finalAccuracy) * ascale) + ozp) : cng.noise(((xx * finalAccuracy) * ascale) + oxp, ((z * finalAccuracy) * ascale) + ozp);
                        n = n > 1 ? 1 : n < 0 ? 0 : n;

                        try {
                            Color color = colorMode ? Color.getHSBColor((float) (n), 1f - (float) (n * n * n * n * n * n), 1f - (float) n) : Color.getHSBColor(0f, 0f, (float) n);
                            int rgb = color.getRGB();
                            img.setRGB(xx, z, rgb);
                        } catch (Throwable ignored) {
                        }
                    }
                });
            }

            e.complete();
            gg.drawImage(img, 0, 0, getParent().getWidth() * accuracy, getParent().getHeight() * accuracy, (img, infoflags, x, y, width, height) -> true);
        }

        p.end();

        t += 1D;
        r.put(p.getMilliseconds());

        if (!isVisible()) {
            return;
        }

        if (!getParent().isVisible()) {
            return;
        }

        if (!getParent().getParent().isVisible()) {
            return;
        }

        EventQueue.invokeLater(() ->
        {
            J.sleep((long) Math.max(0, 32 - r.getAverage()));
            repaint();
        });
    }

    static class HandScrollListener extends MouseAdapter {
        private static final Point pp = new Point();

        @Override
        public void mouseDragged(MouseEvent e) {
            JViewport vport = (JViewport) e.getSource();
            JComponent label = (JComponent) vport.getView();
            Point cp = e.getPoint();
            Point vp = vport.getViewPosition();
            vp.translate(pp.x - cp.x, pp.y - cp.y);
            label.scrollRectToVisible(new Rectangle(vp, vport.getSize()));

            pp.setLocation(cp);
        }

        @Override
        public void mousePressed(MouseEvent e) {
            pp.setLocation(e.getPoint());
        }
    }
}