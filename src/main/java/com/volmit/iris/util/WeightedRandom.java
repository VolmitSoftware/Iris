package com.volmit.iris.util;

import java.util.Random;

public class WeightedRandom<T> {

    private KList<KeyPair<T, Integer>> weightedObjects = new KList<>();
    private Random random;
    private int totalWeight = 0;

    public WeightedRandom(Random random) {
        this.random = random;
    }

    public WeightedRandom() {
        this.random = new Random();
    }

    public void put(T object, int weight) {
        weightedObjects.add(new KeyPair<>(object, weight));
        totalWeight += weight;
    }

    public T pullRandom() {
        int pull = random.nextInt(totalWeight);
        int index = 0;
        while (pull > 0) {
            pull -= weightedObjects.get(index).getV();
            index++;
        }
        return weightedObjects.get(index).getK();
    }

    public void shuffle() {
        weightedObjects.shuffle(random);
    }
}
