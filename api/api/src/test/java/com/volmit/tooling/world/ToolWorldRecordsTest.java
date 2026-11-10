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
    private static final ToolItem ICON = new ToolItem(STICK, ToolText.plain("Tool"), List.of(ToolText.plain("line")), false, "");

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
    void aListenerThatSaysNothingAboutClosingLetsTheWorldCloseAtOnce() {
        var listener = new ToolWorldListener() { };

        assertEquals(java.util.Optional.empty(), listener.closeRefusal());
        assertEquals(true, listener.closing(java.util.Optional.empty()).isDone());
        assertEquals(false, listener.closing(java.util.Optional.empty()).isCompletedExceptionally());
    }

    @Test
    void aToolItemKeepsItsOwnCopyOfTheLore() {
        var lore = new ArrayList<>(List.of(ToolText.plain("one")));
        var item = new ToolItem(STICK, ToolText.plain("Name"), lore, true, "payload");
        lore.add(ToolText.plain("two"));

        assertEquals(List.of(ToolText.plain("one")), item.lore());
        assertThrows(UnsupportedOperationException.class, () -> item.lore().add(ToolText.plain("three")));
        assertThrows(NullPointerException.class, () -> new ToolItem(STICK, ToolText.plain("Name"), List.of(), false, null));
    }

    @Test
    void textIsSpansOfColourAndStyleAndAdjoiningSpansOfOneStyleJoin() {
        var text = ToolText.builder().gray("Capacity: ").gray("16x16x16").white(" now").append(" plain").build();

        assertEquals(3, text.spans().size());
        assertEquals("Capacity: 16x16x16 now plain", text.plainText());
        assertEquals(new ToolText.Span("Capacity: 16x16x16", TextColor.GRAY, false, false, false), text.spans().getFirst());
        assertNull(text.spans().getLast().color());
    }

    @Test
    void emptyTextAddsNoSpanAndPlainTextOfNothingIsEmpty() {
        var text = ToolText.builder().red("").append("").build();

        assertTrue(text.isEmpty());
        assertEquals("", text.plainText());
        assertEquals(ToolText.EMPTY, text);
        assertThrows(IllegalArgumentException.class, () -> new ToolText.Span("", null, false, false, false));
    }

    @Test
    void textKeepsItsOwnCopyOfTheSpans() {
        var spans = new ArrayList<>(List.of(ToolText.Span.of(TextColor.RED, "a")));
        var text = new ToolText(spans);
        spans.add(ToolText.Span.of(TextColor.RED, "b"));

        assertEquals("a", text.plainText());
        assertThrows(UnsupportedOperationException.class, () -> text.spans().add(ToolText.Span.of(TextColor.RED, "c")));
    }

    @Test
    void theSixteenColoursHaveTheirFormattingCodes() {
        var codes = new StringBuilder();
        for (TextColor color : TextColor.values()) {
            codes.append(color.code());
        }

        assertEquals("0123456789abcdef", codes.toString());
    }

    @Test
    void aMenuHasOneToSixRowsAndEveryButtonInsideThemOnItsOwnSlot() {
        var button = new MenuButton(17, ICON, click -> { });

        assertEquals(1, new ToolMenu(ToolText.plain("t"),1, List.of(new MenuButton(8, ICON, click -> { })), null).rows());
        assertEquals(2, new ToolMenu(ToolText.plain("t"),2, List.of(button), null).rows());
        assertThrows(IllegalArgumentException.class, () -> new ToolMenu(ToolText.plain("t"),0, List.of(), null));
        assertThrows(IllegalArgumentException.class, () -> new ToolMenu(ToolText.plain("t"),7, List.of(), null));
        assertThrows(IllegalArgumentException.class, () -> new ToolMenu(ToolText.plain("t"),1, List.of(button), null), "slot 17 is in the second row");
        assertThrows(IllegalArgumentException.class,
                () -> new ToolMenu(ToolText.plain("t"),2, List.of(button, new MenuButton(17, ICON, click -> { })), null));
        assertThrows(IllegalArgumentException.class, () -> new MenuButton(-1, ICON, click -> { }));
    }

    @Test
    void aMenuWithoutAnOnCloseHasOneThatDoesNothing() {
        var closed = new AtomicBoolean();
        var withHandler = new ToolMenu(ToolText.plain("t"),1, List.of(), () -> closed.set(true));
        var without = new ToolMenu(ToolText.plain("t"),1, List.of(), null);

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
    void whatAChangeChangesIsWhatItIsCheckedAgainstUnlessSaidOtherwise() {
        var position = new BlockPosition(1, 2, 3);
        var aside = new BlockPosition(4, 5, 6);

        assertEquals(List.of(position), new BlockChange(BlockChange.Cause.PLACE, List.of(position), Optional.empty(), Optional.empty()).changed());
        var piston = new BlockChange(BlockChange.Cause.PISTON, List.of(), List.of(aside), Optional.empty(), Optional.empty());
        assertEquals(List.of(), piston.positions());
        assertEquals(List.of(aside), piston.changed());
        assertThrows(IllegalArgumentException.class,
                () -> new BlockChange(BlockChange.Cause.PISTON, List.of(), List.of(), Optional.empty(), Optional.empty()));
    }

    @Test
    void aTeleportIsAMoveToAListenerThatDoesNotCareAndNothingToCareAboutInContainers() {
        var moves = new ArrayList<BlockPosition>();
        var listener = new ToolWorldListener() {
            @Override
            public void moved(ToolPlayer player, BlockPosition to) {
                moves.add(to);
            }
        };
        var to = new BlockPosition(7, 8, 9);

        listener.teleported(null, to);
        listener.containerChanged(Optional.empty(), to);

        assertEquals(List.of(to), moves);
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
