package com.volmit.iris.map;

import com.volmit.iris.Iris;
import com.volmit.iris.generator.IrisComplex;
import com.volmit.iris.util.J;
import com.volmit.iris.util.KMap;
import com.volmit.iris.util.KSet;
import com.volmit.iris.util.PrecisionStopwatch;
import com.volmit.iris.util.RandomColor;
import com.volmit.iris.util.RollingSequence;
import io.netty.util.internal.ConcurrentSet;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nullable;
import javax.imageio.ImageIO;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JPanel;
import java.awt.Color;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Point;
import java.awt.event.ComponentEvent;
import java.awt.event.ComponentListener;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionListener;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.stream.Collectors;

public class MapVision extends JPanel {

    private int threadId = 0;

    private static final int TILE_SIZE = 128;     //Tile size in pixels
    private static final int TILE_REALITY = 512;  //How many blocks a tile is
    private static final int TILE_SIZE_R = 7;     //The number of bits to shift to get the pixel side
    private static final int TILE_REALITY_R = 9;  //The number of bits to shift to get the real size

    private static final int DEF_WIDTH = 1440;
    private static final int DEF_HEIGHT = 820;


    private IrisComplex complex;
    private RenderType currentType = RenderType.BIOME_LAND;

    private int mouseX; //The current mouse coords
    private int mouseY;
    private double draggedOffsetX; //The amount the mouse has dragged the map
    private double draggedOffsetY;
    private int centerTileX; //The center tile in the screen
    private int centerTileY;
    private int offsetX; //Offset to draw tiles to
    private int offsetY;
    private int lastTileWidth;

    private boolean dirty = true; //Whether to repaint textures
    private double scale = 1;
    private boolean realname = false;

    private KMap<Integer, Tile> tiles = new KMap<>();

    private Set<Tile> visibleTiles = new ConcurrentSet<>();     //Tiles that are visible on screen
    private Set<Tile> halfDirtyTiles = new ConcurrentSet<>();   //Tiles that should be drawn next draw

    private short[][] spiral; //See #generateSpiral

    private final Color overlay = new Color(80, 80, 80);
    private final Font overlayFont = new Font("Arial", Font.BOLD, 16);

    private RollingSequence roll = new RollingSequence(50);

    private boolean debug = false;
    private int[] debugBorder = new int[] {-5, -3, 6, 4};

    private boolean recalculating;

    // IrisComplex is the main class I need for a biome map. You can make one from an Engine object,
    // which does need a FakeWorld object in it for the seed
    public MapVision(IrisComplex worldComplex)
    {
        this.complex = worldComplex;
        this.setBackground(Color.BLACK);
        this.setVisible(true);
        roll.put(1);
        generateSpiral(64);

        addMouseWheelListener((mouseWheelEvent) -> {
            double oldScale = this.scale;
            this.scale = Math.min(4, Math.max(scale + scale * mouseWheelEvent.getWheelRotation() * 0.2, 1));
            double wx = getWidth();
            double hy = getHeight();
            double xScale = (mouseX - wx) / wx * 0.5;
            double yScale = (mouseY - hy) / hy * 0.5;

            if (mouseWheelEvent.getWheelRotation() > 0) { //Only on zoom in, adjust the position to zoom into
                this.draggedOffsetX += xScale * (wx / 2) * (oldScale - scale);
                this.draggedOffsetY += yScale * (hy / 2) * (oldScale - scale);
            }

            dirty = true;
            repaint();
            softRecalculate();
        });
        addMouseMotionListener(new MouseMotionListener()
        {
            @Override
            public void mouseMoved(MouseEvent e)
            {
                Point cp = e.getPoint();
                mouseX = cp.x;
                mouseY = cp.y;
            }

            @Override
            public void mouseDragged(MouseEvent e)
            {
                Point cp = e.getPoint();
                draggedOffsetX -= (mouseX - cp.x) / scale;
                draggedOffsetY -= (mouseY - cp.y) / scale;
                mouseX = cp.x;
                mouseY = cp.y;
                softRecalculate();
                dirty = true;
            }
        });
        recalculate(); //Setup

    }

    /**
     * Open this GUI
     */
    public void open() {
        JFrame frame = new JFrame("Iris Map (" + complex.getData().getDataFolder().getName() + ")");
        frame.add(this);
        frame.setSize(DEF_WIDTH, DEF_HEIGHT);
        frame.setBackground(Color.BLACK);
        frame.addComponentListener(new ComponentListener() {

            @Override
            public void componentResized(ComponentEvent e) {
                dirty = true;
                softRecalculate();
                repaint();
            }

            @Override
            public void componentMoved(ComponentEvent e) {
                dirty = true;
                repaint();
            }

            @Override
            public void componentShown(ComponentEvent e) { }

            @Override
            public void componentHidden(ComponentEvent e) { }
        });
        frame.addKeyListener(new KeyListener() {
            @Override
            public void keyTyped(KeyEvent e) { }

            @Override
            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_SHIFT)
                    realname = true;
                else if (e.getKeyCode() == KeyEvent.VK_ALT) debug = !debug;
                else if (e.getKeyCode() == KeyEvent.VK_R) {
                    dirty = true;
                    repaint();
                }
            }

            @Override
            public void keyReleased(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_SHIFT)
                    realname = false;
            }
        });
        File file = Iris.getCached("Iris Icon", "https://raw.githubusercontent.com/VolmitSoftware/Iris/master/icon.png");

        if(file != null) {
            try {
                frame.setIconImage(ImageIO.read(file));
            } catch(IOException ignored) { }
        }

        frame.setVisible(true);
        frame.requestFocus();
        frame.toFront();
    }

    @Override
    public void paint(Graphics gx) {
        //super.paint(gx);
        PrecisionStopwatch stopwatch = PrecisionStopwatch.start();

        int windowOffsetX = getWidth() / 2;
        int windowOffsetY = getHeight() / 2;

        offsetX = (int) Math.round(draggedOffsetX * scale) + windowOffsetX;
        offsetY = (int) Math.round(draggedOffsetY * scale) + windowOffsetY;

        //If we should do a full repaint of the entire frame. Only done when the zoom level changes, etc
        if (dirty) {
            super.paint(gx); //Clear the frame first
            for (Iterator<Tile> iterator = visibleTiles.iterator(); iterator.hasNext();) {
                Tile tile = iterator.next();
                drawTile(gx, tile);
            }
            dirty = false;
        } else {
            //Loop through all the tiles that haven't been drawn last draw and draw them
            //This saves us having to do a FULL redraw when only 1 new tile has been added
            for (Iterator<Tile> iterator = halfDirtyTiles.iterator(); iterator.hasNext();) {
                Tile tile = iterator.next();
                drawTile(gx, tile);
                iterator.remove();
            }
        }

        gx.setColor(overlay);
        gx.fillRect(getWidth() - 400, 4, 396, 27);
        gx.setColor(Color.WHITE);
        //int x = (int) (((int) ((mouseX - windowOffsetX)) << 2) + (draggedOffsetX * scale));
        //int y = (int) (((int) ((mouseY - windowOffsetY)) << 2) + (draggedOffsetY * scale));
        int x = (int) (((int) ((mouseX - windowOffsetX))) + (-draggedOffsetX * scale)) << 2;
        int y = (int) (((int) ((mouseY - windowOffsetY))) + (-draggedOffsetY * scale)) << 2;
        String text = " [" + x+ ", " + y + "]";
        if (realname)
            text = complex.getLandBiomeStream().get(x, y).getLoadKey().toUpperCase() + text;
        else
            text = complex.getLandBiomeStream().get(x, y).getName().toUpperCase() + text;
        gx.setFont(overlayFont);
        gx.drawString(text, getWidth() - 400 + 6, 23);

        if (debug) {
            gx.setColor(Color.RED);
            int xx = (int) Math.round((debugBorder[0] << TILE_SIZE_R) / scale + offsetX);
            int yy = (int) Math.round((debugBorder[1] << TILE_SIZE_R) / scale + offsetY);
            int xx2 = (int) Math.round((debugBorder[2] << TILE_SIZE_R) / scale + offsetX);
            int yy2 = (int) Math.round((debugBorder[3] << TILE_SIZE_R) / scale + offsetY);
            gx.drawRect(xx, yy, xx2, yy2);
            gx.drawRect(xx-1, yy-1, xx2+1, yy2+1);
            gx.drawRect(xx-2, yy-2, xx2+2, yy2+2);


            gx.setColor(overlay);
            gx.fillRect(10, 10, 220, 200);
            gx.setColor(Color.WHITE);
            gx.drawString("Center [" + centerTileX + ", " + centerTileY + "]", 20, 25);
            gx.drawString((60 / (Math.max(roll.getAverage(), 1))) + " fps", 20, 45);
            gx.drawString("Width = " + lastTileWidth, 20, 65);
            gx.drawString("Dirty = " + dirty, 20, 85);
            gx.drawString("Scale = " + scale, 20, 105);
            gx.drawString("Tiles (Visible)" + visibleTiles.size(), 20, 125);
            gx.drawString("Tiles (Total)  " + tiles.size(), 20, 145);

            x = (int) (((int) ((mouseX - windowOffsetX))) + (-draggedOffsetX * scale)) >> TILE_SIZE_R;
            y = (int) (((int) ((mouseY - windowOffsetY))) + (-draggedOffsetY * scale)) >> TILE_SIZE_R;
            Tile t = getTile((short)x, (short)y);
            boolean b1 = t != null;
            boolean b2 = b1 && visibleTiles.contains(t);
            gx.drawString("Cursor Tile [" + x + ", " + y + "]", 20, 165);
            gx.drawString("Tile Details [" + String.valueOf(b1).toUpperCase() + ", " + String.valueOf(b2).toUpperCase() + "]", 20, 185);

        }

        stopwatch.end();
        roll.put(stopwatch.getMillis());

        /*J.a(() ->
        {
            J.sleep(1000 / targetFPS);
            repaint();
        });*/
        J.a(sleepTask);
    }

    public void drawTile(Graphics gx, Tile tile) {
        if (gx == null) return;

        int x = (int) Math.round((tile.getX() << TILE_SIZE_R) / scale + offsetX);
        int y = (int) Math.round((tile.getY() << TILE_SIZE_R) / scale + offsetY);
        //int x = (int) ((tile.getX() * TILE_SIZE) / scale + offsetX);
        //int y = (int) ((tile.getY() * TILE_SIZE) / scale + offsetY);

        int size = (int) (TILE_SIZE / scale);
        int off = (int) (TILE_SIZE % scale);
        gx.drawImage(tile.getImage(), x, y, size, size, null);
    }

    private Runnable sleepTask = new Runnable() {
        @Override
        public void run() {
            double t = Math.max(Math.min(roll.getAverage(), 1000), 30);
            J.sleep((long) t);
            repaint();
        }
    };

    /**
     * Check if we should do a full recalculation of what tiles should be visible
     */
    public void softRecalculate() {
        short x = (short) (((-draggedOffsetX * scale)) / TILE_SIZE * scale);
        short y = (short) (((-draggedOffsetY * scale)) / TILE_SIZE * scale);
        int xTiles = (((int)(getWidth() * scale) >> TILE_SIZE_R)) / 2 + 1;

        if (centerTileX != x || centerTileY != y || xTiles != lastTileWidth) {
            recalculate();
        }

        centerTileX = x;
        centerTileY = y;
    }

    /**
     * Recalculate what tiles should be visible on screen, as well as queue
     * new tiles to be created
     */
    public void recalculate() {
        PrecisionStopwatch stopwatch = PrecisionStopwatch.start();

        //Clears out the queue of existing tiles to do because we are redoing them anyway
        //If we don't do this, the queue gets so clogged that it literally takes up the
        //entire CPU with thread locking/unlocking
        executorService.getQueue().clear();

        int W = getWidth();
        int H = getHeight();

        if (W == 0|| H == 0) { //The window hasn't fully opened yet; assume defaults
            W = DEF_WIDTH;
            H = DEF_HEIGHT;
        }

        short centerTileX = (short) (((-draggedOffsetX * scale)) / TILE_SIZE * scale);
        short centerTileY = (short) (((-draggedOffsetY * scale)) / TILE_SIZE * scale);

        //Iris.info("Center is " + centerTileX + ", " + centerTileY);
        //Iris.info("Width is " + W + ", " + H);

        int woh = Math.max(W, H);
        int newSize = ((int)(woh * scale) >> TILE_SIZE_R) + 1;
        int checkSizeX =  (((int)(W * scale) >> TILE_SIZE_R)) / 2;
        int checkSizeY = (((int)(H * scale) >> TILE_SIZE_R)) / 2;
        lastTileWidth = checkSizeX;
        generateSpiral(newSize);

        Set<Integer> checked = new HashSet<>();
        Set<Integer> clone = new HashSet(visibleTiles.stream().map((t) ->
                getTileId(t.getX(), t.getY()))
                .collect(Collectors.toSet()));       //Clone the visible tiles

        if (debug) { //These are the 4 corners of the red line that shows the visibility check region for tiles
            debugBorder[0] = -checkSizeX + centerTileX;
            debugBorder[1] = -checkSizeY + centerTileY;
            debugBorder[2] = checkSizeX + 1 + centerTileX;
            debugBorder[3] = checkSizeY + 1 + centerTileY;
        }

        for (short[] coords : spiral) { //Start from the center of the spiral and work outwards to find new tiles to queue
            short x = (short)(coords[0] + centerTileX);
            short y = (short)(coords[1] + centerTileY);

            //When it goes offscreen, don't queue the tile by continuing
            if (Math.abs(coords[0]) > checkSizeX + 1) {
                continue;
            }
            if (Math.abs(coords[1]) > checkSizeY + 1) {
                continue;
            }

            int id = getTileId(x, y);

            //If the tile is not already made
            if (!tiles.containsKey(id)) {
                short[] c = getTileCoords(id);
                queue(c[0], c[1]); //Queue for creation
            } else {
                checked.add(id);
            }
        }

        clone.removeAll(checked);   //Remove the tiles that we know are onscreen

        for (int id : clone) { //Loop through the invisible tiles and mark them for removal from memory
            short[] c = getTileCoords(id);
            queueForRemoval(getTile(c[0], c[1]));
            //visibleTiles.remove(t);
        }

        stopwatch.end();
        roll.put(stopwatch.getMillis());
    }

    /**
     * Queue a tile for creation
     * @param tileX X tile coord
     * @param tileY Y tile coord
     */
    public void queue(short tileX, short tileY) {
        //If the tile still exists but just isn't visible
        if (tiles.containsKey(getTileId(tileX, tileY))) {
            Tile tile = getTile(tileX, tileY);
            if (visibleTiles.contains(tile)) return;

            visibleTiles.add(tile);
            halfDirtyTiles.add(tile); //Re-render it without doing a full repaint
            //dirty = true;
            return;
        }

        //I turned all lambda around here into objects just to see if they would
        //show up in timings instead of "$lambda". But they didn't. So it's not
        //not my code DIRECTLY. I believe the thing timings show is just to do
        //with threads stopping and starting/halting in the thread pool. Don't
        //know why or how to fix it, though

        /*executorService.execute(() -> {
            Tile tile = new Tile(tileX, tileY);
            tile.render(complex, currentType);
            tiles.put(getTileId(tileX, tileY), tile);
            visibleTiles.add(tile);
            dirty = true;
        });*/
        executorService.execute(queueTask(tileX, tileY));

    }

    public Runnable queueTask(short tileX, short tileY) {
        return new Runnable() {
            @Override
            public void run() {
                Tile tile = new Tile(tileX, tileY);
                tile.render(complex, currentType);
                tiles.put(getTileId(tileX, tileY), tile);
                visibleTiles.add(tile);
                //dirty = true; //Disabled marking as dirty so a redraw of the entire map isn't needed
                halfDirtyTiles.add(tile);
            }
        };
    }

    /**
     * Pend a tile for removal from the screen
     * @param tile The tile to remove
     */
    public void queueForRemoval(Tile tile) {
        //TODO Change from using the async task system as it may be putting strain on the server from being called so often
        J.a(() -> visibleTiles.remove(tile), 20); //Remove visibility in a bit

        J.a(() -> { //Remove it completely from memory after 5 seconds if it's still not visible
            if (!visibleTiles.contains(tile)) {
                tiles.remove(getTileId(tile.getX(), tile.getY()));
            }
        }, 20 * 6);
    }

    /**
     * Get a tile based on the X and Z coords of the tile
     * @param tileX X Coord
     * @param tileY Y Coord
     * @return
     */
    @Nullable
    public Tile getTile(short tileX, short tileY) {
        return tiles.get(getTileId(tileX, tileY));
    }

    /**
     * Get an integer that represents a tile's location
     * @param tileX X Coord
     * @param tileY Y Coord
     * @return
     */
    public int getTileId(short tileX, short tileY) {
        return tileX | tileY << 16;
    }

    /**
     * Converts an integer representing a tiles location back into 2 shorts
     * @param id The tile integer
     * @return
     */
    public short[] getTileCoords(int id) {
        return new short[] {(short) (id & 0x0000FFFF), (short) (id >> 16)};
    }

    /**
     * Generates a 2D array of relative tile locations. This is so we know what order
     * to search for new tiles in a nice, spiral way
     * @param size Size of the array
     */
    public void generateSpiral(int size) {
        if (size % 2 == 0) size++;
        short[][] newSpiral = new short[size * size][2];

        int x = 0; // current position; x
        int y = 0; // current position; y
        int d = 0; // current direction; 0=RIGHT, 1=DOWN, 2=LEFT, 3=UP
        int s = 1; // chain size
        int c = 0; // count

        // starting point
        x = ((int)(size/2.0))-1;
        y = ((int)(size/2.0))-1;
        int offset = (size / 2) - 1;

        for (int k=1; k<=(size-1); k++)
        {
            for (int j=0; j<(k<(size-1)?2:3); j++)
            {
                for (int i=0; i<s; i++)
                {
                    short[] coords = {(short) (x - offset), (short) (y - offset)};
                    newSpiral[c] = coords;
                    c++;
                    //Iris.info("Spiral " + coords[0] + ", " + coords[1]); //Testing

                    switch (d)
                    {
                        case 0: y = y + 1; break;
                        case 1: x = x + 1; break;
                        case 2: y = y - 1; break;
                        case 3: x = x - 1; break;
                    }
                }
                d = (d+1)%4;
            }
            s = s + 1;
        }

        spiral = newSpiral;
    }

    /*private final ExecutorService executorService = Executors.newFixedThreadPool(8, r -> {
        threadId++;
        Thread t = new Thread(r);
        t.setName("Iris Map Renderer " + threadId);
        t.setPriority(Thread.MIN_PRIORITY);
        t.setUncaughtExceptionHandler((et, e) ->
        {
            Iris.info("Exception encountered in " + et.getName());
            e.printStackTrace();
        });

        return t;
    });*/

    private ThreadFactory factory = new ThreadFactory() {
        @Override
        public Thread newThread(@NotNull Runnable r) {
            threadId++;
            Thread t = new Thread(r);
            t.setName("Iris Map Renderer " + threadId);
            t.setPriority(Thread.MIN_PRIORITY);
            t.setDaemon(true);
            t.setUncaughtExceptionHandler((et, e) ->
            {
                Iris.info("Exception encountered in " + et.getName());
                e.printStackTrace();
            });

            return t;
        }
    };

    //Our thread pool that draws the tiles for us
    private final ThreadPoolExecutor executorService = (ThreadPoolExecutor) Executors.newFixedThreadPool(8, factory);


}
