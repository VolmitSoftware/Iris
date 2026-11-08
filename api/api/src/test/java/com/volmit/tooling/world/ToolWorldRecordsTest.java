package com.volmit.tooling.world;

import com.volmit.block.BlockState;
import com.volmit.geometry.BlockPosition;
import com.volmit.geometry.ChunkPos;
import com.volmit.resource.ResourceId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ToolWorldRecordsTest {
    private static final ResourceId STICK = ResourceId.parse("minecraft:stick");
    private static final ToolItem ICON = new ToolItem(STICK, "Tool", List.of("line"), false, "");

    @Test
    void aChunkIsTheBlockCoordinatesDividedBySixteenRoundedDown() {
        assertEquals(new ChunkPos(0, 0), ChunkPos.containing(new BlockPosition(15, 70, 0)));
        assertEquals(new ChunkPos(1, -1), ChunkPos.containing(new BlockPosition(16, 0, -1)));
        assertEquals(new ChunkPos(-2, -1), ChunkPos.containing(new BlockPosition(-17, 0, -16)));
    }

    @Test
    void aToolWorldNameIsLowerCaseWordsOfLettersDigitsUnderscoresAndDashes() {
        ChunkPainter painter = (x, z, canvas) -> { };
        var listener = new ToolWorldListener() { };

        assertEquals("jigsaw-1_a", new ToolWorldSpec("jigsaw-1_a", painter, listener).name());
        assertThrows(IllegalArgumentException.class, () -> new ToolWorldSpec("Jigsaw", painter, listener));
        assertThrows(IllegalArgumentException.class, () -> new ToolWorldSpec("two words", painter, listener));
        assertThrows(IllegalArgumentException.class, () -> new ToolWorldSpec("", painter, listener));
        assertThrows(IllegalArgumentException.class, () -> new ToolWorldSpec("-dash", painter, listener));
        assertThrows(NullPointerException.class, () -> new ToolWorldSpec("name", null, listener));
        assertThrows(NullPointerException.class, () -> new ToolWorldSpec("name", painter, null));
    }

    @Test
    void aToolItemKeepsItsOwnCopyOfTheLore() {
        var lore = new ArrayList<>(List.of("one"));
        var item = new ToolItem(STICK, "Name", lore, true, "payload");
        lore.add("two");

        assertEquals(List.of("one"), item.lore());
        assertThrows(UnsupportedOperationException.class, () -> item.lore().add("three"));
        assertThrows(NullPointerException.class, () -> new ToolItem(STICK, "Name", List.of(), false, null));
    }

    @Test
    void aMenuHasOneToSixRowsAndEveryButtonInsideThemOnItsOwnSlot() {
        var button = new MenuButton(17, ICON, click -> { });

        assertEquals(1, new ToolMenu("t", 1, List.of(new MenuButton(8, ICON, click -> { })), null).rows());
        assertEquals(2, new ToolMenu("t", 2, List.of(button), null).rows());
        assertThrows(IllegalArgumentException.class, () -> new ToolMenu("t", 0, List.of(), null));
        assertThrows(IllegalArgumentException.class, () -> new ToolMenu("t", 7, List.of(), null));
        assertThrows(IllegalArgumentException.class, () -> new ToolMenu("t", 1, List.of(button), null), "slot 17 is in the second row");
        assertThrows(IllegalArgumentException.class,
                () -> new ToolMenu("t", 2, List.of(button, new MenuButton(17, ICON, click -> { })), null));
        assertThrows(IllegalArgumentException.class, () -> new MenuButton(-1, ICON, click -> { }));
    }

    @Test
    void aMenuWithoutAnOnCloseHasOneThatDoesNothing() {
        var closed = new AtomicBoolean();
        var withHandler = new ToolMenu("t", 1, List.of(), () -> closed.set(true));
        var without = new ToolMenu("t", 1, List.of(), null);

        without.onClose().run();
        assertFalse(closed.get());
        withHandler.onClose().run();
        assertTrue(closed.get());
    }

    @Test
    void aBlockChangeNamesAtLeastOneBlock() {
        var position = new BlockPosition(1, 2, 3);
        var change = new BlockChange(BlockChange.Cause.PLACE, List.of(position), Optional.empty(),
                Optional.of(BlockState.parse("minecraft:stone")));

        assertEquals(List.of(position), change.positions());
        assertThrows(IllegalArgumentException.class,
                () -> new BlockChange(BlockChange.Cause.BREAK, List.of(), Optional.empty(), Optional.empty()));
    }

    @Test
    void aListenerThatImplementsNothingFreezesTheWorldAndAllowsEverythingElse() {
        var listener = new ToolWorldListener() { };
        var change = new BlockChange(BlockChange.Cause.PISTON, List.of(new BlockPosition(0, 0, 0)), Optional.empty(), Optional.empty());

        assertFalse(listener.blockChanging(change));
        assertTrue(listener.command(Optional.empty(), "gamemode creative"));
    }

    @Test
    void aParticleHasAnRgbColourAndAPositiveSize() {
        assertEquals(0xFF00FF, new Particle(0, 0, 0, 0xFF00FF, 1.5F).rgb());
        assertThrows(IllegalArgumentException.class, () -> new Particle(0, 0, 0, 0x1000000, 1F));
        assertThrows(IllegalArgumentException.class, () -> new Particle(0, 0, 0, -1, 1F));
        assertThrows(IllegalArgumentException.class, () -> new Particle(0, 0, 0, 0, 0F));
        assertThrows(IllegalArgumentException.class, () -> new Particle(0, 0, 0, 0, Float.NaN));
    }
}
