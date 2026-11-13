package com.volmit.tooling;

import java.util.List;
import java.util.Objects;

/**
 * The sizes a {@link SceneView} builds its scenes at, which the studio lets one choose between.
 *
 * @param blocksAcross each size, as how many blocks across the scene is, smallest first
 * @param opening      the size the studio shows first, one of {@code blocksAcross}
 */
public record SceneSizes(List<Integer> blocksAcross, int opening) {
    public SceneSizes {
        blocksAcross = List.copyOf(Objects.requireNonNull(blocksAcross, "blocksAcross"));
        if (blocksAcross.isEmpty()) {
            throw new IllegalArgumentException("Scene sizes need at least one size");
        }
        for (int at = 0; at < blocksAcross.size(); at++) {
            if (blocksAcross.get(at) < 1) {
                throw new IllegalArgumentException("A scene is at least 1 block across, not " + blocksAcross.get(at));
            }
            if (at > 0 && blocksAcross.get(at) <= blocksAcross.get(at - 1)) {
                throw new IllegalArgumentException("Scene sizes go from smallest to largest: " + blocksAcross);
            }
        }
        if (!blocksAcross.contains(opening)) {
            throw new IllegalArgumentException("The opening size " + opening + " is not one of " + blocksAcross);
        }
    }

    public boolean offers(int size) {
        return blocksAcross.contains(size);
    }
}
