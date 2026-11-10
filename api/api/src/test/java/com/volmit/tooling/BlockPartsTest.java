package com.volmit.tooling;

import com.volmit.geometry.BlockPosition;
import com.volmit.object.GeneratedObject;
import com.volmit.resource.ResourceId;
import com.volmit.world.PackWorld;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BlockPartsTest {
    private static final BlockPosition TRUNK = new BlockPosition(0, 1, 0);
    private static final BlockPosition DECORATION = new BlockPosition(2, 1, 0);

    @Test
    void answersNoPartForABlockNoPartPlaced() {
        BlockParts parts = new BlockParts(Map.of(TRUNK, 3));

        assertEquals(3, parts.partAt(TRUNK));
        assertEquals(BlockParts.NO_PART, parts.partAt(DECORATION));
    }

    @Test
    void keepsItsBlocksApartFromTheMapItWasGiven() {
        Map<BlockPosition, Integer> given = new HashMap<>(Map.of(TRUNK, 3));
        BlockParts parts = new BlockParts(given);
        given.clear();

        assertEquals(3, parts.partAt(TRUNK));
        assertThrows(UnsupportedOperationException.class, () -> parts.partsByBlock().clear());
    }

    @Test
    void refusesAPartBelowZero() {
        assertThrows(IllegalArgumentException.class, () -> new BlockParts(Map.of(TRUNK, BlockParts.NO_PART)));
    }

    @Test
    void aViewWithoutPartsSaysSoAndFindsNone() {
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
            public Optional<GeneratedObject> scene(PackWorld world, String key, int variant) {
                return Optional.empty();
            }
        };

        assertFalse(plain.hasParts());
        assertTrue(plain.parts(null, "any", 0).isEmpty());
    }
}
