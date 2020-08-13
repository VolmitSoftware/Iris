package com.volmit.iris;

import java.awt.Color;
import java.awt.EventQueue;
import java.awt.Graphics;
import java.awt.Graphics2D;

import javax.swing.JFrame;
import javax.swing.JPanel;

import com.volmit.iris.noise.CNG;
import com.volmit.iris.object.NoiseStyle;
import com.volmit.iris.util.M;
import com.volmit.iris.util.PrecisionStopwatch;
import com.volmit.iris.util.RNG;
import com.volmit.iris.util.RollingSequence;

public class NoiseView extends JPanel {

	private static final long serialVersionUID = 2094606939770332040L;

	RollingSequence r = new RollingSequence(60);
	CNG cng = NoiseStyle.CELLULAR_IRIS_DOUBLE.create(new RNG(RNG.r.nextLong())).scale(0.25);

	public NoiseView() {
		for (int i = 0; i < 60; i++) {
			r.put(10000);
		}
	}

	@Override
	public void paint(Graphics g) {
		super.paint(g);
		PrecisionStopwatch p = PrecisionStopwatch.start();
		int accuracy = M.clip(r.getAverage() / 32D, 1D, 128D).intValue();
		int dock = 0;

		if (g instanceof Graphics2D) {
			Graphics2D gg = (Graphics2D) g;

			int x = 0; // current position; x
			int y = 0; // current position; y
			int d = 0; // current direction; 0=RIGHT, 1=DOWN, 2=LEFT, 3=UP
			int c = 0; // counter
			int s = 1; // chain size

			// starting point
			x = ((int) Math.floor(getParent().getWidth() / 2.0)) - 1;
			y = ((int) Math.floor(getParent().getHeight() / 2.0)) - 1;

			for (int k = 1; k <= (getParent().getWidth() - 1); k++) {
				for (int j = 0; j < (k < (getParent().getHeight() - 1) ? 2 : 3); j++) {
					for (int i = 0; i < s; i++) {
						double n = cng.noise(x, Math.sin((double) M.ms() / 10000D) * 400D, y);

						if (n > 1 || n < 0) {
							System.out.println("EXCEEDED " + n);
							break;
						}

						Color color = Color.getHSBColor((float) (n), 1f - (float) (n * n * n * n * n * n),
								1f - (float) n);
						gg.setColor(color);
						gg.fillRect(x, y, accuracy, accuracy);

						c++;

						switch (d) {
						case 0:
							y = y + 1;
							break;
						case 1:
							x = x + 1;
							break;
						case 2:
							y = y - 1;
							break;
						case 3:
							x = x - 1;
							break;
						}
					}
					d = (d + 1) % 4;
				}
				s = s + 1;
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
