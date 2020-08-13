package com.volmit.iris.noise;

import com.volmit.iris.object.NoiseStyle;
import com.volmit.iris.util.RNG;

public class Research {

	public static void main(String[] args) {
		CNG cng = NoiseStyle.VIGOCTAVE_SIMPLEX.create(new RNG(RNG.r.nextLong()));

		double max = -1;
		double min = 2;

		for (int i = 0; i < 999999; i++) {
			double n = cng.noise(i, i * 2, i * 4);

			if (n < min) {
				min = n;
			}

			if (n > max) {
				max = n;
			}
		}

		System.out.println(min + " - " + max);
	}

}
