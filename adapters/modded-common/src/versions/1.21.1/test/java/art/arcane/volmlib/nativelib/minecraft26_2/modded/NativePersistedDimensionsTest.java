package art.arcane.volmlib.nativelib.minecraft26_2.modded;

import net.minecraft.SharedConstants;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.levelgen.WorldDimensions;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.Assert.assertSame;
import static org.mockito.Mockito.mock;

public class NativePersistedDimensionsTest {
    private static final Holder<DimensionType> TYPE = Holder.direct(mock(DimensionType.class));

    @BeforeClass
    public static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    public void replacedDimensionsKeepTheirSavedGenerator() {
        LevelStem savedNether = new LevelStem(TYPE, mock(ChunkGenerator.class));
        LevelStem overworld = new LevelStem(TYPE, mock(ChunkGenerator.class));
        LevelStem irisNether = new LevelStem(TYPE, mock(NativeModdedChunkGenerator.class));
        NativePersistedDimensions.loaded(dimensions(LevelStem.OVERWORLD, overworld, LevelStem.NETHER, savedNether));

        WorldDimensions persisted = NativePersistedDimensions.persisted(
                dimensions(LevelStem.OVERWORLD, overworld, LevelStem.NETHER, irisNether));

        assertSame(savedNether, persisted.dimensions().get(LevelStem.NETHER));
        assertSame(overworld, persisted.dimensions().get(LevelStem.OVERWORLD));
    }

    @Test
    public void irisWorldsAndNewDimensionsPersistAsTheyRun() {
        LevelStem savedIris = new LevelStem(TYPE, mock(NativeModdedChunkGenerator.class));
        LevelStem liveIris = new LevelStem(TYPE, mock(NativeModdedChunkGenerator.class));
        LevelStem addedIris = new LevelStem(TYPE, mock(NativeModdedChunkGenerator.class));
        NativePersistedDimensions.loaded(dimensions(LevelStem.OVERWORLD, savedIris, LevelStem.END, savedIris));
        WorldDimensions live = dimensions(LevelStem.OVERWORLD, liveIris, LevelStem.NETHER, addedIris);

        assertSame(live, NativePersistedDimensions.persisted(live));
    }

    private static WorldDimensions dimensions(ResourceKey<LevelStem> firstKey, LevelStem first,
                                              ResourceKey<LevelStem> secondKey, LevelStem second) {
        Map<ResourceKey<LevelStem>, LevelStem> stems = new LinkedHashMap<>();
        stems.put(firstKey, first);
        stems.put(secondKey, second);
        return new WorldDimensions(stems);
    }
}
