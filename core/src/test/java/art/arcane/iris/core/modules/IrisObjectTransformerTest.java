package art.arcane.iris.core.modules;

import art.arcane.iris.engine.UsableBlockPlatform;
import art.arcane.iris.engine.object.prefab.BlockStateRotation;
import art.arcane.iris.spi.IrisPlatforms;
import art.arcane.iris.spi.PlatformBlockState;
import art.arcane.iris.spi.PlatformRegistries;
import com.volmit.block.BlockState;
import com.volmit.geometry.BlockBox;
import com.volmit.geometry.BlockPosition;
import com.volmit.object.GeneratedObject;
import com.volmit.object.ObjectBlock;
import com.volmit.object.ObjectMarker;
import com.volmit.object.ObjectTransform;
import com.volmit.resource.ResourceId;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.mockito.Mockito.when;

/** Objects turn and mirror about their anchor the way Iris places them; what Iris cannot do is refused. */
public class IrisObjectTransformerTest {
    private static final BlockState LOG = BlockState.parse("minecraft:oak_log");
    private static final BlockState LEAVES = BlockState.parse("minecraft:oak_leaves");
    private static final ResourceId SPAWN = ResourceId.parse("test:marks/spawn");

    private final IrisObjectTransformer transformer = new IrisObjectTransformer();
    private BlockStateRotation.StateRotator previousRotator;

    /** A trunk and a leaf east of its top, with a marker beside the trunk. */
    private final GeneratedObject tree = new GeneratedObject(new BlockBox(0, 0, 0, 2, 2, 1), new BlockPosition(0, 0, 0),
            List.of(new ObjectBlock(new BlockPosition(0, 0, 0), LOG), new ObjectBlock(new BlockPosition(1, 1, 0), LEAVES)),
            List.of(new ObjectMarker(new BlockPosition(1, 0, 0), SPAWN, Map.of("kind", "bird"))));

    @Before
    public void bindPlatform() {
        UsableBlockPlatform.bind();
        previousRotator = BlockStateRotation.bindPlatformRotator((turn, state) -> state);
    }

    @After
    public void unbindPlatform() {
        BlockStateRotation.restorePlatformRotator(previousRotator);
        UsableBlockPlatform.unbind();
    }

    private static ObjectTransform rotatedY(double degrees) {
        return new ObjectTransform(0, degrees, 0, 1, 1, 1, false, false, false);
    }

    @Test
    public void theIdentityIsTheSameObject() {
        assertSame(tree, transformer.transform(tree, ObjectTransform.identity()));
    }

    @Test
    public void aQuarterTurnAboutYMovesBlocksAndMarkersAboutTheAnchor() {
        GeneratedObject turned = transformer.transform(tree, rotatedY(90));

        assertEquals(List.of(new BlockPosition(0, 0, 0), new BlockPosition(0, 1, -1)),
                turned.blocks().stream().map(ObjectBlock::position).sorted().toList());
        assertEquals(new BlockPosition(0, 0, -1), turned.markers().getFirst().position());
        assertEquals(SPAWN, turned.markers().getFirst().type());
        assertEquals(Map.of("kind", "bird"), turned.markers().getFirst().attributes());
        assertEquals(tree.anchor(), turned.anchor());
        assertEquals(new BlockBox(0, 0, -1, 1, 2, 1), turned.bounds());
    }

    @Test
    public void theSourceObjectIsLeftAsItWas() {
        transformer.transform(tree, rotatedY(90));

        assertEquals(new BlockPosition(1, 1, 0), tree.blocks().getLast().position());
    }

    @Test
    public void aYMirrorFlipsHeightsAboutTheAnchorWithoutChangingBlocks() {
        GeneratedObject hung = transformer.transform(tree, new ObjectTransform(0, 0, 0, 1, 1, 1, false, true, false));

        assertEquals(List.of(new BlockPosition(0, 0, 0), new BlockPosition(1, -1, 0)),
                hung.blocks().stream().map(ObjectBlock::position).sorted().toList());
        assertEquals(List.of(LEAVES, LOG), hung.blocks().stream().map(ObjectBlock::state).sorted(java.util.Comparator.comparing(BlockState::key)).toList());
    }

    @Test
    public void blocksTurnedOntoOnePositionKeepTheLastOne() {
        GeneratedObject squeezed = transformer.transform(new GeneratedObject(new BlockBox(0, 0, 0, 2, 1, 1), new BlockPosition(0, 0, 0),
                List.of(new ObjectBlock(new BlockPosition(0, 0, 0), LOG), new ObjectBlock(new BlockPosition(1, 0, 0), LEAVES)), List.of()),
                new ObjectTransform(0, 0, 0, 1, 1, 1, false, true, false));

        assertEquals(2, squeezed.blocks().size());
    }

    private static ObjectTransform mirrored(boolean x, boolean z) {
        return new ObjectTransform(0, 0, 0, 1, 1, 1, x, false, z);
    }

    /** What the platform answers for a state and its mirrors: the platform, not Iris, decides how a block looks mirrored. */
    private static void mirrorsBy(String key, String alongX, String alongZ) {
        PlatformRegistries registries = IrisPlatforms.get().registries();
        PlatformBlockState state = registries.block(key);
        PlatformBlockState mirroredAlongX = registries.block(alongX);
        PlatformBlockState mirroredAlongZ = registries.block(alongZ);
        when(state.mirrorX()).thenReturn(mirroredAlongX);
        when(state.mirrorZ()).thenReturn(mirroredAlongZ);
    }

    /** A stair east of the anchor and a marker beside it, in a world whose stairs mirror as the test platform says. */
    private GeneratedObject stairsEastOfTheAnchor() {
        mirrorsBy("minecraft:oak_stairs[facing=east]", "minecraft:oak_stairs[facing=west]", "minecraft:oak_stairs[facing=east]");
        mirrorsBy("minecraft:oak_stairs[facing=west]", "minecraft:oak_stairs[facing=east]", "minecraft:oak_stairs[facing=west]");
        mirrorsBy("minecraft:oak_stairs[facing=north]", "minecraft:oak_stairs[facing=north]", "minecraft:oak_stairs[facing=south]");
        return new GeneratedObject(new BlockBox(0, 0, 0, 2, 1, 2), new BlockPosition(0, 0, 0),
                List.of(new ObjectBlock(new BlockPosition(1, 0, 0), BlockState.parse("minecraft:oak_stairs[facing=east]")),
                        new ObjectBlock(new BlockPosition(0, 0, 1), BlockState.parse("minecraft:oak_stairs[facing=north]"))),
                List.of(new ObjectMarker(new BlockPosition(1, 0, 1), SPAWN, Map.of("kind", "bird"))));
    }

    @Test
    public void anXMirrorFlipsXAboutTheAnchorAndMirrorsStatesThroughThePlatform() {
        GeneratedObject mirrored = transformer.transform(stairsEastOfTheAnchor(), mirrored(true, false));

        assertEquals(List.of(new ObjectBlock(new BlockPosition(-1, 0, 0), BlockState.parse("minecraft:oak_stairs[facing=west]")),
                new ObjectBlock(new BlockPosition(0, 0, 1), BlockState.parse("minecraft:oak_stairs[facing=north]"))),
                mirrored.blocks());
        assertEquals(new BlockPosition(-1, 0, 1), mirrored.markers().getFirst().position());
        assertEquals(new BlockBox(-1, 0, 0, 1, 1, 2), mirrored.bounds());
        assertEquals(new BlockPosition(0, 0, 0), mirrored.anchor());
    }

    @Test
    public void aZMirrorFlipsZAboutTheAnchorAndMirrorsStatesThroughThePlatform() {
        GeneratedObject mirrored = transformer.transform(stairsEastOfTheAnchor(), mirrored(false, true));

        assertEquals(List.of(new ObjectBlock(new BlockPosition(0, 0, -1), BlockState.parse("minecraft:oak_stairs[facing=south]")),
                new ObjectBlock(new BlockPosition(1, 0, 0), BlockState.parse("minecraft:oak_stairs[facing=east]"))),
                mirrored.blocks().stream().sorted(java.util.Comparator.comparing(ObjectBlock::position)).toList());
        assertEquals(new BlockPosition(1, 0, -1), mirrored.markers().getFirst().position());
    }

    @Test
    public void mirroringBothAxesMirrorsStatesAlongBoth() {
        GeneratedObject mirrored = transformer.transform(stairsEastOfTheAnchor(), mirrored(true, true));

        assertEquals(List.of(new BlockPosition(-1, 0, 0), new BlockPosition(0, 0, -1)),
                mirrored.blocks().stream().map(ObjectBlock::position).sorted().toList());
        assertEquals(List.of("minecraft:oak_stairs[facing=south]", "minecraft:oak_stairs[facing=west]"),
                mirrored.blocks().stream().map(block -> block.state().key()).sorted().toList());
        assertEquals(new BlockPosition(-1, 0, -1), mirrored.markers().getFirst().position());
    }

    @Test
    public void mirrorsComeBeforeRotation() {
        GeneratedObject object = new GeneratedObject(new BlockBox(0, 0, 0, 2, 1, 1), new BlockPosition(0, 0, 0),
                List.of(new ObjectBlock(new BlockPosition(1, 0, 0), LOG)), List.of());

        GeneratedObject both = transformer.transform(object, new ObjectTransform(0, 90, 0, 1, 1, 1, true, false, false));

        // Mirrored first (1,0,0) -> (-1,0,0), then a quarter turn about Y sends +X to -Z, so -X goes to +Z.
        assertEquals(new BlockPosition(0, 0, 1), both.blocks().getFirst().position());
    }

    @Test
    public void aMirrorTheBlockIgnoresLeavesItsStateAsItWas() {
        GeneratedObject mirrored = transformer.transform(tree, mirrored(true, false));

        assertEquals(List.of(LEAVES, LOG), mirrored.blocks().stream().map(ObjectBlock::state).toList());
        assertEquals(List.of(new BlockPosition(-1, 1, 0), new BlockPosition(0, 0, 0)),
                mirrored.blocks().stream().map(ObjectBlock::position).toList());
    }

    @Test
    public void scalingIsRefused() {
        assertThrows(UnsupportedOperationException.class,
                () -> transformer.transform(tree, new ObjectTransform(0, 0, 0, 2, 1, 1, false, false, false)));
    }
}
