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

package com.volmit.iris.manager.gui;

import com.volmit.iris.Iris;
import com.volmit.iris.generator.IrisEngine;
import com.volmit.iris.map.RenderType;
import com.volmit.iris.object.IrisBiome;
import com.volmit.iris.object.IrisRegion;
import com.volmit.iris.scaffold.engine.Engine;
import com.volmit.iris.scaffold.engine.IrisAccess;
import com.volmit.iris.scaffold.parallel.MultiBurst;
import com.volmit.iris.util.*;
import org.bukkit.World;
import org.bukkit.entity.Player;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.BiFunction;

public class IrisVision extends JPanel implements MouseWheelListener, KeyListener, MouseMotionListener {
    private static final long serialVersionUID = 2094606939770332040L;

    private RenderType currentType = RenderType.BIOME;
    private boolean help = true;
    private boolean helpIgnored = false;
    private boolean shift = false;
    private boolean debug = false;
    private boolean follow = false;
    private boolean alt = false;
    private int posX = 0;
    private IrisRenderer renderer;
    private World world;
    private double velocity = 0;
    private int lowq = 12;
    private int posZ = 0;
    private double scale = 128;
    private double mscale = 1D;
    private int w = 0;
    private int h = 0;
    private double lx = 0;
    private double lz = 0;
    private double ox = 0;
    private double oz = 0;
    private double hx = 0;
    private double hz = 0;
    private double oxp = 0;
    private double ozp = 0;
    private Engine engine;
    double tfps = 240D;
    int ltc = 3;
    private final RollingSequence rs = new RollingSequence(512);
    private final O<Integer> m = new O<>();
    private int tid = 0;
    private final KMap<BlockPosition, BufferedImage> positions = new KMap<>();
    private final KMap<BlockPosition, BufferedImage> fastpositions = new KMap<>();
    private final KSet<BlockPosition> working = new KSet<>();
    private final KSet<BlockPosition> workingfast = new KSet<>();
    private final ExecutorService e = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors(), r -> {
        tid++;
        Thread t = new Thread(r);
        t.setName("Iris HD Renderer " + tid);
        t.setPriority(Thread.MIN_PRIORITY);
        t.setUncaughtExceptionHandler((et, e) ->
        {
            Iris.info("Exception encountered in " + et.getName());
            e.printStackTrace();
        });

        return t;
    });

    private final ExecutorService eh = Executors.newFixedThreadPool(ltc, r -> {
        tid++;
        Thread t = new Thread(r);
        t.setName("Iris Renderer " + tid);
        t.setPriority(Thread.NORM_PRIORITY);
        t.setUncaughtExceptionHandler((et, e) ->
        {
            Iris.info("Exception encountered in " + et.getName());
            e.printStackTrace();
        });

        return t;
    });

    public IrisVision(JFrame frame) {
        m.set(8);
        rs.put(1);
        addMouseWheelListener(this);
        addMouseMotionListener(this);
        frame.addKeyListener(this);
        J.a(() -> {
            J.sleep(10000);

            if (!helpIgnored && help) {
                help = false;
            }
        });
    }

    @Override
    public void mouseMoved(MouseEvent e) {
        Point cp = e.getPoint();
        lx = (cp.getX());
        lz = (cp.getY());
    }

    @Override
    public void mouseDragged(MouseEvent e) {
        Point cp = e.getPoint();
        ox += (lx - cp.getX()) * scale;
        oz += (lz - cp.getY()) * scale;
        lx = cp.getX();
        lz = cp.getY();
    }

    public int getColor(double wx, double wz) {
        BiFunction<Double, Double, Integer> colorFunction = (d, dx) -> Color.black.getRGB();

        switch (currentType) {
            case BIOME, DECORATOR_LOAD, OBJECT_LOAD, LAYER_LOAD -> colorFunction = (x, z) -> engine.getFramework().getComplex().getTrueBiomeStream().get(x, z).getColor(engine, currentType).getRGB();
            case BIOME_LAND -> colorFunction = (x, z) -> engine.getFramework().getComplex().getLandBiomeStream().get(x, z).getColor(engine, currentType).getRGB();
            case BIOME_SEA -> colorFunction = (x, z) -> engine.getFramework().getComplex().getSeaBiomeStream().get(x, z).getColor(engine, currentType).getRGB();
            case REGION -> colorFunction = (x, z) -> engine.getFramework().getComplex().getRegionStream().get(x, z).getColor(engine.getFramework().getComplex(), currentType).getRGB();
            case CAVE_LAND -> colorFunction = (x, z) -> engine.getFramework().getComplex().getCaveBiomeStream().get(x, z).getColor(engine, currentType).getRGB();
            case HEIGHT -> colorFunction = (x, z) -> Color.getHSBColor(engine.getFramework().getComplex().getHeightStream().get(x, z).floatValue(), 100, 100).getRGB();
        }

        return colorFunction.apply(wx, wz);
    }
    
    @Override
    public void keyTyped(KeyEvent e) {
        int currentMode = currentType.ordinal();

        if (e.getKeyCode() == KeyEvent.VK_M) {
            currentType = RenderType.values()[(currentMode+1) % RenderType.values().length];
            dump();
        }

        if (e.getKeyCode() == KeyEvent.VK_R) {
            dump();
            return;
        }

        if (e.getKeyCode() == KeyEvent.VK_EQUALS) {

            return;
        }
        if (e.getKeyCode() == KeyEvent.VK_MINUS) {

            return;
        }


        for(RenderType i : RenderType.values())
        {
            if (e.getKeyChar() == String.valueOf(i.ordinal()+1).charAt(0)) {
                if(i.ordinal() != currentMode)
                {
                    currentType = i;
                    dump();
                    return;
                }
            }
        }
    }

    @Override
    public void keyPressed(KeyEvent e) {
        if (e.getKeyCode() == KeyEvent.VK_SHIFT) {
            shift = true;
        } else if (e.getKeyCode() == KeyEvent.VK_SEMICOLON) {
            debug = true;
        } else if (e.getKeyCode() == KeyEvent.VK_SLASH) {
            help = true;
            helpIgnored = true;
        }else if (e.getKeyCode() == KeyEvent.VK_ALT) {
            alt = true;
        }
    }

    @Override
    public void keyReleased(KeyEvent e) {

        if (e.getKeyCode() == KeyEvent.VK_SEMICOLON) {
            debug = false;
        } else if (e.getKeyCode() == KeyEvent.VK_SHIFT) {
            shift = false;
        } else if (e.getKeyCode() == KeyEvent.VK_SLASH) {
            help = false;
            helpIgnored = true;
        }else if (e.getKeyCode() == KeyEvent.VK_ALT) {
            alt = false;
        }


        if (e.getKeyCode() == KeyEvent.VK_F) {
            Iris.info("FOLLOW TOGGLE");
            follow = !follow;
        }
    }

    private void dump()
    {
        positions.clear();
        fastpositions.clear();
    }

    public BufferedImage getTile(KSet<BlockPosition> fg, int div, int x, int z, O<Integer> m) {
        BlockPosition key = new BlockPosition((int) mscale, Math.floorDiv(x, div), Math.floorDiv(z, div));
        fg.add(key);

        if (positions.containsKey(key)) {
            return positions.get(key);
        }

        if (fastpositions.containsKey(key)) {
            if (!working.contains(key) && working.size() < 9) {
                m.set(m.get() - 1);

                if (m.get() >= 0 && velocity < 50) {
                    working.add(key);
                    double mk = mscale;
                    double mkd = scale;
                    e.submit(() ->
                    {
                        PrecisionStopwatch ps = PrecisionStopwatch.start();
                        BufferedImage b = renderer.render(x * mscale, z * mscale, div * mscale, div, currentType);
                        rs.put(ps.getMilliseconds());
                        working.remove(key);

                        if (mk == mscale && mkd == scale) {
                            positions.put(key, b);
                        }
                    });
                }
            }

            return fastpositions.get(key);
        }

        if (workingfast.contains(key) || workingfast.size() > Runtime.getRuntime().availableProcessors()) {
            return null;
        }

        workingfast.add(key);
        double mk = mscale;
        double mkd = scale;
        eh.submit(() ->
        {
            PrecisionStopwatch ps = PrecisionStopwatch.start();
            BufferedImage b = renderer.render(x * mscale, z * mscale, div * mscale, div / lowq, currentType);
            rs.put(ps.getMilliseconds());
            workingfast.remove(key);

            if (mk == mscale && mkd == scale) {
                fastpositions.put(key, b);
            }
        });
        return null;
    }

    private double getWorldX(double screenX) {
        return (mscale * screenX) + ((oxp / scale) * mscale);
    }

    private double getWorldZ(double screenZ) {
        return (mscale * screenZ) + ((ozp / scale) * mscale);
    }

    private double getScreenX(double x) {
        return (x / mscale) - ((oxp / scale));
    }

    private double getScreenZ(double z) {
        return (z / mscale) - ((ozp / scale));
    }

    @Override
    public void paint(Graphics gx) {
        if (ox < oxp) {
            velocity = Math.abs(ox - oxp) * 0.06;
            oxp -= velocity;
        }

        if (ox > oxp) {
            velocity = Math.abs(oxp - ox) * 0.06;
            oxp += velocity;
        }

        if (oz < ozp) {
            velocity = Math.abs(oz - ozp) * 0.06;
            ozp -= velocity;
        }

        if (oz > ozp) {
            velocity = Math.abs(ozp - oz) * 0.06;
            ozp += velocity;
        }

        if (lx < hx) {
            hx -= Math.abs(lx - hx) * 0.06;
        }

        if (lx > hx) {
            hx += Math.abs(hx - lx) * 0.06;
        }

        if (lz < hz) {
            hz -= Math.abs(lz - hz) * 0.06;
        }

        if (lz > hz) {
            hz += Math.abs(hz - lz) * 0.06;
        }

        lowq = Math.max(Math.min((int)M.lerp(8, 28, velocity / 1000D), 28), 8);
        PrecisionStopwatch p = PrecisionStopwatch.start();
        Graphics2D g = (Graphics2D) gx;
        w = getWidth();
        h = getHeight();
        double vscale = scale;
        scale = w / 12D;

        if (scale != vscale) {
            positions.clear();
        }

        KSet<BlockPosition> gg = new KSet<>();
        int iscale = (int) scale;
        g.setColor(Color.white);
        g.clearRect(0, 0, w, h);
        posX = (int) oxp;
        posZ = (int) ozp;
        m.set(3);

        for (int r = 0; r < Math.max(w, h); r += iscale) {
            for (int i = -iscale; i < w + iscale; i += iscale) {
                for (int j = -iscale; j < h + iscale; j += iscale) {
                    int a = i - (w / 2);
                    int b = j - (h / 2);
                    if (a * a + b * b <= r * r) {
                        BufferedImage t = getTile(gg, iscale, Math.floorDiv((posX / iscale) + i, iscale) * iscale, Math.floorDiv((posZ / iscale) + j, iscale) * iscale, m);

                        if (t != null) {
                            g.drawImage(t, i - ((posX / iscale) % (iscale)), j - ((posZ / iscale) % (iscale)), iscale, iscale, (img, infoflags, x, y, width, height) -> true);
                        }
                    }
                }
            }
        }

        p.end();

        for (BlockPosition i : positions.k()) {
            if (!gg.contains(i)) {
                positions.remove(i);
            }
        }

        renderOverlays(g);

        if (!isVisible()) {
            return;
        }

        if (!getParent().isVisible()) {
            return;
        }

        if (!getParent().getParent().isVisible()) {
            return;
        }

        J.a(() ->
        {
            J.sleep(1);
            repaint();
        });
    }

    private void renderOverlays(Graphics2D g) {
        if(help)
        {
            renderOverlayHelp(g);
        }

        else if(debug)
        {
            renderOverlayDebug(g);
        }

        renderHoverOverlay(g, shift);

        Player b = null;

        for(Player i : world.getPlayers())
        {
            b = i;
            renderPosition(g, i.getLocation().getX(), i.getLocation().getZ());
        }

        if(follow && b != null)
        {
            animateTo(b.getLocation().getX(), b.getLocation().getZ());
            drawCardTL(g, new KList<>("Following " + b.getName()));
        }
    }

    private void animateTo(double wx, double wz) {
        double sx = getScreenX(wx);
        double sz = getScreenZ(wz);
        double cx = getWorldX(getWidth()/2);
        double cz = getWorldZ(getHeight()/2);
        ox += (wx - cx) * 0.77;
        oz += (wz - cz) * 0.77;
    }

    private void renderPosition(Graphics2D g, double x, double z)
    {
        g.setColor(Color.orange);
        g.fillRoundRect((int)getScreenX(x) - 15, (int)getScreenZ(z) - 15, 30, 30, 15, 15);
        g.setColor(Color.blue.brighter().brighter());
        g.fillRoundRect((int)getScreenX(x) - 10, (int)getScreenZ(z) - 10, 20, 20, 10, 10);
    }

    private void renderHoverOverlay(Graphics2D g, boolean detailed) {
        IrisBiome biome = engine.getFramework().getComplex().getTrueBiomeStream().get(getWorldX(hx), getWorldZ(hz));
        IrisRegion region = engine.getFramework().getComplex().getRegionStream().get(getWorldX(hx), getWorldZ(hz));
        KList<String> l = new KList<>();
        l.add(biome.getName());
        if(detailed)
        {
            l.add("Block " + (int)getWorldX(hx) + ", " + (int)getWorldZ(hz));
            l.add("Chunk " + ((int)getWorldX(hx)>>4) + ", " + ((int)getWorldZ(hz)>>4));
            l.add("Region " + (((int)getWorldX(hx)>>4)>>5) + ", " + (((int)getWorldZ(hz)>>4)>>5));
            l.add("Key: " + biome.getLoadKey());
            l.add("File: " + biome.getLoadFile());
            l.add("Region: " + region.getName() + "(" + region.getLoadKey() + ")");
        }

        drawCardAt((float)hx, (float)hz, g, l);
    }

    private void renderOverlayDebug(Graphics2D g) {
        KList<String> l = new KList<>();
        l.add("Velocity: " + velocity);

        drawCardTL(g, l);
    }

    private void renderOverlayHelp(Graphics2D g) {
        KList<String> l = new KList<>();
        l.add("/ to show this help screen");
        l.add("R to repaint the screen");
        l.add("F to follow first player");
        l.add("+/- to Change Zoom");
        l.add("M to cycle render modes");

        int ff = 0;
        for (RenderType i : RenderType.values()) {
            ff++;
            l.add(ff + " to view " + Form.capitalizeWords(i.name().toLowerCase().replaceAll("\\Q_\\E", " ")));
        }

        l.add("Shift for additional biome details (at cursor)");
        l.add("Shift + Click to teleport to location");
        l.add("Alt + Click to open biome in VSCode");
        drawCardTL(g, l);
    }

    private void drawCardTL(Graphics2D g, KList<String> text)
    {
        drawCardAt(0, 0, g, text);
    }

    private void drawCardAt(float x, float y, Graphics2D g, KList<String> text)
    {
        int h = 0;
        int w = 0;

        for(String i : text)
        {
            h += g.getFontMetrics().getHeight();
            w = Math.max(w, g.getFontMetrics().stringWidth(i));
        }

        w += 28;
        h += 28;

        g.setColor(Color.darkGray);
        g.fillRect((int)x + 7 + 2, (int)y + 7 + 2, w + 7, h + 7); // Shadow
        g.setColor(Color.gray);
        g.fillRect((int)x + 7 + 1, (int)y + 7 + 1, w + 7, h + 7); // Shadow
        g.setColor(Color.white);
        g.fillRect((int)x + 7, (int)y + 7, w + 7, h + 7);

        g.setColor(Color.black);
        int m = 0;
        for(String i : text)
        {
            g.drawString(i, x + 14, y + 14 + (++m * g.getFontMetrics().getHeight()));
        }
    }

    private static void createAndShowGUI(Engine r, int s, World world) {
        JFrame frame = new JFrame("Vision");
        IrisVision nv = new IrisVision(frame);
        nv.world = world;
        nv.engine = r;
        nv.renderer = new IrisRenderer(r);
        frame.add(nv);
        frame.setSize(1440, 820);
        frame.setVisible(true);
        File file = Iris.getCached("Iris Icon", "https://raw.githubusercontent.com/VolmitSoftware/Iris/master/icon.png");

        if (file != null) {
            try {
                frame.setIconImage(ImageIO.read(file));
            } catch (IOException e) {Iris.reportError(e);

            }
        }
    }

    public static void launch(IrisAccess g, int i) {
        J.a(() ->
                createAndShowGUI(g.getCompound().getEngine(i), i, g.getCompound().getWorld()));
    }

    public void mouseWheelMoved(MouseWheelEvent e) {
        int notches = e.getWheelRotation();
        if (e.isControlDown()) {
            return;
        }

        Iris.info("Blocks/Pixel: " + (mscale) + ", Blocks Wide: " + (w * mscale));
        positions.clear();
        fastpositions.clear();
        mscale = mscale + ((0.044 * mscale) * notches);
        mscale = Math.max(mscale, 0.00001);
    }
}
