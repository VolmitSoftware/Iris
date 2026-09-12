package art.arcane.iris.world.history;

import art.arcane.iris.pack.loading.IrisData;
import art.arcane.iris.generation.biome.IrisBiome;
import art.arcane.iris.generation.terrain.IrisDimension;
import art.arcane.iris.generation.terrain.IrisDimensionCarvingEntry;
import art.arcane.iris.generation.terrain.IrisDimensionStack;
import art.arcane.iris.generation.biome.IrisFloatingChildBiomes;
import art.arcane.iris.generation.terrain.IrisRegion;
import art.arcane.iris.generation.hydrology.IrisRiverPolicy;
import art.arcane.volmlib.util.collection.KList;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.util.Optional;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class SavedBiomeRecoveryTest {
    private static final String BIOME = "transition-demo";
    private static final String REGION = "transition-demo";
    private static final int CHUNK_X = -2;
    private static final int CHUNK_Z = 3;

    private final GenerationHistory history = mock(GenerationHistory.class);
    private final IrisData data = mock(IrisData.class, RETURNS_DEEP_STUBS);
    private final IrisDimension dimension = new IrisDimension();
    private final IrisRegion region = new IrisRegion();
    private final IrisBiome biome = new IrisBiome();
    private final SavedBiomeRecovery.Input input =
            new SavedBiomeRecovery.Input(history, CHUNK_X, CHUNK_Z, data, dimension);

    @Before
    public void prepareSingletonPack() {
        dimension.setLoadKey("overworld");
        dimension.setRegions(new KList<String>().qadd(REGION));
        region.setLoadKey(REGION);
        region.setLandBiomes(new KList<String>().qadd(BIOME));
        region.setSeaBiomes(new KList<String>().qadd(BIOME));
        region.setShoreBiomes(new KList<String>().qadd(BIOME));
        region.setCaveBiomes(new KList<String>().qadd(BIOME));
        biome.setLoadKey(BIOME);
        when(data.getRegionLoader().load(REGION)).thenReturn(region);
        when(data.getBiomeLoader().load(BIOME)).thenReturn(biome);
        when(history.resolveActivation(CHUNK_X, CHUNK_Z))
                .thenReturn(GenerationActivation.initial("a".repeat(64), 1L));
        GenerationEpoch epoch = mock(GenerationEpoch.class);
        GenerationEpoch.DimensionContract contract = mock(GenerationEpoch.DimensionContract.class);
        when(contract.dimensionKey()).thenReturn("overworld");
        when(contract.minHeight()).thenReturn(-64);
        when(contract.maxHeight()).thenReturn(320);
        when(contract.height()).thenReturn(384);
        when(epoch.dimensionContract()).thenReturn(contract);
        when(history.resolveEpoch(CHUNK_X, CHUNK_Z)).thenReturn(epoch);
        record(semantics(1L).seal().build());
    }

    @Test
    public void recoversAllColumnsAndEntireHeightFromTheOwningActivation() throws IOException {
        SavedBiomeChunk recovered = SavedBiomeRecovery.recover(input).orElseThrow();
        SavedBiomeChunk.Cell expected = new SavedBiomeChunk.Cell(1L, BIOME, REGION);
        assertEquals(new SavedBiomeChunk.Header(CHUNK_X, CHUNK_Z, 1L, -64, 384), recovered.header());
        for (int z = 0; z < 16; z++) {
            for (int x = 0; x < 16; x++) {
                assertEquals(expected, recovered.surfaceAt(x, z));
                assertEquals(expected, recovered.caveBaseAt(x, z));
                assertEquals(expected, recovered.biomeAt(x, -64, z));
                assertEquals(expected, recovered.biomeAt(x, 319, z));
                assertEquals(1, recovered.column(x, z).vertical().size());
            }
        }
    }

    @Test
    public void rejectsMissingOrUnsealedRecordedFacts() throws IOException {
        when(history.semantics(CHUNK_X, CHUNK_Z)).thenReturn(Optional.empty());
        assertTrue(SavedBiomeRecovery.recover(input).isEmpty());
        record(semantics(1L).build());
        assertTrue(SavedBiomeRecovery.recover(input).isEmpty());
    }

    @Test
    public void rejectsMultipleSurfaceOrRegionIdentities() throws IOException {
        record(semantics(1L).addSurfaceBiome("other").seal().build());
        assertTrue(SavedBiomeRecovery.recover(input).isEmpty());
        record(semantics(1L).addRegion("other").seal().build());
        assertTrue(SavedBiomeRecovery.recover(input).isEmpty());
    }

    @Test
    public void rejectsInconsistentOwnershipAndDifferentCaveIdentity() throws IOException {
        record(semantics(2L).seal().build());
        assertTrue(SavedBiomeRecovery.recover(input).isEmpty());
        record(semantics(1L).addCaveBiome("other").seal().build());
        assertTrue(SavedBiomeRecovery.recover(input).isEmpty());
    }

    @Test
    public void acceptsRecordedCavesWithTheSameIdentity() throws IOException {
        record(semantics(1L).addCaveBiome(BIOME).seal().build());
        assertTrue(SavedBiomeRecovery.recover(input).isPresent());
    }

    @Test
    public void rejectsMissingDefinitions() throws IOException {
        when(data.getBiomeLoader().load(BIOME)).thenReturn(null);
        assertTrue(SavedBiomeRecovery.recover(input).isEmpty());
        when(data.getBiomeLoader().load(BIOME)).thenReturn(biome);
        when(data.getRegionLoader().load(REGION)).thenReturn(null);
        assertTrue(SavedBiomeRecovery.recover(input).isEmpty());
    }

    @Test
    public void rejectsPoolsWithUnrecordedChoicesOrNoVerticalChoice() throws IOException {
        region.getCaveBiomes().add("other");
        assertTrue(SavedBiomeRecovery.recover(input).isEmpty());
        region.getCaveBiomes().clear();
        assertTrue(SavedBiomeRecovery.recover(input).isEmpty());
        region.getCaveBiomes().add(BIOME);
        region.getSeaBiomes().add("other");
        assertTrue(SavedBiomeRecovery.recover(input).isEmpty());
    }

    @Test
    public void rejectsChildrenAndFloatingBiomes() throws IOException {
        biome.getChildren().add("other");
        assertTrue(SavedBiomeRecovery.recover(input).isEmpty());
        biome.getChildren().clear();
        biome.getFloatingChildBiomes().add(new IrisFloatingChildBiomes());
        assertTrue(SavedBiomeRecovery.recover(input).isEmpty());
    }

    @Test
    public void rejectsStackedUpperAndCarvingOverrides() throws IOException {
        dimension.setDimensionStack(new IrisDimensionStack());
        assertTrue(SavedBiomeRecovery.recover(input).isEmpty());
        dimension.setDimensionStack(null);
        dimension.setUpperDimension("upper");
        assertTrue(SavedBiomeRecovery.recover(input).isEmpty());
        dimension.setUpperDimension("none");
        dimension.getCarving().add(new IrisDimensionCarvingEntry());
        assertTrue(SavedBiomeRecovery.recover(input).isEmpty());
    }

    @Test
    public void rejectsDifferingRiverOverridesAtEachScope() throws IOException {
        IrisRiverPolicy policy = new IrisRiverPolicy().setFloodedCaveBiomes(new KList<String>().qadd("other"));
        dimension.setRiverPolicy(policy);
        assertTrue(SavedBiomeRecovery.recover(input).isEmpty());
        dimension.setRiverPolicy(null);
        region.setRiverPolicy(policy);
        assertTrue(SavedBiomeRecovery.recover(input).isEmpty());
        region.setRiverPolicy(null);
        biome.setRiverPolicy(policy);
        assertTrue(SavedBiomeRecovery.recover(input).isEmpty());
    }

    @Test
    public void rejectsDifferentDimensionAndFocusSelectors() throws IOException {
        dimension.setLoadKey("other");
        assertTrue(SavedBiomeRecovery.recover(input).isEmpty());
        dimension.setLoadKey("overworld");
        dimension.setFocus("other");
        assertTrue(SavedBiomeRecovery.recover(input).isEmpty());
        dimension.setFocus("");
        dimension.getRegions().add("other");
        assertTrue(SavedBiomeRecovery.recover(input).isEmpty());
    }

    @Test
    public void rejectsOneBlendedColumnEvenWhenAllOtherColumnsAreNew() throws IOException {
        TransitionGenerationPlan transition = nextActivation();
        when(transition.newEpochWeightAt(-17, 63)).thenReturn(0.999D);
        assertTrue(SavedBiomeRecovery.recover(input).isEmpty());
        verify(transition).newEpochWeightAt(-17, 63);
    }

    @Test
    public void retainsLaterActivationIdentityForUnblendedChunks() throws IOException {
        nextActivation();
        SavedBiomeChunk recovered = SavedBiomeRecovery.recover(input).orElseThrow();
        assertEquals(2L, recovered.activationId());
        assertEquals(2L, recovered.biomeAt(15, 319, 15).activationId());
    }

    @Test
    public void propagatesUnreadableTransitionEvidence() throws IOException {
        nextActivation();
        when(history.transitionPlan(2L)).thenThrow(new IOException("Unreadable frozen transition"));
        assertThrows(IOException.class, () -> SavedBiomeRecovery.recover(input));
    }

    private TransitionGenerationPlan nextActivation() throws IOException {
        when(history.resolveActivation(CHUNK_X, CHUNK_Z))
                .thenReturn(GenerationActivation.next(2L, "b".repeat(64), 1L, 2L, 64));
        record(semantics(2L).seal().build());
        TransitionGenerationPlan transition = mock(TransitionGenerationPlan.class);
        when(transition.newEpochWeightAt(anyInt(), anyInt())).thenReturn(1D);
        when(history.transitionPlan(2L)).thenReturn(transition);
        return transition;
    }

    private void record(ChunkGenerationSemantics semantics) {
        when(history.semantics(CHUNK_X, CHUNK_Z)).thenReturn(Optional.of(semantics));
    }

    private static ChunkGenerationSemantics.Builder semantics(long activationId) {
        return ChunkGenerationSemantics.builder(CHUNK_X, CHUNK_Z, activationId)
                .addSurfaceBiome(BIOME).addRegion(REGION);
    }
}
