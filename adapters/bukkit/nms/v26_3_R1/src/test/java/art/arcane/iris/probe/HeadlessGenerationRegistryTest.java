package art.arcane.iris.probe;

import art.arcane.iris.spi.PlatformGenerationRegistry;
import art.arcane.volmlib.nativelib.v26_3_R1.terrain.NativeWorldGenerationImpl;
import net.minecraft.data.registries.VanillaRegistries;
import org.bukkit.Bukkit;
import org.junit.Test;

import java.io.File;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;

public final class HeadlessGenerationRegistryTest {
    @Test
    public void capturesGeneratedDefinitionsThroughNativeCodecs() throws Exception {
        HeadlessNativeBootstrap.initialize();
        String biome = HeadlessNativeTestRegistries.generatedBiome();
        String dimension = HeadlessNativeTestRegistries.generatedDimension();
        HeadlessNativePlatform platform = new HeadlessNativePlatform(new File("unused-native-platform"),
            HeadlessNativeTestRegistries::get);
        PlatformGenerationRegistry registry = platform.generationRegistry();
        assertEquals("bukkit-generation-registry-v1", registry.runtimeIdentity());
        assertEquals(new NativeWorldGenerationImpl().generationRendererIdentity(),
                registry.generatedDefinitionRendererIdentity());
        assertEquals(registry.canonicalDefinition("minecraft:worldgen/biome", "iris:native_test", biome),
                registry.generatedDefinition("minecraft:worldgen/biome", "iris:native_test"));
        assertEquals(registry.canonicalDefinition("minecraft:dimension_type", "iris:native_test", dimension),
                registry.generatedDefinition("minecraft:dimension_type", "iris:native_test"));
        assertEquals(PlatformGenerationRegistry.Definition.resourceIdentity("minecraft:block", "minecraft:stone"),
                registry.definition("minecraft:block", "minecraft:stone"));
        assertThrows(IllegalStateException.class,
                () -> registry.generatedDefinition("minecraft:worldgen/biome", "iris:absent"));
        assertThrows(IllegalArgumentException.class,
                () -> registry.canonicalDefinition("minecraft:worldgen/biome", "iris:invalid", "{}"));
        assertNull(Bukkit.getServer());
    }

    @Test
    public void rejectsContractCaptureBeforeCoherentRegistriesLoad() {
        HeadlessNativeBootstrap.initialize();
        HeadlessNativePlatform platform = new HeadlessNativePlatform(new File("unused-native-platform"),
            VanillaRegistries::createWorldLookup);
        assertThrows(IllegalStateException.class,
            () -> platform.generationRegistry().definition("minecraft:block", "minecraft:stone"));
    }
}
