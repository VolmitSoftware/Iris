package com.volmit.tooling;

import com.volmit.object.GeneratedObject;
import com.volmit.resource.ResourceId;
import com.volmit.world.PackWorld;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BakeRunTest {
    @Test
    void keepsItsStagesInOrderAndApartFromTheListItWasGiven() {
        List<BakeRun.Stage> given = new ArrayList<>(List.of(new BakeRun.Stage("Parts", 5L), new BakeRun.Stage("Build", 7L)));
        BakeRun run = new BakeRun(3, given);
        given.clear();

        assertEquals(List.of("Parts", "Build"), run.stages().stream().map(BakeRun.Stage::label).toList());
        assertThrows(UnsupportedOperationException.class, () -> run.stages().clear());
    }

    @Test
    void refusesATimeBelowZeroAndAStageWithoutALabel() {
        assertThrows(IllegalArgumentException.class, () -> new BakeRun.Stage("Parts", -1L));
        assertThrows(IllegalArgumentException.class, () -> new BakeRun.Stage(" ", 1L));
        assertThrows(IllegalArgumentException.class, () -> new BakeRun(-1, List.of()));
    }

    @Test
    void aViewThatDoesNotBakeSaysSoAndTimesNothing() {
        SceneView plain = new SceneView() {
            @Override
            public ResourceId id() {
                return ResourceId.parse("test:plain");
            }

            @Override
            public String label() {
                return "Plain";
            }

            @Override
            public String description() {
                return "Plain";
            }

            @Override
            public List<SceneEntry> entries(PackWorld world) {
                return List.of();
            }

            @Override
            public Optional<GeneratedObject> scene(PackWorld world, SceneRequest request) {
                return Optional.empty();
            }
        };

        assertFalse(plain.isBaked());
        assertTrue(plain.timeBake(null, "any").isEmpty());
    }
}
