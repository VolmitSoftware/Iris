package com.volmit.tooling;

import java.util.List;
import java.util.Objects;

/**
 * One timed bake of everything an entry is built from, as {@link SceneView#timeBake} reports it.
 *
 * @param variantCount how many variants the run baked
 * @param stages       the steps a variant is baked in, in order, each with its time added up over every variant
 */
public record BakeRun(int variantCount, List<Stage> stages) {
    public BakeRun {
        Objects.requireNonNull(stages, "stages");
        if (variantCount < 0) {
            throw new IllegalArgumentException("A bake run cannot have " + variantCount + " variants");
        }
        stages = List.copyOf(stages);
    }

    /**
     * @param label what the studio calls the step, such as {@code Paint}
     * @param nanos how long the step took, in nanoseconds
     */
    public record Stage(String label, long nanos) {
        public Stage {
            Objects.requireNonNull(label, "label");
            if (label.isBlank()) {
                throw new IllegalArgumentException("A bake stage needs a label");
            }
            if (nanos < 0L) {
                throw new IllegalArgumentException("Bake stage " + label + " took " + nanos + " ns; a time is never negative");
            }
        }
    }
}
