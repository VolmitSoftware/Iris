package art.arcane.iris.probe;

import art.arcane.volmlib.nativelib.minecraft26_2.modded.NativeRegistryAccess;
import net.minecraft.core.registries.Registries;
import org.bukkit.Bukkit;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.Map;
import java.io.File;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;

public final class HeadlessNativeRegistryTest {
    @BeforeClass
    public static void bootstrap() {
        HeadlessNativeBootstrap.initialize();
    }

    @Test
    public void corePlatformUsesNativeResolutionWithoutBiomeFallback() throws Exception {
        NativeRegionTerrainWriter writer = new NativeRegionTerrainWriter(HeadlessNativeTestRegistries.get());
        HeadlessNativePlatform platform = new HeadlessNativePlatform(new File("unused-native-platform"), writer::registries);
        assertEquals("headless-native-core", platform.platformName());
        assertTrue(platform.block("minecraft:stone").isSolid());
        assertFalse(platform.block("minecraft:short_grass").isSolid());
        assertTrue(platform.block("minecraft:oak_log").isTreeBlock());
        assertTrue(platform.biomeWriter().biomeIdFor("minecraft:plains") >= 0);
        assertThrows(IllegalStateException.class, () -> platform.biomeWriter().biomeIdFor("iris:unknown"));
        assertThrows(UnsupportedOperationException.class, platform::lootTableKeys);
    }

    @Test
    public void resolvesNativeDynamicAndStaticRegistriesWithoutServer() throws Exception {
        NativeRegionTerrainWriter writer = new NativeRegionTerrainWriter(HeadlessNativeTestRegistries.get());
        NativeRegistryAccess access = new NativeRegistryAccess(new NativeRegistryAccess.Configuration(
                writer::registries, writer::registries, message -> {
                    throw new AssertionError("Unavailable registry: " + message);
                }));
        assertNull(Bukkit.getServer());
        assertEquals("iris:headless/test", access.biome("iris:headless/test").key());
        assertNotNull(access.biome("minecraft:plains"));
        assertNull(access.biome("iris:unknown"));
        assertTrue(access.structureKeys().contains("minecraft:mineshaft"));
        assertTrue(access.enchantmentKeys().contains("minecraft:fortune"));
        assertEquals("minecraft:diamond_sword", access.item("DIAMOND SWORD").key());
        assertEquals("creature", access.entity("minecraft:cow").spawnCategory());
        assertTrue(access.blockStateProperties().containsKey("minecraft:oak_log"));
    }
}
