package art.arcane.iris.core.modules;

import art.arcane.iris.engine.UsableBlockPlatform;
import art.arcane.iris.engine.object.prefab.IrisObjectRotation;
import art.arcane.iris.spi.PlatformBlockState;
import com.volmit.block.BlockState;
import com.volmit.mantle.BlockBox;
import com.volmit.object.GeneratedObject;
import com.volmit.object.ObjectBlock;
import com.volmit.object.ObjectMarker;
import com.volmit.object.ObjectTransform;
import com.volmit.resource.ResourceId;
import com.volmit.world.BlockPosition;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;

/** Objects turn about their anchor the way Iris places them; what Iris cannot do is refused. */
public class IrisObjectTransformerTest {
    private static final BlockState LOG = BlockState.parse("minecraft:oak_log");
    private static final BlockState LEAVES = BlockState.parse("minecraft:oak_leaves");
    private static final ResourceId SPAWN = ResourceId.parse("test:marks/spawn");

    private final IrisObjectTransformer transformer = new IrisObjectTransformer();
    private IrisObjectRotation.StateRotator previousRotator;

    /** A trunk and a leaf east of its top, with a marker beside the trunk. */
    private final GeneratedObject tree = new GeneratedObject(new BlockBox(0, 0, 0, 2, 2, 1), new BlockPosition(0, 0, 0),
            List.of(new ObjectBlock(new BlockPosition(0, 0, 0), LOG), new ObjectBlock(new BlockPosition(1, 1, 0), LEAVES)),
            List.of(new ObjectMarker(new BlockPosition(1, 0, 0), SPAWN, Map.of("kind", "bird"))));

    @Before
    public void bindPlatform() {
        UsableBlockPlatform.bind();
        previousRotator = IrisObjectRotation.bindPlatformRotator((rotation, state, spinX, spinY, spinZ) -> state);
    }

    @After
    public void unbindPlatform() {
        IrisObjectRotation.restorePlatformRotator(previousRotator);
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

    @Test
    public void mirroringAlongXOrZAndScalingAreRefused() {
        assertThrows(UnsupportedOperationException.class,
                () -> transformer.transform(tree, new ObjectTransform(0, 0, 0, 1, 1, 1, true, false, false)));
        assertThrows(UnsupportedOperationException.class,
                () -> transformer.transform(tree, new ObjectTransform(0, 0, 0, 1, 1, 1, false, false, true)));
        assertThrows(UnsupportedOperationException.class,
                () -> transformer.transform(tree, new ObjectTransform(0, 0, 0, 2, 1, 1, false, false, false)));
    }
}
