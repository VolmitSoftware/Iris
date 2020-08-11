package com.volmit.iris.noise;

public class Test {

	public static void main(String[] args) {
		NoiseGenerator t = null;

		for (NoiseType i : NoiseType.values()) {
			System.out.println("Test: " + i.name());
			t = i.create(0);
			for (int j = 0; j < 100; j++) {
				System.out.println(t.noise(j * 1));
			}
		}
	}

}
