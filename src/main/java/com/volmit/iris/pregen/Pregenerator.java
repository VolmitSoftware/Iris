package com.volmit.iris.pregen;

import com.volmit.iris.Iris;
import com.volmit.iris.IrisSettings;
import com.volmit.iris.scaffold.engine.IrisAccess;
import com.volmit.iris.scaffold.parallel.BurstExecutor;
import com.volmit.iris.scaffold.parallel.MultiBurst;
import com.volmit.iris.util.*;
import lombok.Data;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.World;
import org.bukkit.event.Listener;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.image.BufferedImage;
import java.awt.image.ImageObserver;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;

@Data
public class Pregenerator implements Listener
{
	private static final Color COLOR_MCA_PREPARE = Color.DARK_GRAY;
	private static final Color COLOR_MCA_GENERATE = Color.MAGENTA;
	private static final Color COLOR_MCA_GENERATE_SLOW = Color.MAGENTA.darker().darker();
	private static final Color COLOR_MCA_GENERATED = Color.CYAN;
	private static final Color COLOR_MCA_SEALED = Color.GREEN;
	private static final Color COLOR_MCA_SEALING = Color.GREEN.darker().darker();
	private final World world;
	private final DirectWorldWriter directWriter;
	private final AtomicBoolean active;
	private final AtomicBoolean running;
	private final KList<Runnable> onComplete;
	private final ChunkPosition max;
	private final ChunkPosition min;
	private final MCAPregenGui gui;

	public Pregenerator(World world, IrisAccess access, int blockSize, int xoffset, int zoffset)
	{
		this.world = world;
		this.directWriter = new DirectWorldWriter(world.getWorldFolder());
		this.running = new AtomicBoolean(true);
		this.active = new AtomicBoolean(true);
		MultiBurst burst = new MultiBurst(Runtime.getRuntime().availableProcessors());
		int mcaSize = (((blockSize >> 4) + 2) >> 5) + 2;
		int xaoff = (xoffset >> 4) >> 5;
		int zaoff = (zoffset >> 4) >> 5;
		onComplete = new KList<>();
		max = new ChunkPosition(xoffset + (blockSize/2), zoffset + (blockSize/2));
		min = new ChunkPosition(xoffset - (blockSize/2), zoffset - (blockSize/2));
		gui = IrisSettings.get().isLocalPregenGui() && IrisSettings.get().isUseServerLaunchedGuis() ? MCAPregenGui.createAndShowGUI(this)  : null;
		Spiraler spiraler = new Spiraler(mcaSize, mcaSize, (xx,zz) -> {
			int x = xaoff + xx;
			int z = zaoff + zz;
			try {
				flushWorld();
				drawMCA(x, z, COLOR_MCA_PREPARE);
				File mca = new File(world.getWorldFolder(), "region/r." + x + "." + z + ".mca");
				File mcg = directWriter.getMCAFile(x, z);
				Path fileToMovePath = Paths.get(mcg.toURI());
				Path targetPath = Paths.get(mca.toURI());
				BurstExecutor e = burst.burst(1024);
				int mcaox = x << 5;
				int mcaoz = z << 5;

				if(isMCAWritable(x,z) && !mcg.exists())
				{
					for(int i = 0; i < 32; i++)
					{
						int ii = i;
						for(int j = 0; j < 32; j++)
						{
							int jj = j;
							e.queue(() -> {
								draw(ii + mcaox, jj + mcaoz, COLOR_MCA_GENERATE);
								access.directWriteChunk(world, ii + mcaox, jj + mcaoz, directWriter);
								draw(ii + mcaox, jj + mcaoz, COLOR_MCA_GENERATED);
							});
						}
					}

					directWriter.flush();
					Files.move(fileToMovePath, targetPath);
				}

				else
				{
					for(int i = 0; i < 32; i++)
					{
						int ii = i;
						for(int j = 0; j < 32; j++)
						{
							int jj = j;
							e.queue(() -> {
								draw(ii + mcaox, jj + mcaoz, COLOR_MCA_GENERATE_SLOW);
								access.generatePaper(world, ii+mcaox, jj + mcaoz);
								draw(ii + mcaox, jj + mcaoz, COLOR_MCA_GENERATED);
							});
						}
					}
				}

				e.complete();
				drawMCA(x, z, COLOR_MCA_SEALED);
				flushWorld();
				drawMCA(x, z, COLOR_MCA_SEALED);
			} catch (IOException e) {
				e.printStackTrace();
			}
		});
		new Thread(() -> {
			while(running.get() && spiraler.hasNext())
			{
				if(active.get())
				{
					spiraler.next();
				}
			}

			burst.shutdownNow();
			directWriter.flush();
			flushWorld();
			onComplete.forEach(Runnable::run);

			if(gui != null)
			{
				gui.close();
			}
		});
	}

	public void shutdown()
	{
		running.set(false);
		active.set(false);
	}

	private void draw(int cx, int cz, Color color)
	{
		if(gui != null)
		{
			gui.func.accept(new ChunkPosition(cx, cz), color);
		}
	}

	private void drawMCA(int cx, int cz, Color color)
	{
		for(int i = 0; i < 32; i++)
		{
			for(int j = 0; j < 32; j++)
			{
				draw((cx << 5) + i, (cz << 5) + j, color);
			}
		}
	}

	private void flushWorld()
	{
		if(Bukkit.isPrimaryThread())
		{
			flushWorldSync();
			return;
		}

		AtomicBoolean b = new AtomicBoolean(false);
		J.s(() -> {
			flushWorldSync();
			b.set(true);
		});

		while(!b.get())
		{
			J.sleep(10);
		}
	}

	private void flushWorldSync()
	{
		for(Chunk i : world.getLoadedChunks())
		{
			i.unload(true);
		}

		world.save();
		Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "save-all");
	}

	private boolean isMCAWritable(int x, int z) {
		File mca = new File(world.getWorldFolder(), "region/r." + x + "." + z + ".mca");

		if (mca.exists()) {
			return false;
		}

		for (Chunk i : world.getLoadedChunks())
		{
			if(i.getX() >> 5 == x && i.getZ() >> 5 == z)
			{
				return false;
			}
		}

		return true;
	}

	public String[] getProgress() {
		return new String[]{"Derp"};
	}

	public boolean paused() {
		return !active.get();
	}

	public static class MCAPregenGui extends JPanel implements KeyListener
	{
		private Pregenerator job;
		private static final long serialVersionUID = 2094606939770332040L;
		private KList<Runnable> order = new KList<>();
		private int res = 512;
		Graphics2D bg;
		private ReentrantLock l;
		private BufferedImage image = new BufferedImage(res, res, BufferedImage.TYPE_INT_RGB);
		private Consumer2<ChunkPosition,Color> func;
		private JFrame frame;

		public MCAPregenGui()
		{

		}

		public void paint(int x, int z, Color c)
		{
			func.accept(new ChunkPosition(x, z), c);
		}

		@Override
		public void paint(Graphics gx)
		{
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

			J.sleep((long) (IrisSettings.get().isMaximumPregenGuiFPS() ? 4 : 250));
			repaint();
		}

		private void draw(ChunkPosition p, Color c, Graphics2D bg)
		{
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

		@SuppressWarnings("deprecation")
		private static MCAPregenGui createAndShowGUI(Pregenerator j)
		{
			JFrame frame = new JFrame("Pregen View");
			MCAPregenGui nv = new MCAPregenGui();
			frame.addKeyListener(nv);
			nv.l = new ReentrantLock();
			nv.frame = frame;
			nv.job = j;
			nv.func = (c, b) ->
			{
				if(b.equals(Color.pink) && c.equals(new ChunkPosition(Integer.MAX_VALUE, Integer.MAX_VALUE)))
				{
					frame.hide();
				}
				nv.l.lock();
				nv.order.add(() -> nv.draw(c, b, nv.bg));
				nv.l.unlock();
			};
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

			return nv;
		}

		public static void launch(Pregenerator g)
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

		public void close() {
			frame.setVisible(false);
		}
	}
}