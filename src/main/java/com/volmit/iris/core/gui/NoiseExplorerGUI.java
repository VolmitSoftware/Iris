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

package com.volmit.iris.core.gui;

import com.volmit.iris.Iris;
import com.volmit.iris.engine.noise.CNG;
import com.volmit.iris.engine.object.NoiseStyle;
import com.volmit.iris.util.collection.KList;
import com.volmit.iris.util.function.Function2;
import com.volmit.iris.util.math.M;
import com.volmit.iris.util.math.RNG;
import com.volmit.iris.util.math.RollingSequence;
import com.volmit.iris.util.scheduling.GroupedExecutor;
import com.volmit.iris.util.scheduling.J;
import com.volmit.iris.util.scheduling.PrecisionStopwatch;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.io.File;
import java.io.IOException;
import java.util.concurrent.locks.ReentrantLock;

public class NoiseExplorerGUI extends JPanel implements MouseWheelListener {

    private static final long serialVersionUID = 2094606939770332040L;

    static JComboBox<String> combo;
    @SuppressWarnings("CanBeFinal")
    RollingSequence r = new RollingSequence(90);
    @SuppressWarnings("CanBeFinal")
    boolean colorMode = true;
    double scale = 1;
    @SuppressWarnings("CanBeFinal")
    static boolean hd = false;
    static double ascale = 10;
    CNG cng = NoiseStyle.STATIC.create(new RNG(RNG.r.nextLong()));
    @SuppressWarnings("CanBeFinal")
    GroupedExecutor gx = new GroupedExecutor(Runtime.getRuntime().availableProcessors(), Thread.MAX_PRIORITY, "Iris Renderer");
    ReentrantLock l = new ReentrantLock();
    int[][] co;
    int w = 0;
    int h = 0;
    Function2<Double, Double, Double> generator;
    static double oxp = 0;
    static double ozp = 0;
    double ox = 0; //Offset X
    double oz = 0; //Offset Y
    double mx = 0;
    double mz = 0;
    static double mxx = 0;
    static double mzz = 0;
    @SuppressWarnings("CanBeFinal")
    static boolean down = false;
    double lx = Double.MAX_VALUE; //MouseX
    double lz = Double.MAX_VALUE; //MouseY
    double t;
    double tz;

    public NoiseExplorerGUI() {
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
        int accuracy = hd ? 1 : M.clip((r.getAverage() / 6D), 1D, 128D).intValue();
        accuracy = down ? accuracy * 4 : accuracy;
        int v = 1000;

        if (g instanceof Graphics2D gg) {

            if (getParent().getWidth() != w || getParent().getHeight() != h) {
                w = getParent().getWidth();
                h = getParent().getHeight();
                co = null;
            }

            if (co == null) {
                co = new int[w][h];
            }

            for (int x = 0; x < w; x += accuracy) {
                int xx = x;

                for (int z = 0; z < h; z += accuracy) {
                    int zz = z;
                    gx.queue("a", () ->
                    {
                        double n = generator != null ? generator.apply((xx * ascale) + oxp, (zz * ascale) + ozp) : cng.noise((xx * ascale) + oxp, tz, (zz * ascale) + ozp);

                        if (n > 1 || n < 0) {
                            return;
                        }

                        Color color = colorMode ? Color.getHSBColor((float) (n), 1f - (float) (n * n * n * n * n * n), 1f - (float) n) : Color.getHSBColor(0f, 0f, (float) n);
                        int rgb = color.getRGB();
                        co[xx][zz] = rgb;
                    });
                }

                gx.waitFor("a");

                if (hd && p.getMilliseconds() > v) {
                    break;
                }
            }

            for (int x = 0; x < getParent().getWidth(); x += accuracy) {
                for (int z = 0; z < getParent().getHeight(); z += accuracy) {
                    gg.setColor(new Color(co[x][z]));
                    gg.fillRect(x, z, accuracy, accuracy);
                }
            }
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

    private static void createAndShowGUI(Function2<Double, Double, Double> gen, String genName) {
        JFrame frame = new JFrame("Noise Explorer: " + genName);
        NoiseExplorerGUI nv = new NoiseExplorerGUI();
        frame.setDefaultCloseOperation(JFrame.HIDE_ON_CLOSE);
        JLayeredPane pane = new JLayeredPane();
        nv.setSize(new Dimension(1440, 820));
        pane.add(nv, 1, 0);
        nv.generator = gen;
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
        KList<String> li = new KList<>(NoiseStyle.values()).toStringList();
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
    }

    public static void launch(Function2<Double, Double, Double> gen, String genName) {
        EventQueue.invokeLater(() -> createAndShowGUI(gen, genName));
    }

    public static void launch() {
        EventQueue.invokeLater(NoiseExplorerGUI::createAndShowGUI);
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