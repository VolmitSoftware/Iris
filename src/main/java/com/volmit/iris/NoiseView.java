package com.volmit.iris;

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
import java.util.concurrent.locks.ReentrantLock;

import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLayeredPane;
import javax.swing.JPanel;
import javax.swing.JViewport;

import com.volmit.iris.noise.CNG;
import com.volmit.iris.object.NoiseStyle;
import com.volmit.iris.util.GroupedExecutor;
import com.volmit.iris.util.M;
import com.volmit.iris.util.PrecisionStopwatch;
import com.volmit.iris.util.RNG;
import com.volmit.iris.util.RollingSequence;

public class NoiseView extends JPanel implements MouseWheelListener {

	private static final long serialVersionUID = 2094606939770332040L;

	static JComboBox<NoiseStyle> combo;
	RollingSequence r = new RollingSequence(60);
	boolean colorMode = true;
	double scale = 1;
	static boolean hd = false;
	double ascale = 10;
	CNG cng = NoiseStyle.STATIC.create(new RNG(RNG.r.nextLong()));
	GroupedExecutor gx = new GroupedExecutor(Runtime.getRuntime().availableProcessors(), Thread.MAX_PRIORITY,
			"Iris Renderer");
	ReentrantLock l = new ReentrantLock();
	int[][] co;
	int w = 0;
	int h = 0;
	double oxp = 0;
	double ozp = 0;
	double ox = 0;
	double oz = 0;
	boolean down = false;

	double lx = Double.MAX_VALUE;
	double lz = Double.MAX_VALUE;
	double tz = 1D;
	double t = 1D;

	public NoiseView() {

		addMouseWheelListener((MouseWheelListener) this);
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

	public void mouseWheelMoved(MouseWheelEvent e) {

		int notches = e.getWheelRotation();
		if (e.isControlDown()) {
			t = t + ((0.001 * t) * notches);
			return;
		}

		scale = scale + ((0.044 * scale) * notches);
		scale = scale < 0.00001 ? 0.00001 : scale;
	}

	@Override
	public void paint(Graphics g) {

		if (scale < ascale) {
			ascale -= Math.abs(scale - ascale) * 0.16;
		}

		if (scale > ascale) {
			ascale += Math.abs(ascale - scale) * 0.16;
		}

		if (t < tz) {
			tz -= Math.abs(t - tz) * 0.29;
		}

		if (t > tz) {
			tz += Math.abs(tz - t) * 0.29;
		}

		if (ox < oxp) {
			oxp -= Math.abs(ox - oxp) * 0.16;
		}

		if (ox > oxp) {
			oxp += Math.abs(oxp - ox) * 0.16;
		}

		if (oz < ozp) {
			ozp -= Math.abs(oz - ozp) * 0.16;
		}

		if (oz > ozp) {
			ozp += Math.abs(ozp - oz) * 0.16;
		}

		PrecisionStopwatch p = PrecisionStopwatch.start();
		int accuracy = hd ? 1 : M.clip((r.getAverage() / 13D) + 1, 1D, 128D).intValue();

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
						double n = cng.noise((xx * ascale) + oxp, tz, (z * ascale) + ozp);

						if (n > 1 || n < 0) {
							System.out.println("EXCEEDED " + n);
							break;
						}

						Color color = colorMode
								? Color.getHSBColor((float) (n), 1f - (float) (n * n * n * n * n * n), 1f - (float) n)
								: Color.getHSBColor(0f, 0f, (float) n);
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

		t += 1D;
		r.put(p.getMilliseconds());
		EventQueue.invokeLater(() -> {
			repaint();
		});
	}

	private static void createAndShowGUI() {
		JFrame frame = new JFrame("Iris");
		NoiseView nv = new NoiseView();
		frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		combo = new JComboBox<NoiseStyle>(NoiseStyle.values());
		combo.addActionListener(new ActionListener() {

			public void actionPerformed(ActionEvent e) {

				@SuppressWarnings("unchecked")
				NoiseStyle s = (NoiseStyle) (((JComboBox<NoiseStyle>) e.getSource()).getSelectedItem());
				nv.cng = s.create(RNG.r.nextParallelRNG(RNG.r.imax()));
			}
		});

		combo.setSize(500, 30);
		JLayeredPane pane = new JLayeredPane();
		nv.setSize(new Dimension(1440, 820));
		pane.add(nv, 1, 0);
		pane.add(combo, 2, 0);
		frame.add(pane);
		frame.setSize(1440, 820);
		frame.setVisible(true);
	}

	public static void main(String[] args) {
		EventQueue.invokeLater(new Runnable() {
			public void run() {
				createAndShowGUI();
			}
		});
	}

	static class HandScrollListener extends MouseAdapter {
		private static final Point pp = new Point();

		@Override
		public void mouseDragged(MouseEvent e) {
			JViewport vport = (JViewport) e.getSource();
			JComponent label = (JComponent) vport.getView();
			Point cp = e.getPoint();
			Point vp = vport.getViewPosition();
			vp.translate(pp.x - cp.x, pp.y - cp.y);
			label.scrollRectToVisible(new Rectangle(vp, vport.getSize()));

			pp.setLocation(cp);
		}

		@Override
		public void mousePressed(MouseEvent e) {
			pp.setLocation(e.getPoint());
		}
	}
}
