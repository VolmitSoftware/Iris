package com.volmit.iris.manager.gui;

import com.volmit.iris.Iris;
import com.volmit.iris.scaffold.engine.IrisAccess;
import com.volmit.iris.util.*;
import org.bukkit.World;
import org.bukkit.entity.Player;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionListener;
import java.awt.event.MouseWheelEvent;
import java.awt.event.MouseWheelListener;
import java.awt.image.BufferedImage;
import java.awt.image.ImageObserver;
import java.io.File;
import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

public class IrisVision extends JPanel implements MouseWheelListener {
    private static final long serialVersionUID = 2094606939770332040L;
    private IrisRenderer renderer;
    private int posX = 0;
    private World world;
    private int posZ = 0;
    private double scale = 128;
    private double mscale = 1D;
    private int w = 0;
    private int h = 0;
    private double lx = Double.MAX_VALUE;
    private double lz = Double.MAX_VALUE;
    private double ox = 0;
    private double oz = 0;
    private double oxp = 0;
    private double ozp = 0;
    double tfps = 240D;
    private final RollingSequence rs = new RollingSequence(512);
    private final O<Integer> m = new O<>();
    private int tid = 0;
    private final KMap<BlockPosition, BufferedImage> positions = new KMap<>();
    private final KMap<BlockPosition, BufferedImage> fastpositions = new KMap<>();
    private final KSet<BlockPosition> working = new KSet<>();
    private final KSet<BlockPosition> workingfast = new KSet<>();
    private final ExecutorService e = Executors.newFixedThreadPool(8, new ThreadFactory() {
        @Override
        public Thread newThread(Runnable r) {
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
        }
    });

    private final ExecutorService eh = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors(), new ThreadFactory() {
        @Override
        public Thread newThread(Runnable r) {
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
        }
    });

    public IrisVision() {
        m.set(8);
        renderer = new IrisRenderer(null);
        rs.put(1);
        addMouseWheelListener(this);
        addMouseMotionListener(new MouseMotionListener() {
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
        });
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

                if (m.get() >= 0) {
                    working.add(key);
                    double mk = mscale;
                    double mkd = scale;
                    e.submit(() ->
                    {
                        PrecisionStopwatch ps = PrecisionStopwatch.start();
                        BufferedImage b = renderer.render(x * mscale, z * mscale, div * mscale, div);
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

        if (workingfast.contains(key)) {
            return null;
        }

        m.set(m.get() - 1);

        if (m.get() >= 0) {
            workingfast.add(key);
            double mk = mscale;
            double mkd = scale;
            eh.submit(() ->
            {
                PrecisionStopwatch ps = PrecisionStopwatch.start();
                BufferedImage b = renderer.render(x * mscale, z * mscale, div * mscale, div / 12);
                rs.put(ps.getMilliseconds());
                workingfast.remove(key);

                if (mk == mscale && mkd == scale) {
                    fastpositions.put(key, b);
                }
            });
        }
        return null;
    }

    private double getWorldX(double screenX) {
        return (screenX + oxp) * mscale;
    }

    private double getWorldZ(double screenZ) {
        return (screenZ + ozp) * mscale;
    }

    private double getScreenX(double x) {
        return (oxp + x) / mscale;
    }

    private double getScreenZ(double z) {
        return (ozp + z) / mscale;
    }

    @Override
    public void paint(Graphics gx) {
        if (ox < oxp) {
            oxp -= Math.abs(ox - oxp) * 0.36;
        }

        if (ox > oxp) {
            oxp += Math.abs(oxp - ox) * 0.36;
        }

        if (oz < ozp) {
            ozp -= Math.abs(oz - ozp) * 0.36;
        }

        if (oz > ozp) {
            ozp += Math.abs(ozp - oz) * 0.36;
        }

        PrecisionStopwatch p = PrecisionStopwatch.start();
        Graphics2D g = (Graphics2D) gx;
        w = getWidth();
        h = getHeight();
        double vscale = scale;
        scale = w / 16D;

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
                            g.drawImage(t, i - ((posX / iscale) % (iscale)), j - ((posZ / iscale) % (iscale)), iscale, iscale, new ImageObserver() {
                                @Override
                                public boolean imageUpdate(Image img, int infoflags, int x, int y, int width, int height) {
                                    return true;
                                }
                            });
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

        for (Player i : world.getPlayers()) {
            g.setColor(Color.getHSBColor(RNG.r.f(), 1f, 1f));
            g.drawRect((int) getScreenX(i.getLocation().getX()), (int) getScreenZ(i.getLocation().getZ()), 4, 4);
        }

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

    private static void createAndShowGUI(Renderer r, int s, World world) {
        JFrame frame = new JFrame("Vision");
        IrisVision nv = new IrisVision();
        nv.world = world;
        nv.renderer = new IrisRenderer(r);
        frame.add(nv);
        frame.setSize(1440, 820);
        frame.setVisible(true);
        File file = Iris.getCached("Iris Icon", "https://raw.githubusercontent.com/VolmitSoftware/Iris/master/icon.png");

        if (file != null) {
            try {
                frame.setIconImage(ImageIO.read(file));
            } catch (IOException e) {

            }
        }
    }

    public static void launch(IrisAccess g, int i) {
        J.a(() ->
        {
            createAndShowGUI((x, z) -> g.getEngineAccess(i).draw(x, z), i, g.getCompound().getWorld());
        });
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
        mscale = mscale < 0.00001 ? 0.00001 : mscale;
    }
}
