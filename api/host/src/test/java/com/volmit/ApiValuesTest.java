package com.volmit;

import com.volmit.block.BlockState;
import com.volmit.host.ArrayHunk;
import com.volmit.geometry.*;
import com.volmit.object.*;
import com.volmit.placement.PlacementRequest;
import com.volmit.resource.*;
import com.volmit.validation.Problem;
import com.volmit.validation.Severity;
import com.volmit.validation.Subject;
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
    void aPackKeyBecomesTheIdOfItsResource() {
        assertEquals(ResourceId.parse("overworld:dungeon/chest"), ResourceId.ofPackKey("overworld", "dungeon/chest"));
        assertEquals(ResourceId.parse("overworld:dungeon/chest_1"), ResourceId.ofPackKey("overworld", "Dungeon/Chest 1"));
        assertEquals(ResourceId.parse("test:a/b"), new com.volmit.support.EmptyResources().idOf("a/b"));
    }

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
    }

    @Test
    void withPropertyChecksTheNewPropertyAndKeepsValueSemantics() {
        var log = new BlockState("minecraft:oak_log").withProperty("waterlogged", "false").withProperty("axis", "y");

        assertEquals(new BlockState(ResourceId.parse("minecraft:oak_log"), Map.of("axis", "y", "waterlogged", "false")), log);
        assertEquals(Map.of("axis", "y", "waterlogged", "false").hashCode(), log.properties().hashCode());
        assertEquals("BlockState[material=minecraft:oak_log, properties={axis=y, waterlogged=false}]", log.toString());
        assertEquals(List.of("axis", "waterlogged"), List.copyOf(log.properties().keySet()));
        assertEquals(log, new BlockState(log.material(), log.properties()));
        assertThrows(UnsupportedOperationException.class, () -> log.properties().put("axis", "x"));
        assertThrows(IllegalArgumentException.class, () -> log.withProperty("Axis", "x"));
        assertThrows(IllegalArgumentException.class, () -> log.withProperty("axis", "X"));
        assertThrows(NullPointerException.class, () -> log.withProperty("axis", null));
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
        var request = new PlacementRequest(object, new BlockPosition(-16, 64, -16));
        assertEquals(new BlockPosition(-16, 64, -16), request.worldPosition(object.anchor()));
        assertEquals(new BlockBox(-18, 62, -18, -15, 65, -15), request.footprint());
        var overflow = new PlacementRequest(object, new BlockPosition(Integer.MAX_VALUE, 0, 0));
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

    @Test
    void packBiomesAndRegionsAreImmutableValues() {
        var children = new ArrayList<>(List.of(new Weighted<>(ResourceId.parse("pack:dunes"), 2)));
        var desert = new PackBiome(ResourceId.parse("pack:desert"), "Desert", ResourceId.parse("minecraft:desert"), children);
        children.clear();
        assertEquals(List.of(new Weighted<>(ResourceId.parse("pack:dunes"), 2)), desert.children());
        assertThrows(UnsupportedOperationException.class, () -> desert.children().clear());
        assertEquals(desert.platformBiome(), desert.vanillaBiome(), "vanilla sees the platform biome unless a pack says otherwise");
        var mesa = new PackBiome(ResourceId.parse("pack:mesa"), "Mesa", ResourceId.parse("minecraft:desert"),
                ResourceId.parse("minecraft:badlands"), List.of());
        assertEquals(ResourceId.parse("minecraft:badlands"), mesa.vanillaBiome());

        var land = List.of(new Weighted<>(desert.id(), 1));
        var arid = new PackRegion(ResourceId.parse("pack:arid"), "Arid", land, List.of(), List.of(), List.of());
        assertEquals(land, arid.landBiomes());
        assertThrows(UnsupportedOperationException.class, () -> arid.caveBiomes().add(new Weighted<>(desert.id(), 1)));
    }

    static final class Keep {}

    @Test
    void resourceKindsReadOneFolderIntoAConcreteClass() {
        assertTrue(ResourceKind.isFolderName("jigsaw-pools"));
        for (String folder : List.of("", ".", "..", "a/b", "Keeps", "a b")) {
            var failure = assertThrows(IllegalArgumentException.class, () -> ResourceKind.of("test:castle/keep", folder, Keep.class));
            assertTrue(failure.getMessage().startsWith("Folder '" + folder + "' of resource kind test:castle/keep is not a folder name"),
                    failure.getMessage());
        }
        assertThrows(IllegalArgumentException.class, () -> ResourceKind.of("test:castle/keep", "keeps", Runnable.class));
        assertThrows(IllegalArgumentException.class, () -> ResourceKind.of("test:castle/keep", "keeps", int.class));
        assertThrows(IllegalArgumentException.class, () -> ResourceKind.of("keep", "keeps", Keep.class));
    }

    @Test
    void problemsConcernAResourceOrAFileBelowThePackRoot() {
        var error = Problem.error(Subject.file("structures/castle.json"), "Castle never assembles.");
        var warning = Problem.warning(Subject.resource(ResourceId.parse("overworld:castle")), "Castle is large.");

        assertEquals(Severity.ERROR, error.severity());
        assertEquals("structures/castle.json", error.subject().toString());
        assertEquals(Severity.WARNING, warning.severity());
        assertEquals("overworld:castle", warning.subject().toString());
        for (String path : List.of("", "/structures/castle.json", "../castle.json", "structures/../castle.json", "a//b", "a\\b", "./a")) {
            assertThrows(IllegalArgumentException.class, () -> Subject.file(path), path);
        }
        assertThrows(IllegalArgumentException.class, () -> Problem.error(Subject.file("a.json"), " "));
    }
}
