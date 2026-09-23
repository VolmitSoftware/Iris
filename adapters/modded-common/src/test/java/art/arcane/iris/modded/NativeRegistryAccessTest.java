package art.arcane.iris.modded;

import art.arcane.volmlib.nativelib.minecraft26_2.modded.NativeRegistryAccess;
import art.arcane.volmlib.nativelib.terrain.NativeBlockProperty;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public class NativeRegistryAccessTest {
    @BeforeClass
    public static void bootstrapMinecraftRegistries() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    public void statePropertiesPreserveTypesDefaultsAndSharedGroups() {
        NativeRegistryAccess access = new NativeRegistryAccess(new NativeRegistryAccess.Configuration(() -> null, () -> null, message -> {}));
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
        NativeRegistryAccess access = new NativeRegistryAccess(new NativeRegistryAccess.Configuration(() -> null, () -> null, warnings::add));

        assertEquals("minecraft:diamond_sword", access.item("DIAMOND SWORD").key());
        assertEquals("creature", access.entity("minecraft:cow").spawnCategory());
        assertNull(access.item("example:unknown"));
        assertTrue(access.biomeKeys().isEmpty());
        assertTrue(access.structureKeys().isEmpty());
        assertTrue(access.enchantmentKeys().isEmpty());
        assertEquals(List.of("biome", "structure", "enchantment"), warnings);
    }
}
