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
import com.volmit.iris.core.gui.components.IrisRenderer;
import com.volmit.iris.core.gui.components.RenderType;
import com.volmit.iris.core.tools.IrisToolbelt;
import com.volmit.iris.engine.IrisComplex;
import com.volmit.iris.engine.framework.Engine;
import com.volmit.iris.engine.object.IrisBiome;
import com.volmit.iris.engine.object.IrisRegion;
import com.volmit.iris.engine.object.IrisWorld;
import com.volmit.iris.util.collection.KList;
import com.volmit.iris.util.collection.KMap;
import com.volmit.iris.util.collection.KSet;
import com.volmit.iris.util.format.Form;
import com.volmit.iris.util.math.BlockPosition;
import com.volmit.iris.util.math.M;
import com.volmit.iris.util.math.RollingSequence;
import com.volmit.iris.util.scheduling.ChronoLatch;
import com.volmit.iris.util.scheduling.J;
import com.volmit.iris.util.scheduling.O;
import com.volmit.iris.util.scheduling.PrecisionStopwatch;
import org.bukkit.Location;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.event.MouseInputListener;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.BiFunction;

public class VisionGUI extends JPanel implements MouseWheelListener, KeyListener, MouseMotionListener, MouseInputListener {
    private static final long serialVersionUID = 2094606939770332040L;
    private final KList<LivingEntity> lastEntities = new KList<>();
    private final KMap<String, Long> notifications = new KMap<>();
    private final ChronoLatch centities = new ChronoLatch(1000);
    private final RollingSequence rs = new RollingSequence(512);
    private final O<Integer> m = new O<>();
    private final KMap<BlockPosition, BufferedImage> positions = new KMap<>();
    private final KMap<BlockPosition, BufferedImage> fastpositions = new KMap<>();
    private final KSet<BlockPosition> working = new KSet<>();
    private final KSet<BlockPosition> workingfast = new KSet<>();
    double tfps = 240D;
    int ltc = 3;
    private RenderType currentType = RenderType.BIOME;
    private boolean help = true;
    private boolean helpIgnored = false;
    private boolean shift = false;
    private Player player = null;
    private boolean debug = false;
    private boolean control = false;
    private boolean eco = false;
    private boolean lowtile = false;
    private boolean follow = false;
    private boolean alt = false;
    private IrisRenderer renderer;
    private IrisWorld world;
    private double velocity = 0;
    private int lowq = 12;
    private double scale = 128;
    private double mscale = 4D;
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
    private int tid = 0;
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
    private BufferedImage texture;

    public VisionGUI(JFrame frame) {
        m.set(8);
        rs.put(1);
        addMouseWheelListener(this);
        addMouseMotionListener(this);
        addMouseListener(this);
        frame.addKeyListener(this);
        J.a(() -> {
            J.sleep(10000);

            if (!helpIgnored && help) {
                help = false;
            }
        });
        frame.addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowClosing(java.awt.event.WindowEvent windowEvent) {
                e.shutdown();
                eh.shutdown();
            }
        });
    }

    private static void createAndShowGUI(Engine r, int s, IrisWorld world) {
        JFrame frame = new JFrame("Vision");
        VisionGUI nv = new VisionGUI(frame);
        nv.world = world;
        nv.engine = r;
        nv.renderer = new IrisRenderer(r);
        frame.add(nv);
        frame.setSize(1440, 820);
        frame.setVisible(true);
        File file = Iris.getCached("Iris Icon", "https://raw.githubusercontent.com/VolmitSoftware/Iris/master/icon.png");

        if (file != null) {
            try {
                nv.texture = ImageIO.read(file);
                frame.setIconImage(ImageIO.read(file));
            } catch (IOException e) {
                Iris.reportError(e);

            }
        }
    }

    public static void launch(Engine g, int i) {
        J.a(() ->
                createAndShowGUI(g, i, g.getWorld()));
    }

    public boolean updateEngine() {
        if (engine.isClosed()) {
            if (world.hasRealWorld()) {
                try {
                    engine = IrisToolbelt.access(world.realWorld()).getEngine();
                    return !engine.isClosed();
                } catch (Throwable e) {

                }
            }
        }

        return false;
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
            case BIOME, DECORATOR_LOAD, OBJECT_LOAD, LAYER_LOAD ->
                    colorFunction = (x, z) -> engine.getComplex().getTrueBiomeStream().get(x, z).getColor(engine, currentType).getRGB();
            case BIOME_LAND ->
                    colorFunction = (x, z) -> engine.getComplex().getLandBiomeStream().get(x, z).getColor(engine, currentType).getRGB();
            case BIOME_SEA ->
                    colorFunction = (x, z) -> engine.getComplex().getSeaBiomeStream().get(x, z).getColor(engine, currentType).getRGB();
            case REGION ->
                    colorFunction = (x, z) -> engine.getComplex().getRegionStream().get(x, z).getColor(engine.getComplex(), currentType).getRGB();
            case CAVE_LAND ->
                    colorFunction = (x, z) -> engine.getComplex().getCaveBiomeStream().get(x, z).getColor(engine, currentType).getRGB();
            case HEIGHT ->
                    colorFunction = (x, z) -> Color.getHSBColor(engine.getComplex().getHeightStream().get(x, z).floatValue(), 100, 100).getRGB();
        }

        return colorFunction.apply(wx, wz);
    }

    public void notify(String s) {
        notifications.put(s, M.ms() + 2500);
    }

    @Override
    public void keyTyped(KeyEvent e) {

    }

    @Override
    public void keyPressed(KeyEvent e) {
        if (e.getKeyCode() == KeyEvent.VK_SHIFT) {
            shift = true;
        }
        if (e.getKeyCode() == KeyEvent.VK_CONTROL) {
            control = true;
        }
        if (e.getKeyCode() == KeyEvent.VK_SEMICOLON) {
            debug = true;
        }
        if (e.getKeyCode() == KeyEvent.VK_SLASH) {
            help = true;
            helpIgnored = true;
        }
        if (e.getKeyCode() == KeyEvent.VK_ALT) {
            alt = true;
        }
    }

    @Override
    public void keyReleased(KeyEvent e) {
        if (e.getKeyCode() == KeyEvent.VK_SEMICOLON) {
            debug = false;
        }
        if (e.getKeyCode() == KeyEvent.VK_SHIFT) {
            shift = false;
        }

        if (e.getKeyCode() == KeyEvent.VK_CONTROL) {
            control = false;
        }
        if (e.getKeyCode() == KeyEvent.VK_SLASH) {
            help = false;
            helpIgnored = true;
        }
        if (e.getKeyCode() == KeyEvent.VK_ALT) {
            alt = false;
        }

        // Pushes
        if (e.getKeyCode() == KeyEvent.VK_F) {
            follow = !follow;

            if (player != null && follow) {
                notify("Following " + player.getName() + ". Press F to disable");
            } else if (follow) {
                notify("Can't follow, no one is in the world");
                follow = false;
            } else {
                notify("Follow Off");
            }

            return;
        }

        if (e.getKeyCode() == KeyEvent.VK_R) {
            dump();
            notify("Refreshing Chunks");
            return;
        }

        if (e.getKeyCode() == KeyEvent.VK_P) {
            lowtile = !lowtile;
            dump();
            notify("Rendering " + (lowtile ? "Low" : "High") + " Quality Tiles");
            return;
        }
        if (e.getKeyCode() == KeyEvent.VK_E) {
            eco = !eco;
            dump();
            notify("Using " + (eco ? "60" : "Uncapped") + " FPS Limit");
            return;
        }
        if (e.getKeyCode() == KeyEvent.VK_EQUALS) {
            mscale = mscale + ((0.044 * mscale) * -3);
            mscale = Math.max(mscale, 0.00001);
            dump();
            return;
        }
        if (e.getKeyCode() == KeyEvent.VK_MINUS) {
            mscale = mscale + ((0.044 * mscale) * 3);
            mscale = Math.max(mscale, 0.00001);
            dump();
            return;
        }

        if (e.getKeyCode() == KeyEvent.VK_BACK_SLASH) {
            mscale = 1D;
            dump();
            notify("Zoom Reset");
            return;
        }

        int currentMode = currentType.ordinal();

        for (RenderType i : RenderType.values()) {
            if (e.getKeyChar() == String.valueOf(i.ordinal() + 1).charAt(0)) {
                if (i.ordinal() != currentMode) {
                    currentType = i;
                    dump();
                    notify("Rendering " + Form.capitalizeWords(currentType.name().toLowerCase().replaceAll("\\Q_\\E", " ")));
                    return;
                }
            }
        }

        if (e.getKeyCode() == KeyEvent.VK_M) {
            currentType = RenderType.values()[(currentMode + 1) % RenderType.values().length];
            notify("Rendering " + Form.capitalizeWords(currentType.name().toLowerCase().replaceAll("\\Q_\\E", " ")));
            dump();
        }
    }

    private void dump() {
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
                        BufferedImage b = renderer.render(x * mscale, z * mscale, div * mscale, div / (lowtile ? 3 : 1), currentType);
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
        //return (mscale * screenX) + ((oxp / scale) * mscale);
        return (mscale * screenX) + ((oxp / scale));
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

        if (engine.isClosed()) {
            EventQueue.invokeLater(() -> {
                try {
                    setVisible(false);
                } catch (Throwable e) {

                }
            });

            return;
        }

        if (updateEngine()) {
            dump();
        }

        if (ox < oxp) {
            velocity = Math.abs(ox - oxp) * 0.36;
            oxp -= velocity;
        }

        if (ox > oxp) {
            velocity = Math.abs(oxp - ox) * 0.36;
            oxp += velocity;
        }

        if (oz < ozp) {
            velocity = Math.abs(oz - ozp) * 0.36;
            ozp -= velocity;
        }

        if (oz > ozp) {
            velocity = Math.abs(ozp - oz) * 0.36;
            ozp += velocity;
        }

        if (lx < hx) {
            hx -= Math.abs(lx - hx) * 0.36;
        }

        if (lx > hx) {
            hx += Math.abs(hx - lx) * 0.36;
        }

        if (lz < hz) {
            hz -= Math.abs(lz - hz) * 0.36;
        }

        if (lz > hz) {
            hz += Math.abs(hz - lz) * 0.36;
        }

        if (centities.flip()) {
            J.s(() -> {
                synchronized (lastEntities) {
                    lastEntities.clear();
                    lastEntities.addAll(world.getEntitiesByClass(LivingEntity.class));
                }
            });
        }
        lowq = Math.max(Math.min((int) M.lerp(8, 28, velocity / 1000D), 28), 8);
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
        int posX = (int) oxp;
        int posZ = (int) ozp;
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

        hanleFollow();
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
            J.sleep(eco ? 15 : 1);
            repaint();
        });
    }

    private void hanleFollow() {
        if (follow && player != null) {
            animateTo(player.getLocation().getX(), player.getLocation().getZ());
        }
    }

    private void renderOverlays(Graphics2D g) {
        renderPlayer(g);

        if (help) {
            renderOverlayHelp(g);
        } else if (debug) {
            renderOverlayDebug(g);
        }

        renderOverlayLegend(g);

        renderHoverOverlay(g, shift);
        if (!notifications.isEmpty()) {
            renderNotification(g);
        }
    }

    private void renderOverlayLegend(Graphics2D g) {
        KList<String> l = new KList<>();
        l.add("Zoom: " + Form.pc(mscale, 0));
        l.add("Blocks: " + Form.f((int) mscale * w) + " by " + Form.f((int) mscale * h));
        l.add("BPP: " + Form.f(mscale, 1));
        l.add("Render Mode: " + Form.capitalizeWords(currentType.name().toLowerCase().replaceAll("\\Q_\\E", " ")));

        drawCardBR(g, l);
    }

    private void renderNotification(Graphics2D g) {
        drawCardCB(g, notifications.k());

        for (String i : notifications.k()) {
            if (M.ms() > notifications.get(i)) {
                notifications.remove(i);
            }
        }
    }

    private void renderPlayer(Graphics2D g) {
        Player b = null;

        for (Player i : world.getPlayers()) {
            b = i;
            renderPosition(g, i.getLocation().getX(), i.getLocation().getZ());
        }

        synchronized (lastEntities) {
            double dist = Double.MAX_VALUE;
            LivingEntity h = null;

            for (LivingEntity i : lastEntities) {
                if (i instanceof Player) {
                    continue;
                }

                renderMobPosition(g, i, i.getLocation().getX(), i.getLocation().getZ());
                if (shift) {
                    double d = i.getLocation().distanceSquared(new Location(i.getWorld(), getWorldX(hx), i.getLocation().getY(), getWorldZ(hz)));

                    if (d < dist) {
                        dist = d;
                        h = i;
                    }
                }
            }

            if (h != null && shift) {
                g.setColor(Color.red);
                g.fillRoundRect((int) getScreenX(h.getLocation().getX()) - 10, (int) getScreenZ(h.getLocation().getZ()) - 10, 20, 20, 20, 20);
                KList<String> k = new KList<>();
                k.add(Form.capitalizeWords(h.getType().name().toLowerCase(Locale.ROOT).replaceAll("\\Q_\\E", " ")) + h.getEntityId());

                k.add("Pos: " + h.getLocation().getBlockX() + ", " + h.getLocation().getBlockY() + ", " + h.getLocation().getBlockZ());
                k.add("UUID: " + h.getUniqueId());
                k.add("HP: " + h.getHealth() + " / " + h.getAttribute(Attribute.GENERIC_MAX_HEALTH).getValue());

                drawCardTR(g, k);
            }
        }

        player = b;
    }

    private void animateTo(double wx, double wz) {
        double cx = getWorldX(getWidth() / 2);
        double cz = getWorldZ(getHeight() / 2);
        ox += (wx - cx);
        oz += (wz - cz);
    }

    private void renderPosition(Graphics2D g, double x, double z) {
        if (texture != null) {
            g.drawImage(texture, (int) getScreenX(x), (int) getScreenZ(z), 66, 66, (img, infoflags, xx, xy, width, height) -> true);
        } else {
            g.setColor(Color.darkGray);
            g.fillRoundRect((int) getScreenX(x) - 15, (int) getScreenZ(z) - 15, 30, 30, 15, 15);
            g.setColor(Color.cyan.darker().darker());
            g.fillRoundRect((int) getScreenX(x) - 10, (int) getScreenZ(z) - 10, 20, 20, 10, 10);
        }
    }

    private void renderMobPosition(Graphics2D g, LivingEntity e, double x, double z) {
        g.setColor(Color.red.darker().darker());
        g.fillRoundRect((int) getScreenX(x) - 2, (int) getScreenZ(z) - 2, 4, 4, 4, 4);
    }

    private void renderHoverOverlay(Graphics2D g, boolean detailed) {
        IrisBiome biome = engine.getComplex().getTrueBiomeStream().get(getWorldX(hx), getWorldZ(hz));
        IrisRegion region = engine.getComplex().getRegionStream().get(getWorldX(hx), getWorldZ(hz));
        KList<String> l = new KList<>();
        l.add("Biome: " + biome.getName());
        l.add("Region: " + region.getName() + "(" + region.getLoadKey() + ")");
        l.add("Block " + (int) getWorldX(hx) + ", " + (int) getWorldZ(hz));
        if (detailed) {
            l.add("Chunk " + ((int) getWorldX(hx) >> 4) + ", " + ((int) getWorldZ(hz) >> 4));
            l.add("Region " + (((int) getWorldX(hx) >> 4) >> 5) + ", " + (((int) getWorldZ(hz) >> 4) >> 5));
            l.add("Key: " + biome.getLoadKey());
            l.add("File: " + biome.getLoadFile());
        }

        drawCardAt((float) hx, (float) hz, 0, 0, g, l);
    }

    private void renderOverlayDebug(Graphics2D g) {
        KList<String> l = new KList<>();
        l.add("Velocity: " + (int) velocity);
        l.add("Center Pos: " + Form.f((int) getWorldX(getWidth() / 2)) + ", " + Form.f((int) getWorldZ(getHeight() / 2)));
        drawCardBL(g, l);
    }

    private void renderOverlayHelp(Graphics2D g) {
        KList<String> l = new KList<>();
        l.add("/ to show this help screen");
        l.add("R to repaint the screen");
        l.add("F to follow first player");
        l.add("+/- to Change Zoom");
        l.add("\\ to reset zoom to 1");
        l.add("M to cycle render modes");
        l.add("P to toggle Tile Quality Mode");
        l.add("E to toggle Eco FPS Mode");

        int ff = 0;
        for (RenderType i : RenderType.values()) {
            ff++;
            l.add(ff + " to view " + Form.capitalizeWords(i.name().toLowerCase().replaceAll("\\Q_\\E", " ")));
        }

        l.add("Shift for additional biome details (at cursor)");
        l.add("CTRL + Click to teleport to location");
        l.add("ALT + Click to open biome in VSCode");
        drawCardTL(g, l);
    }

    private void drawCardTL(Graphics2D g, KList<String> text) {
        drawCardAt(0, 0, 0, 0, g, text);
    }

    private void drawCardBR(Graphics2D g, KList<String> text) {
        drawCardAt(getWidth(), getHeight(), 1, 1, g, text);
    }

    private void drawCardBL(Graphics2D g, KList<String> text) {
        drawCardAt(0, getHeight(), 0, 1, g, text);
    }

    private void drawCardTR(Graphics2D g, KList<String> text) {
        drawCardAt(getWidth(), 0, 1, 0, g, text);
    }

    private void open() {
        IrisComplex complex = engine.getComplex();
        File r = null;
        switch (currentType) {
            case BIOME, LAYER_LOAD, DECORATOR_LOAD, OBJECT_LOAD, HEIGHT ->
                    r = complex.getTrueBiomeStream().get(getWorldX(hx), getWorldZ(hz)).openInVSCode();
            case BIOME_LAND -> r = complex.getLandBiomeStream().get(getWorldX(hx), getWorldZ(hz)).openInVSCode();
            case BIOME_SEA -> r = complex.getSeaBiomeStream().get(getWorldX(hx), getWorldZ(hz)).openInVSCode();
            case REGION -> r = complex.getRegionStream().get(getWorldX(hx), getWorldZ(hz)).openInVSCode();
            case CAVE_LAND -> r = complex.getCaveBiomeStream().get(getWorldX(hx), getWorldZ(hz)).openInVSCode();
        }

        notify("Opening " + r.getPath() + " in VSCode");
    }

    private void teleport() {
        J.s(() -> {
            if (player != null) {
                int xx = (int) getWorldX(hx);
                int zz = (int) getWorldZ(hz);
                int h = engine.getComplex().getRoundedHeighteightStream().get(xx, zz);
                player.teleport(new Location(player.getWorld(), xx, h, zz));
                notify("Teleporting to " + xx + ", " + h + ", " + zz);
            } else {
                notify("No player in world, can't teleport.");
            }
        });
    }

    private void drawCardCB(Graphics2D g, KList<String> text) {
        drawCardAt(getWidth() / 2, getHeight(), 0.5, 1, g, text);
    }

    private void drawCardCT(Graphics2D g, KList<String> text) {
        drawCardAt(getWidth() / 2, 0, 0.5, 0, g, text);
    }

    private void drawCardAt(float x, float y, double pushX, double pushZ, Graphics2D g, KList<String> text) {
        g.setFont(new Font("Hevetica", Font.BOLD, 16));
        int h = 0;
        int w = 0;

        for (String i : text) {
            h += g.getFontMetrics().getHeight();
            w = Math.max(w, g.getFontMetrics().stringWidth(i));
        }

        w += 28;
        h += 14;

        int cw = (int) ((w + 26) * pushX);
        int ch = (int) ((h + 26) * pushZ);

        g.setColor(Color.darkGray);
        g.fillRect((int) x + 7 + 2 - cw, (int) y + 12 + 2 - ch, w + 7, h); // Shadow
        g.setColor(Color.gray);
        g.fillRect((int) x + 7 + 1 - cw, (int) y + 12 + 1 - ch, w + 7, h); // Shadow
        g.setColor(Color.white);
        g.fillRect((int) x + 7 - cw, (int) y + 12 - ch, w + 7, h);

        g.setColor(Color.black);
        int m = 0;
        for (String i : text) {
            g.drawString(i, x + 14 - cw, y + 14 - ch + (++m * g.getFontMetrics().getHeight()));
        }
    }

    public void mouseWheelMoved(MouseWheelEvent e) {
        int notches = e.getWheelRotation();
        if (e.isControlDown()) {
            return;
        }

        //Iris.info("Blocks/Pixel: " + (mscale) + ", Blocks Wide: " + (w * mscale));
        positions.clear();
        fastpositions.clear();
        mscale = mscale + ((0.25 * mscale) * notches);
        mscale = Math.max(mscale, 0.00001);
    }

    @Override
    public void mouseClicked(MouseEvent e) {
        if (control) {
            teleport();
        } else if (alt) {
            open();
        }
    }

    @Override
    public void mousePressed(MouseEvent e) {

    }

    @Override
    public void mouseReleased(MouseEvent e) {

    }

    @Override
    public void mouseEntered(MouseEvent e) {

    }

    @Override
    public void mouseExited(MouseEvent e) {

    }
}
