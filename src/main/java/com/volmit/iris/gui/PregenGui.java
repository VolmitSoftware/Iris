package com.volmit.iris.gui;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.image.BufferedImage;
import java.awt.image.ImageObserver;
import java.io.File;
import java.io.IOException;
import java.util.concurrent.locks.ReentrantLock;

import javax.imageio.ImageIO;
import javax.swing.JFrame;
import javax.swing.JPanel;

import com.volmit.iris.Iris;
import com.volmit.iris.IrisSettings;
import com.volmit.iris.util.ChunkPosition;
import com.volmit.iris.util.J;
import com.volmit.iris.util.KList;
import com.volmit.iris.util.M;
import com.volmit.iris.util.PregenJob;

public class PregenGui extends JPanel implements KeyListener
{
	private PregenJob job;
	private static final long serialVersionUID = 2094606939770332040L;
	private KList<Runnable> order = new KList<>();
	private int res = 512;
	Graphics2D bg;
	double minC;
	double maxC;
	private ReentrantLock l;
	private BufferedImage image = new BufferedImage(res, res, BufferedImage.TYPE_INT_RGB);

	public PregenGui()
	{

	}

	@Override
	public void paint(Graphics gx)
	{
		minC = Math.floorDiv(job.min(), 16) - 4;
		maxC = Math.floorDiv(job.max(), 16) + 4;
		Graphics2D g = (Graphics2D) gx;
		bg = (Graphics2D) image.getGraphics();

		l.lock();
		while(order.isNotEmpty())
		{
			try
			{
				order.pop().run();
			}

			catch(Throwable e)
			{

			}
		}
		l.unlock();

		g.drawImage(image, 0, 0, getParent().getWidth(), getParent().getHeight(), new ImageObserver()
		{
			@Override
			public boolean imageUpdate(Image img, int infoflags, int x, int y, int width, int height)
			{
				return true;
			}
		});

		g.setColor(Color.WHITE);
		g.setFont(new Font("Hevetica", Font.BOLD, 28));
		String[] prog = job.getProgress();
		int h = g.getFontMetrics().getHeight() + 5;
		int hh = 20;

		if(job.paused())
		{
			g.drawString("PAUSED", 20, hh += h);

			g.drawString("Press P to Resume", 20, hh += h);
		}

		else
		{
			for(String i : prog)
			{
				g.drawString(i, 20, hh += h);
			}

			g.drawString("Press P to Pause", 20, hh += h);
		}

		J.sleep((long) (IrisSettings.get().isMaxPregenGuiFPS() ? 4 : 250));
		repaint();
	}

	private void draw(ChunkPosition p, Color c, double minC, double maxC, Graphics2D bg)
	{
		double pw = M.lerpInverse(minC, maxC, p.getX());
		double ph = M.lerpInverse(minC, maxC, p.getZ());
		double pwa = M.lerpInverse(minC, maxC, p.getX() + 1);
		double pha = M.lerpInverse(minC, maxC, p.getZ() + 1);
		int x = (int) M.lerp(0, res, pw);
		int z = (int) M.lerp(0, res, ph);
		int xa = (int) M.lerp(0, res, pwa);
		int za = (int) M.lerp(0, res, pha);
		bg.setColor(c);
		bg.fillRect(x, z, xa - x, za - z);
	}

	@SuppressWarnings("deprecation")
	private static void createAndShowGUI(PregenJob j)
	{
		JFrame frame = new JFrame("Pregen View");
		PregenGui nv = new PregenGui();
		frame.addKeyListener(nv);
		nv.l = new ReentrantLock();
		nv.job = j;
		j.subscribe((c, b) ->
		{
			if(b.equals(Color.pink) && c.equals(new ChunkPosition(Integer.MAX_VALUE, Integer.MAX_VALUE)))
			{
				frame.hide();
			}
			nv.l.lock();
			nv.order.add(() -> nv.draw(c, b, nv.minC, nv.maxC, nv.bg));
			nv.l.unlock();
		});
		frame.add(nv);
		frame.setSize(1000, 1000);
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

	public static void launch(PregenJob g)
	{
		J.a(() ->
		{
			createAndShowGUI(g);
		});
	}

	@Override
	public void keyTyped(KeyEvent e)
	{

	}

	@Override
	public void keyPressed(KeyEvent e)
	{

	}

	@Override
	public void keyReleased(KeyEvent e)
	{
		if(e.getKeyCode() == KeyEvent.VK_P)
		{
			PregenJob.pauseResume();
		}
	}
}
