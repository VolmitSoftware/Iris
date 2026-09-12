package art.arcane.iris.pack;

import art.arcane.iris.pack.ContentKeyValidator.ContentKeyError;
import art.arcane.iris.pack.ContentKeyValidator.ContentRegistry;
import art.arcane.iris.spi.PlatformBiome;
import art.arcane.iris.spi.PlatformBlockProperty;
import art.arcane.iris.spi.PlatformBlockState;
import art.arcane.iris.spi.PlatformEntityType;
import art.arcane.iris.spi.PlatformItem;
import art.arcane.iris.spi.PlatformNumericRange;
import art.arcane.iris.spi.PlatformRegistries;
import org.junit.Test;

import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class ContentKeyValidatorTest {
    private static PlatformRegistries registries() {
        return new FakeRegistries(
                List.of("minecraft:stone", "minecraft:cobblestone", "minecraft:oak_log", "minecraft:grass_block"),
                List.of("minecraft:diamond", "minecraft:wooden_pickaxe", "minecraft:stone_pickaxe"),
                List.of("minecraft:zombie", "minecraft:creeper"),
                Map.of());
    }

    private static PlatformRegistries registriesWithProperties() {
        return new FakeRegistries(
                List.of("minecraft:stone", "minecraft:oak_log", "create:cogwheel"),
                List.of(),
                List.of(),
                Map.of(
                        "minecraft:oak_log", List.of(
                                new PlatformBlockProperty("axis", "string", "y", List.of("x", "y", "z"), null),
                                new PlatformBlockProperty("waterlogged", "boolean", false, List.of(true, false), null)),
                        // The Bukkit shape for a numeric property: no enumerable values, bounds instead. Modded
                        // enumerates 0..15 into allowedValues, so both must be validated the same way.
                        "minecraft:water", List.of(
                                new PlatformBlockProperty("level", "integer", 0, List.of(),
                                        new PlatformNumericRange(0, 15, false, false)),
                                new PlatformBlockProperty("custom", "string", "a", List.of(), null)),
                        "create:cogwheel", List.of()));
    }

    @Test
    public void validateBlockStatePropertiesFlagsValueAboveDeclaredRange() {
        List<String> messages = ContentKeyValidator.validateBlockStateProperties(registriesWithProperties(),
                List.of("minecraft:water[level=99]"));
        assertEquals(1, messages.size());
        assertTrue(messages.get(0), messages.get(0).contains("does not accept '99'"));
        assertTrue(messages.get(0), messages.get(0).contains("at least 0"));
        assertTrue(messages.get(0), messages.get(0).contains("at most 15"));
    }

    @Test
    public void validateBlockStatePropertiesFlagsNonNumericValueForNumericProperty() {
        List<String> messages = ContentKeyValidator.validateBlockStateProperties(registriesWithProperties(),
                List.of("minecraft:water[level=full]"));
        assertEquals(1, messages.size());
        assertTrue(messages.get(0), messages.get(0).contains("is numeric and does not accept 'full'"));
    }

    @Test
    public void validateBlockStatePropertiesAcceptsValueInsideDeclaredRange() {
        assertTrue(ContentKeyValidator.validateBlockStateProperties(registriesWithProperties(),
                List.of("minecraft:water[level=0]", "minecraft:water[level=15]", "minecraft:water[level=7]")).isEmpty());
    }

    @Test
    public void validateBlockStatePropertiesStaysSilentWithoutValuesOrRange() {
        assertTrue(ContentKeyValidator.validateBlockStateProperties(registriesWithProperties(),
                List.of("minecraft:water[custom=anything]")).isEmpty());
    }

    @Test
    public void validateFlagsUnknownBlockKeyWithSingleError() {
        List<ContentKeyError> errors = ContentKeyValidator.validate(registries(),
                List.of("minecraft:stone", "minecraft:not_a_real_block"), List.of(), List.of());
        assertEquals(1, errors.size());
        ContentKeyError error = errors.get(0);
        assertEquals("minecraft:not_a_real_block", error.key());
        assertEquals(ContentRegistry.BLOCK, error.registry());
        assertTrue(error.namespaceLoaded());
    }

    @Test
    public void validateAcceptsNativeAndQualifiedExternalBlockTypes() {
        PlatformRegistries registries = mock(PlatformRegistries.class);
        when(registries.blockKeys()).thenReturn(List.of("minecraft:stone"));
        when(registries.blockTypeKeys()).thenReturn(List.of("minecraft:stone", "oraxen:caveblock",
                "oraxen:oraxen/caveblock", "custom:marble", "itemsadder:custom/marble"));

        List<ContentKeyError> errors = ContentKeyValidator.validate(registries,
                List.of("stone", "oraxen:caveblock", "oraxen:oraxen/caveblock",
                        "custom:marble", "itemsadder:custom/marble"), List.of(), List.of());

        assertTrue(errors.toString(), errors.isEmpty());
    }

    @Test
    public void validateStillReportsMissingExternalBlockTypes() {
        PlatformRegistries registries = mock(PlatformRegistries.class);
        when(registries.blockKeys()).thenReturn(List.of("minecraft:stone"));
        when(registries.blockTypeKeys()).thenReturn(List.of("minecraft:stone", "oraxen:oraxen/caveblock"));

        List<ContentKeyError> errors = ContentKeyValidator.validate(registries,
                List.of("oraxen:oraxen/caveblok", "itemsadder:custom/marble"), List.of(), List.of());

        assertEquals(2, errors.size());
        assertEquals("oraxen:oraxen/caveblok", errors.get(0).key());
        assertTrue(errors.get(0).namespaceLoaded());
        assertEquals("oraxen:oraxen/caveblock", errors.get(0).suggestion());
        assertEquals("itemsadder:custom/marble", errors.get(1).key());
        assertFalse(errors.get(1).namespaceLoaded());
    }

    @Test
    public void validateFlagsUnloadedNamespaceWhenNamespaceMissing() {
        List<ContentKeyError> errors = ContentKeyValidator.validate(registries(),
                List.of("create:cogwheel"), List.of(), List.of());
        assertEquals(1, errors.size());
        assertFalse(errors.get(0).namespaceLoaded());
        assertEquals("create", ContentKeyValidator.namespaceOf(errors.get(0).key()));
    }

    @Test
    public void validateSuggestsNearestKeyForTypo() {
        List<ContentKeyError> errors = ContentKeyValidator.validate(registries(),
                List.of("minecraft:cobblstone"), List.of(), List.of());
        assertEquals(1, errors.size());
        assertEquals("minecraft:cobblestone", errors.get(0).suggestion());
    }

    @Test
    public void validateDedupsRepeatedKeyIntoOneError() {
        List<ContentKeyError> errors = ContentKeyValidator.validate(registries(),
                List.of("minecraft:ghostblock", "minecraft:ghostblock", "ghostblock"), List.of(), List.of());
        assertEquals(1, errors.size());
        assertEquals("minecraft:ghostblock", errors.get(0).key());
    }

    @Test
    public void validateDefaultsBareKeyToMinecraftNamespace() {
        List<ContentKeyError> errors = ContentKeyValidator.validate(registries(),
                List.of("stone", "oak_log"), List.of(), List.of());
        assertTrue(errors.isEmpty());
    }

    @Test
    public void validateStripsBlockStateBeforeLookup() {
        List<ContentKeyError> errors = ContentKeyValidator.validate(registries(),
                List.of("minecraft:oak_log[axis=y]"), List.of(), List.of());
        assertTrue(errors.isEmpty());
    }

    @Test
    public void validateReportsUnknownItemAndEntityPerRegistry() {
        List<ContentKeyError> errors = ContentKeyValidator.validate(registries(),
                List.of(), List.of("minecraft:not_an_item"), List.of("minecraft:not_an_entity"));
        assertEquals(2, errors.size());
        assertTrue(errors.stream().anyMatch(e -> e.registry() == ContentRegistry.ITEM && e.key().equals("minecraft:not_an_item")));
        assertTrue(errors.stream().anyMatch(e -> e.registry() == ContentRegistry.ENTITY && e.key().equals("minecraft:not_an_entity")));
    }

    @Test
    public void validateReturnsEmptyWhenRegistriesNull() {
        assertTrue(ContentKeyValidator.validate(null, List.of("minecraft:whatever"), List.of(), List.of()).isEmpty());
    }

    @Test
    public void validateBlockStatePropertiesFlagsUnknownPropertyWithSuggestion() {
        List<String> messages = ContentKeyValidator.validateBlockStateProperties(registriesWithProperties(),
                List.of("minecraft:oak_log[axi=y]"));
        assertEquals(1, messages.size());
        assertTrue(messages.get(0).contains("has no property 'axi'"));
        assertTrue(messages.get(0).contains("did you mean 'axis'"));
    }

    @Test
    public void validateBlockStatePropertiesFlagsDisallowedValue() {
        List<String> messages = ContentKeyValidator.validateBlockStateProperties(registriesWithProperties(),
                List.of("minecraft:oak_log[axis=q]"));
        assertEquals(1, messages.size());
        assertTrue(messages.get(0).contains("does not accept 'q'"));
        assertTrue(messages.get(0).contains("allowed: x, y, z"));
    }

    @Test
    public void validateBlockStatePropertiesAcceptsValidState() {
        assertTrue(ContentKeyValidator.validateBlockStateProperties(registriesWithProperties(),
                List.of("minecraft:oak_log[axis=z,waterlogged=true]")).isEmpty());
    }

    @Test
    public void validateBlockStatePropertiesSkipsBlocksWithoutDeclaredProperties() {
        assertTrue(ContentKeyValidator.validateBlockStateProperties(registriesWithProperties(),
                List.of("create:cogwheel[axis=y]", "minecraft:unknown_block[axis=y]")).isEmpty());
    }

    @Test
    public void validateBlockStatePropertiesDedupsRepeatedIssue() {
        List<String> messages = ContentKeyValidator.validateBlockStateProperties(registriesWithProperties(),
                List.of("minecraft:oak_log[axis=q]", "minecraft:oak_log[axis=q]"));
        assertEquals(1, messages.size());
    }

    @Test
    public void validateBlockStatePropertiesReturnsEmptyWithoutPropertyData() {
        assertTrue(ContentKeyValidator.validateBlockStateProperties(registries(),
                List.of("minecraft:oak_log[axi=y]")).isEmpty());
    }

    @Test
    public void propertySectionOfExtractsStateBody() {
        assertEquals("axis=y", ContentKeyValidator.propertySectionOf("minecraft:oak_log[axis=y]"));
        assertNull(ContentKeyValidator.propertySectionOf("minecraft:oak_log"));
    }

    @Test
    public void strictContentFollowsSystemProperty() {
        String previous = System.getProperty("iris.strictContent");
        try {
            System.setProperty("iris.strictContent", "true");
            assertTrue(ContentKeyValidator.strictContent());
            System.setProperty("iris.strictContent", "false");
            assertFalse(ContentKeyValidator.strictContent());
        } finally {
            if (previous == null) {
                System.clearProperty("iris.strictContent");
            } else {
                System.setProperty("iris.strictContent", previous);
            }
        }
    }

    private record FakeRegistries(List<String> blocks, List<String> items, List<String> entities,
                                  Map<String, List<PlatformBlockProperty>> properties) implements PlatformRegistries {
        @Override
        public PlatformBlockState block(String key) {
            return null;
        }

        @Override
        public PlatformBlockState blockOrNull(String key) {
            return null;
        }

        @Override
        public PlatformBlockState blockOrNull(String key, boolean warn) {
            return null;
        }

        @Override
        public PlatformBlockState air() {
            return null;
        }

        @Override
        public PlatformBlockState deepSlateOre(PlatformBlockState block, PlatformBlockState ore) {
            return null;
        }

        @Override
        public PlatformBiome biome(String key) {
            return null;
        }

        @Override
        public PlatformItem item(String key) {
            return null;
        }

        @Override
        public PlatformEntityType entity(String key) {
            return null;
        }

        @Override
        public List<String> blockKeys() {
            return blocks;
        }

        @Override
        public List<String> biomeKeys() {
            return List.of();
        }

        @Override
        public List<String> structureKeys() {
            return List.of();
        }

        @Override
        public List<String> itemKeys() {
            return items;
        }

        @Override
        public List<String> entityKeys() {
            return entities;
        }

        @Override
        public List<String> blockTypeKeys() {
            return blocks;
        }

        @Override
        public List<String> enchantmentKeys() {
            return List.of();
        }

        @Override
        public List<String> potionEffectKeys() {
            return List.of();
        }

        @Override
        public List<String> lootTableKeys() {
            return List.of();
        }

        @Override
        public Map<String, List<PlatformBlockProperty>> blockStateProperties() {
            return properties;
        }
    }
}
