package com.volmit.iris.manager.gui;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.EventQueue;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionListener;
import java.awt.event.MouseWheelEvent;
import java.awt.event.MouseWheelListener;
import java.io.File;
import java.io.IOException;
import java.util.concurrent.locks.ReentrantLock;

import javax.imageio.ImageIO;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLayeredPane;
import javax.swing.JPanel;
import javax.swing.JViewport;

import com.volmit.iris.Iris;
import com.volmit.iris.generator.noise.CNG;
import com.volmit.iris.object.NoiseStyle;
import com.volmit.iris.util.Function2;
import com.volmit.iris.util.GroupedExecutor;
import com.volmit.iris.util.J;
import com.volmit.iris.util.KList;
import com.volmit.iris.util.M;
import com.volmit.iris.util.PrecisionStopwatch;
import com.volmit.iris.util.RNG;
import com.volmit.iris.util.RollingSequence;

public class NoiseExplorer extends JPanel implements MouseWheelListener
{

	private static final long serialVersionUID = 2094606939770332040L;

	static JComboBox<String> combo;
	RollingSequence r = new RollingSequence(90);
	boolean colorMode = true;
	double scale = 1;
	static boolean hd = false;
	static double ascale = 10;
	CNG cng = NoiseStyle.STATIC.create(new RNG(RNG.r.nextLong()));
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
	static boolean down = false;
	double lx = Double.MAX_VALUE; //MouseX
	double lz = Double.MAX_VALUE; //MouseY
	double t;
	double tz;

	public NoiseExplorer()
	{
		addMouseWheelListener(this);
		addMouseMotionListener(new MouseMotionListener()
		{
			@Override
			public void mouseMoved(MouseEvent e)
			{
				Point cp = e.getPoint();

				lx = (cp.getX());
				lz = (cp.getY());
				mx = lx;
				mz = lz;
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

	public void mouseWheelMoved(MouseWheelEvent e)
	{

		int notches = e.getWheelRotation();
		if(e.isControlDown())
		{
			t = t + ((0.0025 * t) * notches);
			return;
		}

		scale = scale + ((0.044 * scale) * notches);
		scale = Math.max(scale, 0.00001);
	}

	@Override
	public void paint(Graphics g)
	{
		if(scale < ascale)
		{
			ascale -= Math.abs(scale - ascale) * 0.16;
		}

		if(scale > ascale)
		{
			ascale += Math.abs(ascale - scale) * 0.16;
		}

		if(t < tz)
		{
			tz -= Math.abs(t - tz) * 0.29;
		}

		if(t > tz)
		{
			tz += Math.abs(tz - t) * 0.29;
		}

		if(ox < oxp)
		{
			oxp -= Math.abs(ox - oxp) * 0.16;
		}

		if(ox > oxp)
		{
			oxp += Math.abs(oxp - ox) * 0.16;
		}

		if(oz < ozp)
		{
			ozp -= Math.abs(oz - ozp) * 0.16;
		}

		if(oz > ozp)
		{
			ozp += Math.abs(ozp - oz) * 0.16;
		}

		if(mx < mxx)
		{
			mxx -= Math.abs(mx - mxx) * 0.16;
		}

		if(mx > mxx)
		{
			mxx += Math.abs(mxx - mx) * 0.16;
		}

		if(mz < mzz)
		{
			mzz -= Math.abs(mz - mzz) * 0.16;
		}

		if(mz > mzz)
		{
			mzz += Math.abs(mzz - mz) * 0.16;
		}

		PrecisionStopwatch p = PrecisionStopwatch.start();
		int accuracy = hd ? 1 : M.clip((r.getAverage() / 6D), 1D, 128D).intValue();
		accuracy = down ? accuracy * 4 : accuracy;
		int v = 1000;

		if(g instanceof Graphics2D)
		{
			Graphics2D gg = (Graphics2D) g;

			if(getParent().getWidth() != w || getParent().getHeight() != h)
			{
				w = getParent().getWidth();
				h = getParent().getHeight();
				co = null;
			}

			if(co == null)
			{
				co = new int[w][h];
			}

			for(int x = 0; x < w; x += accuracy)
			{
				int xx = x;

				for(int z = 0; z < h; z += accuracy)
				{
					int zz = z;
					gx.queue("a", () ->
					{
						double n = generator != null ? generator.apply((xx * ascale) + oxp, (zz * ascale) + ozp) : cng.noise((xx * ascale) + oxp, tz, (zz * ascale) + ozp);

						if(n > 1 || n < 0)
						{
							return;
						}

						Color color = colorMode ? Color.getHSBColor((float) (n), 1f - (float) (n * n * n * n * n * n), 1f - (float) n) : Color.getHSBColor(0f, 0f, (float) n);
						int rgb = color.getRGB();
						co[xx][zz] = rgb;
					});
				}

				gx.waitFor("a");

				if(hd && p.getMilliseconds() > v)
				{
					break;
				}
			}

			for(int x = 0; x < getParent().getWidth(); x += accuracy)
			{
				for(int z = 0; z < getParent().getHeight(); z += accuracy)
				{
					gg.setColor(new Color(co[x][z]));
					gg.fillRect(x, z, accuracy, accuracy);
				}
			}
		}

		p.end();

		t += 1D;
		r.put(p.getMilliseconds());

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

		EventQueue.invokeLater(() ->
		{
			J.sleep((long) Math.max(0, 32 - r.getAverage()));
			repaint();
		});
	}

	private static void createAndShowGUI(Function2<Double, Double, Double> gen, String genName)
	{
		JFrame frame = new JFrame("Noise Explorer: " + genName);
		NoiseExplorer nv = new NoiseExplorer();
		frame.setDefaultCloseOperation(JFrame.HIDE_ON_CLOSE);
		JLayeredPane pane = new JLayeredPane();
		nv.setSize(new Dimension(1440, 820));
		pane.add(nv, 1, 0);
		nv.generator = gen;
		frame.add(pane);
		File file = Iris.getCached("Iris Icon", "https://raw.githubusercontent.com/VolmitSoftware/Iris/master/icon.png");

		if(file != null)
		{
			try
			{
				frame.setIconImage(ImageIO.read(file));
			}
			catch(IOException ignored) { }
		}
		frame.setSize(1440, 820);
		frame.setVisible(true);
	}

	private static void createAndShowGUI()
	{
		JFrame frame = new JFrame("Noise Explorer");
		NoiseExplorer nv = new NoiseExplorer();
		frame.setDefaultCloseOperation(JFrame.HIDE_ON_CLOSE);
		KList<String> li = new KList<NoiseStyle>(NoiseStyle.values()).toStringList();
		combo = new JComboBox<String>(li.toArray(new String[li.size()]));
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

		if(file != null)
		{
			try
			{
				frame.setIconImage(ImageIO.read(file));
			}
			catch(IOException ignored) { }
		}
		frame.setSize(1440, 820);
		frame.setVisible(true);
	}

	public static void launch(Function2<Double, Double, Double> gen, String genName)
	{
		EventQueue.invokeLater(() -> createAndShowGUI(gen, genName));
	}

	public static void launch()
	{
		EventQueue.invokeLater(() -> createAndShowGUI());
	}

	static class HandScrollListener extends MouseAdapter
	{
		private static final Point pp = new Point();

		@Override
		public void mouseDragged(MouseEvent e)
		{
			JViewport vport = (JViewport) e.getSource();
			JComponent label = (JComponent) vport.getView();
			Point cp = e.getPoint();
			Point vp = vport.getViewPosition();
			vp.translate(pp.x - cp.x, pp.y - cp.y);
			label.scrollRectToVisible(new Rectangle(vp, vport.getSize()));

			pp.setLocation(cp);
		}

		@Override
		public void mousePressed(MouseEvent e)
		{
			pp.setLocation(e.getPoint());
		}
	}
}