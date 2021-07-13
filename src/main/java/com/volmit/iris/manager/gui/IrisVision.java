package com.volmit.iris.manager.gui;

import com.volmit.iris.Iris;
import com.volmit.iris.scaffold.engine.IrisAccess;
import com.volmit.iris.util.*;
import io.lumine.xikage.mythicmobs.utils.cooldown.Cooldown;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.awt.image.ImageObserver;
import java.io.File;
import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

public class IrisVision extends JPanel implements MouseWheelListener, Listener
{
	private static final long serialVersionUID = 2094606939770332040L;
	private static final int[] qualitySteps = new int[]{
			25, 7, 1
	};
	private int tc = 8;
	private IrisRenderer renderer;
	private int posX = 0;
	private int posZ = 0;
	private double scale = 128;
	private double mscale = 1D;
	private int w = 0;
	private int h = 0;
	private World world;
	private double lx = Double.MAX_VALUE;
	private double lz = Double.MAX_VALUE;
	private double ox = 0;
	private double oz = 0;
	private double oxp = 0;
	private double ozp = 0;
	double tfps = 240D;
	private RollingSequence rs = new RollingSequence(512);
	private O<Integer> m = new O<>();
	private int tid = 0;
	private KSet<BlockPosition> cooldown = new KSet<>();
	private KMap<BlockPosition, TileRender> positions = new KMap<>();
	private KSet<BlockPosition> working = new KSet<>();
	private final ExecutorService e = Executors.newFixedThreadPool(tc, r -> {
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

	public IrisVision()
	{
		m.set(8);
		renderer = new IrisRenderer(null);
		rs.put(1);
		addMouseListener(new MouseListener() {
			@Override
			public void mouseClicked(MouseEvent e) {
				renderer.set(getWorldX(lx), getWorldZ(lz));
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
		});
		addMouseWheelListener(this);
		addMouseMotionListener(new MouseMotionListener()
		{
			@Override
			public void mouseMoved(MouseEvent e)
			{
				Point cp = e.getPoint();
				lx = (cp.getX());
				lz = (cp.getY());
			}

			@Override
			public void mouseDragged(MouseEvent e)
			{
				Point cp = e.getPoint();
				ox += (lx - cp.getX()) * scale;
				oz += (lz - cp.getY()) * scale;
				lx = cp.getX();
				lz = cp.getY();
			}
		});
	}

	private double getWorldX(double screenX)
	{
		return (screenX + oxp) * mscale;
	}

	private double getWorldZ(double screenZ)
	{
		return (screenZ + ozp) * mscale;
	}

	private double getScreenX(double x)
	{
		return (x / mscale) - oxp;
	}

	private double getScreenZ(double z)
	{
		return (z / mscale) - ozp;
	}

	private int getTileQuality(TileRender r, int div)
	{
		if(r == null)
		{
			return div / qualitySteps[0];
		}

		for(int i : qualitySteps)
		{
			if(r.getQuality() < div / i)
			{
				return div / i;
			}
		}

		return r.getQuality();
	}

	public BufferedImage getTile(KSet<BlockPosition> fg, int div, int x, int z, O<Integer> m)
	{
		BlockPosition key = new BlockPosition((int) mscale, Math.floorDiv(x, div), Math.floorDiv(z, div));
		fg.add(key);
		TileRender render = positions.get(key);

		if(render != null && getTileQuality(render, div) <= render.getQuality())
		{
			return render.getImage();
		}

		if(!cooldown.contains(key) && !working.contains(key) && working.size() < tc)
		{
			working.add(key);
			double mk = mscale;
			double mkd = scale;

			e.submit(() ->
			{
				PrecisionStopwatch ps = PrecisionStopwatch.start();
				int q = getTileQuality(render, div);
				BufferedImage b = renderer.render(x * mscale, z * mscale, div * mscale, q);
				rs.put(ps.getMilliseconds());
				working.remove(key);

				if(mk == mscale && mkd == scale)
				{
					TileRender r = render != null ? render : TileRender.builder()
							.image(b).quality(q)
							.build();
					r.setImage(b);
					r.setQuality(q);
					positions.put(key, r);
					cooldown.add(key);
				}
			});
		}

		return render != null ? render.getImage() : null;
	}

	@Override
	public void paint(Graphics gx)
	{
		if(ox < oxp)
		{
			oxp -= Math.abs(ox - oxp) * 0.36;
		}

		if(ox > oxp)
		{
			oxp += Math.abs(oxp - ox) * 0.36;
		}

		if(oz < ozp)
		{
			ozp -= Math.abs(oz - ozp) * 0.36;
		}

		if(oz > ozp)
		{
			ozp += Math.abs(ozp - oz) * 0.36;
		}

		PrecisionStopwatch p = PrecisionStopwatch.start();
		Graphics2D g = (Graphics2D) gx;
		w = getWidth();
		h = getHeight();
		double vscale = scale;
		scale = w / 16D;

		if(scale != vscale)
		{
			positions.clear();
		}

		KSet<BlockPosition> gg = new KSet<>();
		int iscale = (int) scale;
		g.setColor(Color.white);
		g.clearRect(0, 0, w, h);
		posX = (int) oxp;
		posZ = (int) ozp;
		m.set(3);
		boolean hasNull = false;

		for(int r = 0; r < Math.max(w, h); r += iscale)
		{
			for(int i = -iscale*4; i < w + (iscale*4); i += iscale)
			{
				for(int j = -iscale*4; j < h + (iscale*4); j += iscale)
				{
					int a = i - (w / 2);
					int b = j - (h / 2);
					if(a * a + b * b <= r * r)
					{
						BufferedImage t = getTile(gg, iscale, Math
								.floorDiv((posX / iscale) + i, iscale) * iscale, Math
								.floorDiv((posZ / iscale) + j, iscale) * iscale, m);

						if(t != null)
						{
							g.drawImage(t,
									i - ((posX / iscale) % (iscale)),
									j - ((posZ / iscale) % (iscale)),
									iscale,
									iscale,
									(img, infoflags, x, y, width, height) -> true);
						}

						else
						{
							hasNull = true;
						}
					}
				}
			}
		}

		if(!hasNull)
		{
			cooldown.clear();
		}

		p.end();

		for(BlockPosition i : positions.k())
		{
			if(!gg.contains(i))
			{
				positions.remove(i);
			}
		}

		g.setColor(Color.red);
		g.drawRect((int)lx, (int)lz, 3,3);

		for(Player i : world.getPlayers())
		{
			g.drawRect((int)getScreenX(i.getLocation().getX()), (int)getScreenZ(i.getLocation().getZ()), 3,3);
		}

		g.drawString("X: " + posX, 20, 20);
		g.drawString("Z: " + posZ, 20, 25 + g.getFont().getSize());

		if(!isVisible())
		{
			return;
		}

		if(!getParent().isVisible())
		{
			return;
		}

		if(!getParent().getParent().isVisible())
		{
			return;
		}

		J.a(() ->
		{
			J.sleep((long) 1);
			repaint();
		});
	}

	private static void createAndShowGUI(Renderer r, World world, int s)
	{
		JFrame frame = new JFrame("Vision");
		IrisVision nv = new IrisVision();
		nv.renderer = new IrisRenderer(r);
		nv.world = world;
		frame.add(nv);
		frame.setSize(1440, 820);
		frame.setVisible(true);
		File file = Iris.getCached("Iris Icon", "https://raw.githubusercontent.com/VolmitSoftware/Iris/master/icon.png");

		if(file != null)
		{
			try
			{
				frame.setIconImage(ImageIO.read(file));
			}

			catch(IOException e)
			{

			}
		}
	}

	public static void launch(IrisAccess g, int i) {
		J.a(() ->
		{
			createAndShowGUI((x, z) -> g.getEngineAccess(i).draw(x, z), g.getTarget().getWorld(), i);
		});
	}

	public void mouseWheelMoved(MouseWheelEvent e)
	{
		int notches = e.getWheelRotation();
		if(e.isControlDown())
		{
			return;
		}

		Iris.info("Blocks/Pixel: " + (mscale) + ", Blocks Wide: " + (w * mscale));
		positions.clear();
		cooldown.clear();
		mscale = mscale + ((0.044 * mscale) * notches);
		mscale = mscale < 0.00001 ? 0.00001 : mscale;
	}
}
