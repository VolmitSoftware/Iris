package art.arcane.iris.modded;

import art.arcane.iris.structure.placement.LootResolver;
import art.arcane.iris.world.loot.IrisLootMode;
import art.arcane.volmlib.util.math.RNG;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.RandomizableContainer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootTable;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.lang.reflect.Proxy;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class ModdedLootApplierTest {
    @Test
    public void addAppendsSourcesInOrder() {
        List<String> sources = new ArrayList<>(List.of("placement-native"));

        LootResolver.injectSources(sources, List.of("dimension-iris", "region-iris"), IrisLootMode.ADD, false);

        assertEquals(List.of("placement-native", "dimension-iris", "region-iris"), sources);
    }

    @Test
    public void clearRemovesEverythingAndContributesNothing() {
        List<String> sources = new ArrayList<>(List.of("placement-native", "dimension-iris"));

        LootResolver.injectSources(sources, List.of("biome-iris"), IrisLootMode.CLEAR, false);

        assertEquals(List.of(), sources);
    }

    @Test
    public void replaceRemovesNativeAndIrisSourcesBeforeAdding() {
        List<String> sources = new ArrayList<>(List.of("placement-native", "dimension-iris"));

        LootResolver.injectSources(sources, List.of("biome-iris"), IrisLootMode.REPLACE, false);

        assertEquals(List.of("biome-iris"), sources);
    }

    @Test
    public void entityLootBindingTargetsOnlyTheBaseLootInjection() {
        assertTrue(ModdedDeathLoot.hasBindingTag(Set.of("unrelated", "iris_loot|17|1|2|3|test")));
        assertFalse(ModdedDeathLoot.hasBindingTag(Set.of("unrelated", "iris_other")));
    }

    @Test
    public void fallbackDoesNotOverrideExistingNativeOrIrisSources() {
        List<String> sources = new ArrayList<>(List.of("placement-native", "dimension-iris"));

        LootResolver.injectSources(sources, List.of("fallback-iris"), IrisLootMode.FALLBACK, false);

        assertEquals(List.of("placement-native", "dimension-iris"), sources);
    }

    @Test
    public void fallbackAddsSourcesWhenPlacementIsEmpty() {
        List<String> sources = new ArrayList<>();

        LootResolver.injectSources(sources, List.of("fallback-iris"), IrisLootMode.FALLBACK, true);

        assertEquals(List.of("fallback-iris"), sources);
    }

    @Test
    public void zeroMultiplierRemovesNativeAndIrisSources() {
        List<String> sources = new ArrayList<>(List.of("placement-native", "dimension-iris"));

        LootResolver.scaleSources(sources, 0D, new RNG(17L));

        assertTrue(sources.isEmpty());
    }

    @Test
    public void unitMultiplierPreservesNativeAndIrisSources() {
        List<String> sources = new ArrayList<>(List.of("placement-native", "dimension-iris"));

        LootResolver.scaleSources(sources, 1D, new RNG(17L));

        assertEquals(List.of("placement-native", "dimension-iris"), sources);
    }

    @Test
    public void doubleMultiplierScalesTheUnifiedSourceList() {
        List<String> sources = new ArrayList<>(List.of("placement-native", "dimension-iris"));

        LootResolver.scaleSources(sources, 2D, new RNG(17L));

        assertEquals(4, sources.size());
        assertEquals("placement-native", sources.get(0));
        assertEquals("dimension-iris", sources.get(1));
        Set<String> expectedSources = Set.of("placement-native", "dimension-iris");
        for (String source : sources) {
            assertTrue(expectedSources.contains(source));
        }
    }

    @Test
    public void nativeKeyIsReturnedWhenTheLiveRegistryContainsIt() {
        ResourceKey<LootTable> key = ModdedLootApplier.resolveNativeKey("minecraft:chests/simple_dungeon", candidate -> true);

        assertNotNull(key);
        assertEquals(Identifier.parse("minecraft:chests/simple_dungeon"), key.identifier());
    }

    @Test
    public void nativeKeyIsRejectedWhenTheLiveRegistryDoesNotContainIt() {
        ResourceKey<LootTable> key = ModdedLootApplier.resolveNativeKey("minecraft:chests/missing", candidate -> false);

        assertNull(key);
    }

    @Test
    public void malformedNativeKeyIsRejectedBeforeRegistryLookup() {
        AtomicBoolean registryConsulted = new AtomicBoolean(false);

        ResourceKey<LootTable> key = ModdedLootApplier.resolveNativeKey("not a valid identifier", candidate -> {
            registryConsulted.set(true);
            return true;
        });

        assertNull(key);
        assertFalse(registryConsulted.get());
    }

    @Test
    public void nativeOnlyFillMarksTheContainerChanged() {
        TrackingContainer container = new TrackingContainer();

        ModdedLootApplier.fillContainer(container, List.<ItemStack>of(), new RNG(17L));

        assertTrue(container.changed);
    }

    @Test
    public void doubleChestHasExactlyOneCanonicalHalf() {
        BlockPos leftPos = new BlockPos(0, 64, 0);
        BlockPos rightPos = new BlockPos(1, 64, 0);

        assertTrue(ModdedLootApplier.isCanonicalPair(leftPos, rightPos));
        assertFalse(ModdedLootApplier.isCanonicalPair(rightPos, leftPos));
    }

    @Test
    public void nativeLootTableIsDetectedBeforeContainerAccess() {
        ResourceKey<LootTable> key = ResourceKey.create(
                Registries.LOOT_TABLE,
                Identifier.parse("minecraft:chests/simple_dungeon")
        );
        RandomizableContainer empty = containerWithLootTable(null);
        RandomizableContainer nativeLoot = containerWithLootTable(key);

        assertFalse(ModdedLootApplier.hasNativeLootTable(empty));
        assertTrue(ModdedLootApplier.hasNativeLootTable(nativeLoot));
    }

    private RandomizableContainer containerWithLootTable(ResourceKey<LootTable> key) {
        return (RandomizableContainer) Proxy.newProxyInstance(
                RandomizableContainer.class.getClassLoader(),
                new Class<?>[]{RandomizableContainer.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("getLootTable")) {
                        return key;
                    }
                    Class<?> returnType = method.getReturnType();
                    if (returnType == boolean.class) {
                        return false;
                    }
                    if (returnType == int.class) {
                        return 0;
                    }
                    if (returnType == long.class) {
                        return 0L;
                    }
                    return null;
                }
        );
    }

    private static final class TrackingContainer extends SimpleContainer {
        private boolean changed;

        private TrackingContainer() {
            super(0);
        }

        @Override
        public void setChanged() {
            changed = true;
            super.setChanged();
        }
    }
}
