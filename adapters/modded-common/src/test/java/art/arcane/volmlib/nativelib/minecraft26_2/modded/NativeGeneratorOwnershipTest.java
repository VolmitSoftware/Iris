package art.arcane.volmlib.nativelib.minecraft26_2.modded;

import art.arcane.iris.modded.IrisModdedChunkGenerator;

import art.arcane.volmlib.nativelib.minecraft26_2.modded.NativeGeneratorContext;
import art.arcane.volmlib.nativelib.minecraft26_2.modded.NativeWorldGenerators;
import art.arcane.volmlib.nativelib.terrain.NativeWorld;
import com.mojang.serialization.JsonOps;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.chunk.ChunkGenerator;
import org.junit.Test;
import org.junit.BeforeClass;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;

import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class NativeGeneratorOwnershipTest {
    @BeforeClass
    public static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    public void unboundGeneratorKeepsMetadataAndSerializedFieldNames() {
        IrisModdedChunkGenerator owner = new IrisModdedChunkGenerator(
                new NativeGeneratorContext(mock(BiomeSource.class), "example:surface"));
        NativeModdedChunkGenerator<?, ?, ?> nativeGenerator = (NativeModdedChunkGenerator<?, ?, ?>) owner.nativeGenerator();
        assertSame(owner, nativeGenerator.owner());
        assertEquals(-64, nativeGenerator.getMinY());
        assertEquals(384, nativeGenerator.getGenDepth());
        assertEquals(63, nativeGenerator.getSeaLevel());
        assertEquals("example:surface", nativeGenerator.context().dimensionKey());
        Set<String> fields = IrisModdedChunkGenerator.DEFINITION.codec().keys(JsonOps.INSTANCE)
                .map(value -> value.getAsString()).collect(Collectors.toSet());
        assertEquals(Set.of("biome_source", "dimension"), fields);
    }

    @Test
    public void worldLookupReturnsPolicyOwnerOnlyWhileNativeGeneratorIsInstalled() {
        IrisModdedChunkGenerator owner = new IrisModdedChunkGenerator(
                new NativeGeneratorContext(mock(BiomeSource.class), "example:surface"));
        NativeModdedChunkGenerator<?, ?, ?> nativeGenerator = (NativeModdedChunkGenerator<?, ?, ?>) owner.nativeGenerator();
        ServerLevel level = mock(ServerLevel.class);
        ServerChunkCache chunks = mock(ServerChunkCache.class);
        NativeWorld world = mock(NativeWorld.class);
        when(world.nativeHandle()).thenReturn(level);
        when(level.getChunkSource()).thenReturn(chunks);
        when(chunks.getGenerator()).thenReturn(nativeGenerator);
        assertSame(owner, NativeWorldGenerators.find(world, IrisModdedChunkGenerator.class));
        assertTrue(nativeGenerator.represents(world));

        when(chunks.getGenerator()).thenReturn(mock(ChunkGenerator.class));
        assertNull(NativeWorldGenerators.find(world, IrisModdedChunkGenerator.class));
        assertFalse(nativeGenerator.represents(world));
    }
}
