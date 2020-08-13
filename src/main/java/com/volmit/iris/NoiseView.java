package com.volmit.iris;

import java.awt.Color;
import java.awt.EventQueue;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.util.concurrent.locks.ReentrantLock;

import javax.swing.JFrame;
import javax.swing.JPanel;

import com.volmit.iris.noise.CNG;
import com.volmit.iris.object.NoiseStyle;
import com.volmit.iris.util.GroupedExecutor;
import com.volmit.iris.util.M;
import com.volmit.iris.util.PrecisionStopwatch;
import com.volmit.iris.util.RNG;
import com.volmit.iris.util.RollingSequence;

public class NoiseView extends JPanel {

	private static final long serialVersionUID = 2094606939770332040L;

	RollingSequence r = new RollingSequence(256);
	CNG cng = NoiseStyle.PERLIN_IRIS.create(new RNG(RNG.r.nextLong())).scale(0.25);
	GroupedExecutor gx = new GroupedExecutor(Runtime.getRuntime().availableProcessors(), Thread.MAX_PRIORITY,
			"Iris Renderer");
	ReentrantLock l = new ReentrantLock();
	int[][] co;
	int w = 0;
	int h = 0;

	public NoiseView() {
		for (int i = 0; i < 60; i++) {
			r.put(10000);
		}
	}

	@Override
	public void paint(Graphics g) {
		super.paint(g);
		PrecisionStopwatch p = PrecisionStopwatch.start();
		int accuracy = M.clip(r.getAverage() / 13D, 1D, 128D).intValue();

		if (g instanceof Graphics2D) {
			Graphics2D gg = (Graphics2D) g;

			if (getParent().getWidth() != w || getParent().getHeight() != h) {
				w = getParent().getWidth();
				h = getParent().getHeight();
				co = null;
			}

			if (co == null) {
				co = new int[getParent().getWidth()][getParent().getHeight()];
			}

			for (int x = 0; x < getParent().getWidth(); x += accuracy) {
				int xx = x;
				gx.queue("a", () -> {
					for (int z = 0; z < getParent().getHeight(); z += accuracy) {
						double n = cng.noise(xx, Math.sin((double) M.ms() / 10000D) * 400D, z);

						if (n > 1 || n < 0) {
							System.out.println("EXCEEDED " + n);
							break;
						}

						Color color = Color.getHSBColor((float) (n), 1f - (float) (n * n * n * n * n * n),
								1f - (float) n);
						int rgb = color.getRGB();
						co[xx][z] = rgb;
					}
				});
			}

			gx.waitFor("a");

			for (int x = 0; x < getParent().getWidth(); x += accuracy) {
				for (int z = 0; z < getParent().getHeight(); z += accuracy) {
					gg.setColor(new Color(co[x][z]));
					gg.fillRect(x, z, accuracy, accuracy);
				}
			}
		}

		p.end();
		r.put(p.getMilliseconds());

		EventQueue.invokeLater(() -> {
			repaint();
		});
	}

	private static void createAndShowGUI() {
		JFrame frame = new JFrame("Iris");
		frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		frame.add(new NoiseView());
		frame.setLocationByPlatform(true);
		frame.pack();
		frame.setSize(900, 500);
		frame.setVisible(true);
	}

	public static void main(String[] args) {
		EventQueue.invokeLater(new Runnable() {
			public void run() {
				createAndShowGUI();
			}
		});
	}

}
