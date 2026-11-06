package art.arcane.iris.core.modules;

import art.arcane.iris.engine.UsableBlockPlatform;
import art.arcane.iris.engine.framework.Engine;
import com.volmit.placement.PlacementTerrain;
import com.volmit.host.MantleRegistration;
import com.volmit.geometry.BlockBox;
import com.volmit.data.Codec;
import com.volmit.mantle.MantleKey;
import com.volmit.mantle.MantleLifetime;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** A placement's mantle writes, direct or through an edit, reach the engine's mantle only with the placement's commit. */
public class PlacementMantleTest {
    private static final int HEIGHT = 64;
    private static final int Y = 20;
    private static final MantleKey<String> MARKS = new MantleKey<>("iris:marks/mark", String.class);
    private static final Codec<String> TEXT = new Codec<>() {
        public int version() { return 1; }
        public void write(DataOutput output, String value) throws IOException { output.writeUTF(value); }
        public String read(DataInput input, int storedVersion) throws IOException { return input.readUTF(); }
    };
    private static final List<MantleRegistration<?>> LAYERS =
            List.of(new MantleRegistration<>(MARKS, MantleLifetime.GENERATION, TEXT, 1));

    private final InMemoryMantle storage = new InMemoryMantle(HEIGHT / 16);
    /** A placement area has no vertical limit. */
    private final BlockBox area = new BlockBox(-8, Integer.MIN_VALUE, -8, 24, Integer.MAX_VALUE, 24);

    @Before
    public void bindPlatform() {
        UsableBlockPlatform.bind();
    }

    @After
    public void unbindPlatform() {
        UsableBlockPlatform.unbind();
    }

    private Engine engine() {
        Engine engine = mock(Engine.class);
        when(engine.getHeight()).thenReturn(HEIGHT);
        storage.bindTo(engine);
        return engine;
    }

    private TerrainEdit edit(Engine engine) {
        return new TerrainEdit(mock(PlacementTerrain.class), area, () -> new PlacementMantle(engine, area, LAYERS));
    }

    private String stored(int x, int z) {
        return storage.value(MARKS, TEXT, x, Y, z);
    }

    @Test
    public void writesReachTheMantleTogetherWhenThePlacementCommits() {
        TerrainEdit placement = edit(engine());
        placement.mantle().layer(MARKS).set(1, Y, 1, "near");
        placement.mantle().layer(MARKS).set(17, Y, 2, "next chunk");
        placement.mantle().layer(MARKS).set(17, Y, 3, "removed");
        placement.mantle().layer(MARKS).remove(17, Y, 3);

        assertEquals("a stage reads what it wrote", "near", placement.mantle().layer(MARKS).get(1, Y, 1));
        assertNull(placement.mantle().layer(MARKS).get(17, Y, 3));
        assertNull("nothing is in the mantle before the placement commits", stored(1, 1));
        assertNull(stored(17, 2));
        placement.commit();

        assertEquals("near", stored(1, 1));
        assertEquals("next chunk", stored(17, 2));
        assertNull(stored(17, 3));
    }

    @Test
    public void aDiscardedPlacementLeavesTheMantleAsItWas() {
        TerrainEdit placement = edit(engine());
        placement.mantle().layer(MARKS).set(1, Y, 1, "written");

        placement.close();

        assertNull(stored(1, 1));
    }

    @Test
    public void aStageCannotOpenAnEditOfItsOwn() {
        TerrainEdit placement = edit(engine());

        assertThrows(UnsupportedOperationException.class, () -> placement.mantle().edit());
    }

    @Test
    public void visitsAndClearsStayWithinTheHeightsTheMantleStores() {
        TerrainEdit placement = edit(engine());
        placement.mantle().layer(MARKS).set(1, Y, 1, "low");
        placement.mantle().layer(MARKS).set(2, HEIGHT - 1, 1, "high");
        List<String> visited = new ArrayList<>();

        placement.mantle().layer(MARKS).visit(area, (x, y, z, value) -> visited.add(value));
        placement.mantle().layer(MARKS).clear(area);

        assertEquals(List.of("low", "high"), visited);
        assertNull(placement.mantle().layer(MARKS).get(1, Y, 1));
    }

    @Test
    public void aPlacementWhoseStagesNeverAskForTheMantleNeverTouchesIt() {
        Engine engine = engine();
        TerrainEdit placement = edit(engine);

        placement.commit();

        assertFalse(storage.isTouched());
    }

    @Test
    public void writesOutsideThePlacementAreaFail() {
        TerrainEdit placement = edit(engine());

        assertThrows(IndexOutOfBoundsException.class, () -> placement.mantle().layer(MARKS).set(24, Y, 0, "out"));
        assertThrows("above the heights the mantle stores", IndexOutOfBoundsException.class,
                () -> placement.mantle().layer(MARKS).set(0, HEIGHT, 0, "above"));
        assertNull("but reading there is allowed and finds nothing", placement.mantle().layer(MARKS).get(0, HEIGHT + 5, 0));
    }

    @Test
    public void aPlacementWithoutAnEngineHasNoMantle() {
        TerrainEdit placement = new TerrainEdit(mock(PlacementTerrain.class), area, () -> {
            throw new UnsupportedOperationException("This placement runs without an Iris engine, so it has no mantle");
        });

        assertThrows(UnsupportedOperationException.class, placement::mantle);
    }
}
