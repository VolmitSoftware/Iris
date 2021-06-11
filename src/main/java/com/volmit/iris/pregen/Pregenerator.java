package com.volmit.iris.pregen;

import com.volmit.iris.Iris;
import com.volmit.iris.IrisSettings;
import com.volmit.iris.scaffold.IrisWorlds;
import com.volmit.iris.scaffold.engine.IrisAccess;
import com.volmit.iris.scaffold.parallel.BurstExecutor;
import com.volmit.iris.scaffold.parallel.MultiBurst;
import com.volmit.iris.util.*;
import io.papermc.lib.PaperLib;
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
import java.awt.HeadlessException;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantLock;

@Data
public class Pregenerator implements Listener
{
	private static Pregenerator instance;
	private static final Color COLOR_MCA_PREPARE = Color.decode("#16211d");
	private static final Color COLOR_MCA_GENERATE = Color.decode("#34c0eb");
	private static final Color COLOR_MCA_GENERATE_SLOW = Color.decode("#34c0eb");
	private static final Color COLOR_MCA_GENERATE_SLOW_ASYNC = Color.decode("#34c0eb");
	private static final Color COLOR_MCA_GENERATED = Color.decode("#34eb83");
	private static final Color COLOR_MCA_SEALED = Color.decode("#34eb83");
	private static final Color COLOR_MCA_DEFERRED = Color.decode("#211617");
	private final World world;
	private final DirectWorldWriter directWriter;
	private final AtomicBoolean active;
	private final AtomicBoolean running;
	private final KList<Runnable> onComplete;
	private final ChunkPosition max;
	private final ChunkPosition min;
	private final MCAPregenGui gui;
	private final KList<ChunkPosition> mcaDefer;
	private final AtomicInteger generated;
	private final AtomicInteger generatedLast;
	private final RollingSequence perSecond;
	private final AtomicInteger totalChunks;
	private final AtomicLong memory;
	private final AtomicReference<String> memoryMetric;
	private final AtomicReference<String> method;
	private final AtomicInteger vmcax;
	private final AtomicInteger vmcaz;
	private final AtomicInteger vcax;
	private final AtomicInteger vcaz;
	private final long elapsed;
	private final ChronoLatch latch;

	public Pregenerator(World world, int blockSize, Runnable onComplete)
	{
		this(world, blockSize);
		this.onComplete.add(onComplete);
	}

	public Pregenerator(World world, int blockSize) throws HeadlessException
	{
		instance();
		latch = new ChronoLatch(5000);
		memoryMetric = new AtomicReference<>("...");
		method = new AtomicReference<>("STARTUP");
		memory = new AtomicLong(0);
		this.world = world;
		vmcax = new AtomicInteger();
		vmcaz = new AtomicInteger();
		vcax = new AtomicInteger();
		vcaz = new AtomicInteger();
		perSecond = new RollingSequence(20);
		generatedLast = new AtomicInteger(0);
		totalChunks = new AtomicInteger(0);
		generated = new AtomicInteger(0);
		mcaDefer = new KList<>();
		IrisAccess access = IrisWorlds.access(world);
		this.directWriter = new DirectWorldWriter(world.getWorldFolder());
		this.running = new AtomicBoolean(true);
		this.active = new AtomicBoolean(true);
		MultiBurst burst = new MultiBurst(Runtime.getRuntime().availableProcessors());
		int mcaSize = (((blockSize >> 4) + 2) >> 5) + 1;
		onComplete = new KList<>();
		max = new ChunkPosition(0,0);
		min = new ChunkPosition(0,0);
		KList<Runnable> draw = new KList<>();
		new Spiraler(mcaSize, mcaSize, (xx,zz) -> {
			min.setX(Math.min(xx << 5, min.getX()));
			min.setZ(Math.min(zz << 5, min.getZ()));
			max.setX(Math.max((xx << 5) + 31, max.getX()));
			max.setZ(Math.max((zz << 5) + 31, max.getZ()));
			totalChunks.getAndAdd(1024);
			draw.add(() -> drawMCA(xx, zz, COLOR_MCA_PREPARE));
		}).drain();
		gui = IrisSettings.get().getGui().isLocalPregenGui() && IrisSettings.get().getGui().isUseServerLaunchedGuis() ? MCAPregenGui.createAndShowGUI(this)  : null;
		flushWorld();
		KList<ChunkPosition> order = computeChunkOrder();
		Consumer3<Integer, Integer, Consumer2<Integer, Integer>> mcaIteration =
				(ox, oz, r) -> order.forEach((i)
						-> r.accept(i.getX() + ox, i.getZ() + oz));
		draw.forEach(Runnable::run);
		Spiraler spiraler = new Spiraler(mcaSize, mcaSize, (xx,zz) -> {
			vmcax.set(xx);
			vmcaz.set(zz);
			flushWorld();
			drawMCA(xx, zz, COLOR_MCA_PREPARE);
			if(access != null && generateMCARegion(xx, zz, burst, access, mcaIteration))
			{
				flushWorld();
			}

			else
			{
				drawMCA(xx, zz, COLOR_MCA_DEFERRED);
				mcaDefer.add(new ChunkPosition(xx, zz));
			}
		});

		elapsed = M.ms();

		new Thread(() -> {
			flushWorld();
			J.sleep(2000);
			flushWorld();

			while(running.get() && spiraler.hasNext())
			{
				if(active.get())
				{
					spiraler.next();
				}
			}

			mcaDefer.removeDuplicates();

			while(running.get() && mcaDefer.isNotEmpty())
			{
				ChunkPosition p = mcaDefer.popLast();
				vmcax.set(p.getX());
				vmcaz.set(p.getZ());
				generateDeferedMCARegion(p.getX(), p.getZ(), burst, mcaIteration);
				flushWorld();
				drawMCA(p.getX(), p.getZ(), COLOR_MCA_SEALED);
			}

			burst.shutdownNow();
			directWriter.flush();
			flushWorld();
			onComplete.forEach(Runnable::run);
			running.set(false);
			active.set(false);
			if(gui != null)
			{
				gui.close();
			}
		}).start();
		new Thread(() -> {
			PrecisionStopwatch p = PrecisionStopwatch.start();

			while(running.get() && active.get()) {
				int m = generated.get();
				int w = generatedLast.get();
				int up = m - w;
				double dur = p.getMilliseconds();
				perSecond.put((int) (up / (dur / 1000D)));
				p.reset();
				p.begin();
				updateProgress();
				generatedLast.set(m);
				J.sleep(100);
				long lmem = memory.get();
				memory.set(Runtime.getRuntime().freeMemory());

				if(memory.get() > lmem)
				{
					long free = memory.get();
					long max = Runtime.getRuntime().maxMemory();
					long total = Runtime.getRuntime().totalMemory();
					long use = total - free;
					memoryMetric.set(Form.memSize(use, 2) + " (" + Form.pc((double)use / (double)max, 0) + ")");
				}
			}
		}).start();
	}

	private boolean generateMCARegion(int x, int z, MultiBurst burst, IrisAccess access, Consumer3<Integer, Integer, Consumer2<Integer, Integer>> mcaIteration) {
		if(!Iris.instance.isMCA())
		{
			return false;
		}

		File mca = new File(world.getWorldFolder(), "region/r." + x + "." + z + ".mca");
		File mcg = directWriter.getMCAFile(x, z);
		Path fileToMovePath = Paths.get(mcg.toURI());
		Path targetPath = Paths.get(mca.toURI());
		BurstExecutor e = burst.burst(1024);
		int mcaox = x << 5;
		int mcaoz = z << 5;
		if(isMCAWritable(x,z) && !mca.exists())
		{
			method.set("Direct (Fast)");
			mcaIteration.accept(mcaox, mcaoz, (ii, jj) -> e.queue(() -> {
				draw(ii, jj, COLOR_MCA_GENERATE);
				access.directWriteChunk(world, ii, jj, directWriter);
				draw(ii, jj, COLOR_MCA_GENERATED);
				generated.getAndIncrement();
				vcax.set(ii);
				vcaz.set(jj);
			}));
			e.complete();
			directWriter.flush();
			if(!install(mcg, mca))
			{
				drawMCA(x, z, COLOR_MCA_DEFERRED);
				generated.set(generated.get() - 1024);
				mcaDefer.add(new ChunkPosition(x, z));
			}
		}

		else
		{
			mcaDefer.add(new ChunkPosition(x, z));
			e.complete();
			return false;
		}

		return true;
	}

	private boolean install(File from, File to) {
		try
		{
			Files.move(from.toPath(), to.toPath());
			return true;
		}

		catch(Throwable ignored)
		{

		}

		try {
			IO.copyFile(from, to);
			from.delete();
			return true;
		} catch (IOException ignored) {

		}

		return false;
	}

	public void updateProgress()
	{
		if(!latch.flip())
		{
			return;
		}

		String[] v = getProgress();
		Iris.info("Pregeneration " + v[0] + " | "  + v[1] + " | " + v[2] + " | " + v[3]);
	}

	private void generateDeferedMCARegion(int x, int z, MultiBurst burst, Consumer3<Integer, Integer, Consumer2<Integer, Integer>> mcaIteration) {
		BurstExecutor e = burst.burst(1024);
		int mcaox = x << 5;
		int mcaoz = z << 5;
		if(PaperLib.isPaper())
		{
			method.set("PaperAsync (Slow)");
			mcaIteration.accept(mcaox, mcaoz, (ii, jj) -> {
				e.queue(() -> {
					CompletableFuture<Chunk> cc = PaperLib.getChunkAtAsync(world, ii, jj);
					draw(ii, jj, COLOR_MCA_GENERATE_SLOW_ASYNC);
					cc.join();
					draw(ii, jj, COLOR_MCA_GENERATED);
					generated.getAndIncrement();
					vcax.set(ii);
					vcaz.set(jj);
				});
			});
			e.complete();
		}

		else
		{
			AtomicInteger m = new AtomicInteger();
			method.set("Spigot (Very Slow)");
			KList<Runnable> q = new KList<>();
			mcaIteration.accept(mcaox, mcaoz, (ii, jj) -> {
				q.add(() -> {
					draw(ii, jj, COLOR_MCA_GENERATE_SLOW);
					world.getChunkAt(ii, jj).load(true);
					draw(ii, jj, COLOR_MCA_GENERATED);
					m.getAndIncrement();
					generated.getAndIncrement();
					vcax.set(ii);
					vcaz.set(jj);
				});
			});
			ChronoLatch tick = new ChronoLatch(1000);
			new SR(0) {
				@Override
				public void run() {
					if(tick.flip())
					{
						return;
					}

					if(q.isEmpty())
					{
						cancel();
						return;
					}

					try
					{
						q.pop().run();
					}

					catch(Throwable e)
					{

					}
				}
			};

			while(m.get() < 1024)
			{
				J.sleep(25);
			}
		}
	}

	private KList<ChunkPosition> computeChunkOrder() {
		ChunkPosition center = new ChunkPosition(15, 15);
		KList<ChunkPosition> p = new KList<>();
		new Spiraler(33, 33, (x, z) -> {
			int xx = x + 15;
			int zz = z + 15;
			if(xx < 0 || xx > 31 || zz < 0 || zz > 31)
			{
				return;
			}

			p.add(new ChunkPosition(xx, zz));
		}).drain();
		p.sort(Comparator.comparing((i) -> i.distance(center)));
		return p;
	}

	public static Pregenerator getInstance()
	{
		return instance;
	}

	public static boolean shutdownInstance(){
		if(instance != null)
		{
			instance.shutdown();
			instance = null;
			return true;
		}

		return false;
	}

	public static void pauseResume() {
		instance.active.set(!instance.active.get());
	}

	public static boolean isPaused() {
		return instance.paused();
	}

	private void instance() {
		if(instance != null)
		{
			instance.shutdown();
		}

		instance = this;
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
			J.sleep(1);
		}
	}

	private void flushWorldSync()
	{
		for(Chunk i : world.getLoadedChunks())
		{
			i.unload(true);
		}

		world.save();
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
		long eta = (long) ((totalChunks.get() - generated.get()) * ((double)(M.ms() - elapsed) / (double) generated.get()));

		return new String[]{
				"Progress: " + Form.f(generated.get()) + " of " + Form.f(totalChunks.get()) + " (" + Form.pc((double)generated.get() / (double)totalChunks.get(), 0) + ")",
				"ETA: " + Form.duration(eta, 0),
				"Chunks/s: " + Form.f((int)perSecond.getAverage()),
				"Memory: " + memoryMetric.get(),
				"Cursor: " + "MCA(" + vmcax.get() + ", " + vmcaz.get() + ") @ (" + vcax.get() + ", " + vcaz.get() + ")",
				"Gen Mode: " + method.get(),
		};
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

			J.sleep((long) (IrisSettings.get().getGui().isMaximumPregenGuiFPS() ? 4 : 250));
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
		private static MCAPregenGui createAndShowGUI(Pregenerator j) throws HeadlessException
		{
			JFrame frame;
			try {
				frame = new JFrame("Pregen View");
			} catch (HeadlessException e) {
				throw e;
			}
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
				Pregenerator.pauseResume();
			}
		}

		public void close() {
			frame.setVisible(false);
		}
	}
}