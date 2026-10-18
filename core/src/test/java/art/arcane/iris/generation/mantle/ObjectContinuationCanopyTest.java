package art.arcane.iris.generation.mantle;

import art.arcane.iris.generation.block.TileData;
import art.arcane.iris.generation.decoration.tree.TreeBlockMaterial;
import art.arcane.iris.generation.runtime.Engine;
import art.arcane.iris.integration.Identifier;
import art.arcane.iris.spi.IrisPlatform;
import art.arcane.iris.spi.IrisPlatforms;
import art.arcane.iris.spi.PlatformRegistries;
import art.arcane.iris.structure.object.IObjectPlacer;
import art.arcane.iris.testsupport.PlatformLeakGuard;
import art.arcane.iris.world.storage.matter.IrisMatterSupport;
import art.arcane.iris.world.storage.matter.TileWrapper;
import art.arcane.volmlib.nativelib.terrain.NativeBlockState;
import art.arcane.volmlib.util.hunk.Hunk;
import art.arcane.volmlib.util.mantle.runtime.Mantle;
import art.arcane.volmlib.util.mantle.runtime.MantleChunk;
import art.arcane.volmlib.util.math.BlockPosition;
import art.arcane.volmlib.util.matter.IrisMatter;
import art.arcane.volmlib.util.matter.Matter;
import art.arcane.volmlib.util.matter.MatterSlice;
import org.junit.After;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class ObjectContinuationCanopyTest {
    @ClassRule
    public static final PlatformLeakGuard PLATFORM_GUARD = PlatformLeakGuard.clean();

    private IrisPlatform previous;
    private NativeBlockState trunk;
    private NativeBlockState canopy;
    private NativeBlockState custom;

    @Before
    public void bindPalette() {
        IrisMatterSupport.ensureRegistered();
        previous = IrisPlatforms.isBound() ? IrisPlatforms.get() : null;
        IrisPlatform platform = mock(IrisPlatform.class);
        PlatformRegistries registries = mock(PlatformRegistries.class);
        trunk = mock(NativeBlockState.class);
        canopy = mock(NativeBlockState.class);
        custom = mock(NativeBlockState.class);
        when(trunk.key()).thenReturn("minecraft:oak_log");
        when(trunk.isSolid()).thenReturn(true);
        when(canopy.key()).thenReturn("minecraft:red_stained_glass");
        when(canopy.isSolid()).thenReturn(true);
        when(custom.isCustom()).thenReturn(true);
        when(custom.placementBaseState()).thenReturn(canopy);
        when(custom.deferredPlacementKey()).thenReturn("test:custom_canopy");
        when(platform.registries()).thenReturn(registries);
        when(registries.decodeBlockState(trunk.key())).thenReturn(trunk);
        when(registries.decodeBlockState(canopy.key())).thenReturn(canopy);
        IrisPlatforms.unbind();
        IrisPlatforms.bind(platform);
    }

    @After
    public void restorePalette() {
        IrisPlatforms.unbind();
        if (previous != null) {
            IrisPlatforms.bind(previous);
        }
    }

    @Test
    public void encodingKeepsTrunkProvenanceWhenLaterCustomCanopyOverlaps() throws Exception {
        Matter payload = decode(List.of(
                new ObjectDestinationTransaction.SetMutation(key(16, NativeBlockState.class), trunk, false),
                new ObjectDestinationTransaction.SetMutation(key(16, String.class), "original-trunk", false),
                new ObjectDestinationTransaction.CustomBlockMutation(key(16, NativeBlockState.class), custom, true),
                new ObjectDestinationTransaction.SetMutation(key(16, String.class), "rejected-canopy", true),
                new ObjectDestinationTransaction.CustomBlockMutation(key(17, NativeBlockState.class), custom, true)));

        assertSame(trunk, payload.getSlice(NativeBlockState.class).get(0, 5, 1));
        assertEquals("original-trunk", payload.getSlice(String.class).get(0, 5, 1));
        assertNull(payload.getSlice(Identifier.class).get(0, 5, 1));
        assertNull(payload.getSlice(TreeCanopy.class).get(0, 5, 1));
        assertSame(canopy, payload.getSlice(NativeBlockState.class).get(1, 5, 1));
        assertEquals(Identifier.fromString("test:custom_canopy"), payload.getSlice(Identifier.class).get(1, 5, 1));
        assertSame(TreeCanopy.INSTANCE, payload.getSlice(TreeCanopy.class).get(1, 5, 1));
    }

    @Test
    public void unconditionalBlockClearsPreviousCanopyCondition() throws Exception {
        Matter payload = decode(List.of(
                new ObjectDestinationTransaction.CustomBlockMutation(key(16, NativeBlockState.class), custom, true),
                new ObjectDestinationTransaction.SetMutation(key(16, NativeBlockState.class), trunk, false)));

        assertSame(trunk, payload.getSlice(NativeBlockState.class).get(0, 5, 1));
        MatterSlice<TreeCanopy> conditions = payload.getSlice(TreeCanopy.class);
        assertTrue(conditions == null || conditions.get(0, 5, 1) == null);
        MatterSlice<Identifier> identifiers = payload.getSlice(Identifier.class);
        assertTrue(identifiers == null || identifiers.get(0, 5, 1) == null);
    }

    @Test
    public void replayDefersCanopyAndPreservesLaterStructureMetadata() throws Exception {
        ReplayFixture fixture = new ReplayFixture();
        Matter payload = customPayload();
        ObjectContinuationPersistence.restore(fixture.mantle, 1, 0, payload, fixture.blocks, false);
        assertFalse(fixture.section.hasSlice(Identifier.class));
        assertNull(fixture.blocks.get(0, 5, 1));
        fixture.blocks.set(0, 5, 1, trunk);
        Identifier existingId = Identifier.fromString("test:existing_structure");
        TreeBlockMaterial provenance = TreeBlockMaterial.of("minecraft:oak_log");
        TileWrapper tile = new TileWrapper(mock(TileData.class));
        fixture.section.slice(NativeBlockState.class).set(0, 5, 1, trunk);
        fixture.section.slice(Identifier.class).set(0, 5, 1, existingId);
        fixture.section.slice(String.class).set(0, 5, 1, "original-trunk");
        fixture.section.slice(TreeBlockMaterial.class).set(0, 5, 1, provenance);
        fixture.section.slice(TileWrapper.class).set(0, 5, 1, tile);

        ObjectContinuationPersistence.restore(fixture.mantle, 1, 0, payload, fixture.blocks, true);

        assertSame(trunk, fixture.blocks.get(0, 5, 1));
        assertSame(trunk, fixture.section.getSlice(NativeBlockState.class).get(0, 5, 1));
        assertEquals(existingId, fixture.section.getSlice(Identifier.class).get(0, 5, 1));
        assertEquals("original-trunk", fixture.section.getSlice(String.class).get(0, 5, 1));
        assertEquals(provenance, fixture.section.getSlice(TreeBlockMaterial.class).get(0, 5, 1));
        assertSame(tile, fixture.section.getSlice(TileWrapper.class).get(0, 5, 1));
        assertFalse(fixture.section.hasSlice(TreeCanopy.class));
    }

    @Test
    public void replayPlacesCustomCanopyIntoAirAndClearsOldTileMetadata() throws Exception {
        ReplayFixture fixture = new ReplayFixture();
        fixture.section.slice(TileWrapper.class).set(0, 5, 1, new TileWrapper(mock(TileData.class)));
        fixture.section.slice(TreeBlockMaterial.class).set(0, 5, 1, TreeBlockMaterial.of("minecraft:oak_log"));

        ObjectContinuationPersistence.restore(fixture.mantle, 1, 0, customPayload(), fixture.blocks, true);

        assertSame(canopy, fixture.blocks.get(0, 5, 1));
        assertEquals(Identifier.fromString("test:custom_canopy"), fixture.section.getSlice(Identifier.class).get(0, 5, 1));
        assertEquals("incoming-canopy", fixture.section.getSlice(String.class).get(0, 5, 1));
        assertNull(fixture.section.getSlice(TileWrapper.class).get(0, 5, 1));
        assertNull(fixture.section.getSlice(TreeBlockMaterial.class).get(0, 5, 1));
        assertFalse(fixture.section.hasSlice(TreeCanopy.class));
    }

    @Test
    @SuppressWarnings("unchecked")
    public void floatingContinuationRetainsCustomCanopyCondition() throws Exception {
        IObjectPlacer delegate = mock(IObjectPlacer.class);
        Engine engine = mock(Engine.class);
        EngineMantle engineMantle = mock(EngineMantle.class);
        Mantle<Matter> mantle = mock(Mantle.class);
        MantleChunk<Matter> firstChunk = mock(MantleChunk.class);
        MantleChunk<Matter> secondChunk = mock(MantleChunk.class);
        Matter first = new IrisMatter(16, 16, 16);
        Matter second = new IrisMatter(16, 16, 16);
        when(delegate.getEngine()).thenReturn(engine);
        when(delegate.beginTreeCanopy(any())).thenCallRealMethod();
        when(engine.getHeight()).thenReturn(64);
        when(engine.getMantle()).thenReturn(engineMantle);
        when(engineMantle.getMantle()).thenReturn(mantle);
        when(mantle.useChunk(0, 0)).thenReturn(firstChunk);
        when(mantle.useChunk(1, 0)).thenReturn(secondChunk);
        when(firstChunk.getOrCreate(0)).thenReturn(first);
        when(secondChunk.getOrCreate(0)).thenReturn(second);
        FloatingObjectPlacementTransaction transaction = new FloatingObjectPlacementTransaction(delegate,
                new ObjectContinuationBundle.PlacementKey(ObjectContinuationBundle.Kind.FLOATING, 0, 0, 0));
        transaction.set(15, 5, 1, trunk);
        assertTrue(transaction.beginTreeCanopy(new BlockPosition(16, 5, 1)));
        transaction.set(16, 5, 1, custom);
        transaction.setData(16, 5, 1, "floating-canopy");
        transaction.endTreeCanopy();

        assertEquals(FloatingObjectPlacementTransaction.CommitResult.COMMITTED, transaction.commit());

        Matter payload = second.<ObjectContinuationBundle>getSlice(ObjectContinuationBundle.class)
                .get(0, 0, 0).fragments().getFirst().decode();
        assertSame(TreeCanopy.INSTANCE, payload.getSlice(TreeCanopy.class).get(0, 5, 1));
        assertSame(canopy, payload.getSlice(NativeBlockState.class).get(0, 5, 1));
        assertEquals(Identifier.fromString("test:custom_canopy"), payload.getSlice(Identifier.class).get(0, 5, 1));
        assertEquals("floating-canopy", payload.getSlice(String.class).get(0, 5, 1));
    }

    private Matter customPayload() throws IOException {
        return decode(List.of(
                new ObjectDestinationTransaction.CustomBlockMutation(key(16, NativeBlockState.class), custom, true),
                new ObjectDestinationTransaction.SetMutation(key(16, String.class), "incoming-canopy", true)));
    }

    private static Matter decode(List<ObjectDestinationTransaction.Mutation> mutations) throws IOException {
        return Matter.read(new ByteArrayInputStream(ObjectSourcePlan.encode(mutations, 64)));
    }

    private static ObjectDestinationTransaction.DataKey key(int x, Class<?> type) {
        return new ObjectDestinationTransaction.DataKey(x, 5, 1, type);
    }

    private static final class ReplayFixture {
        private final Mantle<Matter> mantle;
        private final Matter section = new IrisMatter(16, 16, 16);
        private final Hunk<NativeBlockState> blocks = Hunk.newArrayHunk(16, 64, 16);

        @SuppressWarnings("unchecked")
        private ReplayFixture() {
            mantle = mock(Mantle.class);
            MantleChunk<Matter> chunk = mock(MantleChunk.class);
            when(mantle.useChunk(1, 0)).thenReturn(chunk);
            when(chunk.get(0)).thenReturn(section);
            when(chunk.getOrCreate(0)).thenReturn(section);
        }
    }
}
