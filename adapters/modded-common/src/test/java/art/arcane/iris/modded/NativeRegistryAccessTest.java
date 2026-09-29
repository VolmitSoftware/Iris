package art.arcane.iris.modded;

import art.arcane.volmlib.nativelib.minecraft26_2.modded.NativeRegistryAccess;
import art.arcane.volmlib.nativelib.minecraft26_2.modded.NativeModdedServer;
import art.arcane.volmlib.nativelib.terrain.NativeBlockProperty;
import net.minecraft.SharedConstants;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.Bootstrap;
import net.minecraft.server.MinecraftServer;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class NativeRegistryAccessTest {
    @BeforeClass
    public static void bootstrapMinecraftRegistries() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    public void statePropertiesPreserveTypesDefaultsAndSharedGroups() {
        NativeRegistryAccess access = NativeModdedServer.registryAccess(() -> null, message -> {});
        Map<String, List<NativeBlockProperty>> properties = access.blockStateProperties();

        assertSame(properties.get("minecraft:stone"), properties.get("minecraft:dirt"));
        NativeBlockProperty power = properties.get("minecraft:redstone_wire").stream()
                .filter(property -> property.name().equals("power")).findFirst().orElseThrow();
        assertEquals("integer", power.jsonType());
        assertEquals(0, power.defaultValue());
        assertEquals(16, power.allowedValues().size());
        assertTrue(power.allowedValues().contains(15));
        assertNull(power.numericRange());
    }

    @Test
    public void staticRegistriesResolveWithoutAnActiveServerAndDynamicReadsStayEmpty() {
        List<String> warnings = new ArrayList<>();
        NativeRegistryAccess access = NativeModdedServer.registryAccess(() -> null, warnings::add);

        assertEquals("minecraft:diamond_sword", access.item("DIAMOND SWORD").key());
        assertEquals("creature", access.entity("minecraft:cow").spawnCategory());
        assertNull(access.item("example:unknown"));
        assertTrue(access.biomeKeys().isEmpty());
        assertTrue(access.structureKeys().isEmpty());
        assertTrue(access.enchantmentKeys().isEmpty());
        assertTrue(access.lootTableKeys().isEmpty());
        assertEquals(List.of("biome", "structure", "enchantment", "loot table"), warnings);
    }

    @Test
    public void dynamicRegistriesFollowServerStartupReloadAndShutdown() {
        AtomicReference<NativeModdedServer> activeServer = new AtomicReference<>();
        List<String> warnings = new ArrayList<>();
        NativeRegistryAccess access = NativeModdedServer.registryAccess(activeServer::get, warnings::add);
        assertTrue(access.lootTableKeys().isEmpty());

        MinecraftServer server = mock(MinecraftServer.class, RETURNS_DEEP_STUBS);
        when(server.registryAccess().lookupOrThrow(Registries.BIOME).listElementIds())
                .thenAnswer(invocation -> Stream.of(ResourceKey.create(Registries.BIOME, Identifier.parse("example:biome"))));
        when(server.reloadableRegistries().lookup().lookupOrThrow(Registries.LOOT_TABLE).listElementIds())
                .thenAnswer(invocation -> Stream.of(ResourceKey.create(Registries.LOOT_TABLE, Identifier.parse("example:initial"))));
        activeServer.set(NativeModdedServer.fromHandle(server));

        assertEquals(List.of("example:biome"), access.biomeKeys());
        assertEquals(List.of("example:initial"), access.lootTableKeys());
        HolderLookup.Provider reloadedRegistries = mock(HolderLookup.Provider.class, RETURNS_DEEP_STUBS);
        when(reloadedRegistries.lookupOrThrow(Registries.LOOT_TABLE).listElementIds())
                .thenAnswer(invocation -> Stream.of(ResourceKey.create(Registries.LOOT_TABLE, Identifier.parse("example:reloaded"))));
        when(server.reloadableRegistries().lookup()).thenReturn(reloadedRegistries);
        assertEquals(List.of("example:reloaded"), access.lootTableKeys());
        activeServer.set(null);
        assertTrue(access.biomeKeys().isEmpty());
        assertTrue(access.lootTableKeys().isEmpty());
        assertEquals(List.of("loot table", "biome", "loot table"), warnings);
    }
}
