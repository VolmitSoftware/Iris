package art.arcane.volmlib.nativelib.minecraft26_2.modded;

import art.arcane.volmlib.nativelib.terrain.NativeModdedBiomePolicy;
import com.google.gson.JsonElement;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.MapCodec;
import net.minecraft.SharedConstants;
import net.minecraft.core.Holder;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.Climate;
import org.junit.BeforeClass;
import org.junit.Test;

import java.lang.reflect.Constructor;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class NativeModdedBiomeSourceCodecTest {
    @BeforeClass
    public static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    public void runtimeSourceEncodeReportsErrorInsteadOfThrowing() {
        DataResult<JsonElement> result = BiomeSource.CODEC.encodeStart(JsonOps.INSTANCE, runtimeSource("epoch:unused"));
        assertTrue(result.isError());
    }

    @Test
    @SuppressWarnings("unchecked")
    public void structureStateSourceEncodesItsGenerationKey() throws ReflectiveOperationException {
        BiomeSource structureState = structureState(runtimeSource("epoch:example"));
        MapCodec<BiomeSource> codec = (MapCodec<BiomeSource>) NativeModdedBiomeSource.STRUCTURE_STATE_CODEC;

        JsonElement encoded = codec.codec().encodeStart(JsonOps.INSTANCE, structureState).getOrThrow();

        assertEquals("epoch:example", encoded.getAsJsonObject().get("generation").getAsString());
        assertTrue(codec.codec().parse(JsonOps.INSTANCE, encoded).isError());
    }

    private static BiomeSource structureState(NativeModdedBiomeSource<?> source) throws ReflectiveOperationException {
        Class<?> type = Class.forName(NativeModdedBiomeSource.class.getName() + "$StructureStateBiomeSource");
        Constructor<?> constructor = type.getDeclaredConstructor(NativeModdedBiomeSource.class, Set.class);
        constructor.setAccessible(true);
        return (BiomeSource) constructor.newInstance(source, Set.<Holder<Biome>>of());
    }

    @SuppressWarnings("unchecked")
    private static NativeModdedBiomeSource<NativeModdedBiomePolicy<Holder<Biome>, Climate.Sampler>> runtimeSource(String key) {
        NativeModdedBiomePolicy<Holder<Biome>, Climate.Sampler> policy = mock(NativeModdedBiomePolicy.class);
        when(policy.structureStateKey()).thenReturn(key);
        return new NativeModdedBiomeSource<>(new NativeModdedBiomeSource.Options<>(
                mock(BiomeSource.class), () -> null, access -> policy));
    }
}
