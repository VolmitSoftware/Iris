package com.volmit;

import com.volmit.block.BlockState;
import com.volmit.host.ArrayHunk;
import com.volmit.mantle.BlockBox;
import com.volmit.object.*;
import com.volmit.placement.PlacementRequest;
import com.volmit.resource.*;
import com.volmit.world.*;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import static org.junit.jupiter.api.Assertions.*;

class ApiValuesTest {
    @Test
    void idsAndStateKeysAreExplicitCanonicalImmutableValues() {
        for (String invalid : List.of("stone", "Minecraft:stone", "a:b:c", "a:", ":a")) {
            assertThrows(IllegalArgumentException.class, () -> ResourceId.parse(invalid));
        }
        var properties = new HashMap<>(Map.of("waterlogged", "false", "axis", "y"));
        var state = new BlockState(ResourceId.parse("minecraft:oak_log"), properties);
        properties.clear();
        assertEquals("minecraft:oak_log[axis=y,waterlogged=false]", state.key());
        assertEquals("y", state.properties().get("axis"));
        assertEquals("x", state.withProperty("axis", "x").properties().get("axis"));
        assertThrows(UnsupportedOperationException.class, () -> state.properties().clear());
        assertThrows(IllegalArgumentException.class, () -> new ResourceKey<>("test:value", int.class));
    }

    @Test
    void blockStatesParseWhatTheirKeyWrites() {
        var log = new BlockState("minecraft:oak_log").withProperty("waterlogged", "false").withProperty("axis", "y");

        assertEquals(log, BlockState.parse(log.key()));
        assertEquals(log, BlockState.parse("minecraft:oak_log[waterlogged=false,axis=y]"));
        assertEquals(new BlockState("minecraft:stone"), BlockState.parse("minecraft:stone"));
        assertEquals(new BlockState("minecraft:stone"), BlockState.parse("minecraft:stone[]"));
        assertThrows(IllegalArgumentException.class, () -> BlockState.parse("minecraft:oak_log[axis=y"));
        assertThrows(IllegalArgumentException.class, () -> BlockState.parse("minecraft:oak_log[axis]"));
        assertThrows(IllegalArgumentException.class, () -> BlockState.parse("minecraft:oak_log[axis=y,axis=x]"));
        assertThrows(IllegalArgumentException.class, () -> BlockState.parse("stone"));
    }

    @Test
    void moduleInfoRequiresANameAndVersion() {
        var info = ModuleInfo.of("example:moss", "Moss", "1.0.0");
        assertEquals(ResourceId.parse("example:moss"), info.id());
        assertEquals("Moss", info.name());
        assertThrows(IllegalArgumentException.class, () -> ModuleInfo.of("example:moss", " ", "1.0.0"));
        assertThrows(IllegalArgumentException.class, () -> ModuleInfo.of("example:moss", "Moss", ""));
        assertThrows(NullPointerException.class, () -> ModuleInfo.of("example:moss", null, "1.0.0"));
    }

    @Test
    void moduleInfoDeclaresOptionalPackagesWithTheirSubpackages() {
        var plain = ModuleInfo.of("example:moss", "Moss", "1.0.0");
        var info = plain.withOptionalPackages("org.slf4j", "org.apache.logging.");

        assertEquals(List.of(), plain.optionalPackages());
        assertEquals(List.of(new PackageName("org.slf4j"), new PackageName("org.apache.logging")), info.optionalPackages());
        assertEquals(List.of(), info.withOptionalPackages().optionalPackages());
        assertTrue(info.optionalPackages().getFirst().contains("org.slf4j.spi.LocationAwareLogger"));
        assertFalse(info.optionalPackages().getFirst().contains("org.slf4jx.Logger"));
        for (var invalid : List.of("", "org.*", "org..slf4j", "1org")) {
            var failure = assertThrows(IllegalArgumentException.class, () -> plain.withOptionalPackages(invalid));
            assertTrue(failure.getMessage().startsWith("Optional package '" + invalid + "' of module example:moss is not a package name"),
                    failure.getMessage());
        }
    }

    @Test
    void moduleInfoTextFitsOnOneLogLine() {
        for (var broken : List.of("Moss\nINFO forged line", "Moss\r", "Moss\tTabbed", "Moss\u2028Next")) {
            var failure = assertThrows(IllegalArgumentException.class, () -> ModuleInfo.of("example:moss", broken, "1.0.0"));
            assertEquals("Module name of example:moss contains a line break or other control character", failure.getMessage());
        }
        assertThrows(IllegalArgumentException.class, () -> ModuleInfo.of("example:moss", "Moss", "1.0\n"));
        assertEquals("Mosß é", ModuleInfo.of("example:moss", "Mosß é", "1.0.0").name());
    }

    @Test
    void moduleIdsAreOneSegmentAndOwnTheIdsBelowThem() {
        var nested = assertThrows(IllegalArgumentException.class, () -> ModuleInfo.of("iris:stilt/extra", "Extra", "1"));
        assertEquals("Module id iris:stilt/extra must not contain '/': stage ids are a module id, '/' and a name, "
                + "so iris:stilt/extra would name stages under another module's id", nested.getMessage());

        var stilt = ModuleInfo.of("iris:stilt", "Stilt", "1");
        assertEquals("iris:stilt/", stilt.idPrefix());
        assertTrue(stilt.ownsStage(ResourceId.parse("iris:stilt/fill")));
        assertTrue(stilt.ownsStage(ResourceId.parse("iris:stilt/fill/deep")));
        assertFalse(stilt.ownsStage(ResourceId.parse("iris:stilt")));
        assertFalse(stilt.ownsStage(ResourceId.parse("iris:stilts/fill")));
        assertFalse(stilt.ownsStage(ResourceId.parse("iris:anchor/height")));
        assertFalse(stilt.ownsStage(ResourceId.parse("other:stilt/fill")));

        assertTrue(stilt.ownsId(ResourceId.parse("iris:stilt")));
        assertTrue(stilt.ownsId(ResourceId.parse("iris:stilt/depth")));
        assertFalse(stilt.ownsId(ResourceId.parse("iris:stilts")));
        assertFalse(stilt.ownsId(ResourceId.parse("iris:stilt-depth")));
        assertFalse(stilt.ownsId(ResourceId.parse("iris:anchor")));
        assertFalse(stilt.ownsId(ResourceId.parse("other:stilt")));
    }

    @Test
    void sparseObjectsCopySortAndRejectDuplicateOrOutOfBoundsBlocks() {
        var a = new ObjectBlock(new BlockPosition(-1, 0, 0), new BlockState("minecraft:stone"));
        var b = new ObjectBlock(new BlockPosition(1, 0, 0), new BlockState("minecraft:stone"));
        var blocks = new ArrayList<>(List.of(b, a));
        var bounds = new BlockBox(-1, 0, 0, 2, 1, 1);
        var object = new GeneratedObject(bounds, new BlockPosition(0, 0, 0), blocks, List.of());
        blocks.clear();
        assertEquals(List.of(a, b), object.blocks());
        assertThrows(IllegalArgumentException.class, () -> new GeneratedObject(bounds, object.anchor(), List.of(a, a), List.of()));
        assertThrows(IllegalArgumentException.class, () -> new GeneratedObject(new BlockBox(0, 0, 0, 1, 1, 1), object.anchor(), List.of(a), List.of()));
    }

    @Test
    void payloadBuffersDoNotEscapeAndHaveValueEquality() {
        byte[] source = {1, 2};
        var payload = new BlockEntityData(ResourceId.parse("test:tile"), 1, source);
        source[0] = 99;
        payload.payload()[0] = 98;
        assertArrayEquals(new byte[]{1, 2}, payload.payload());
        assertEquals(payload, new BlockEntityData(ResourceId.parse("test:tile"), 1, new byte[]{1, 2}));
    }

    @Test
    void placementAlignsAnchorsAndCheckedArithmeticNeverWraps() {
        var object = new GeneratedObject(new BlockBox(-1, -1, -1, 2, 2, 2), new BlockPosition(1, 1, 1), List.of(), List.of());
        var request = new PlacementRequest(ResourceId.parse("test:placement"), object, new BlockPosition(-16, -64, -16));
        assertEquals(new BlockPosition(-16, -64, -16), request.worldPosition(object.anchor()));
        assertEquals(new BlockBox(-18, -66, -18, -15, -63, -15), request.footprint());
        var overflow = new PlacementRequest(request.id(), object, new BlockPosition(Integer.MAX_VALUE, 0, 0));
        assertThrows(ArithmeticException.class, overflow::footprint);
    }

    @Test
    void boundedHunksAndTerrainSamplesRejectInvalidInputs() {
        var hunk = new ArrayHunk<>(String.class, 2, 3, 4, "air");
        hunk.set(1, 2, 3, "stone");
        assertEquals("stone", hunk.get(1, 2, 3));
        assertEquals("air", hunk.get(0, 0, 0));
        assertThrows(IndexOutOfBoundsException.class, () -> hunk.get(2, 0, 0));
        assertThrows(IndexOutOfBoundsException.class, () -> hunk.set(-1, 0, 0, "stone"));
        assertThrows(NullPointerException.class, () -> hunk.set(0, 0, 0, null));
        assertThrows(IllegalArgumentException.class, () -> new TerrainSample(Double.NaN, OptionalInt.empty(), 0));
        assertThrows(IllegalArgumentException.class, () -> new TerrainSample(0, OptionalInt.empty(), -1));
    }
}
