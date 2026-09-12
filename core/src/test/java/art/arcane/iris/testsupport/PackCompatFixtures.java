package art.arcane.iris.testsupport;

import art.arcane.iris.generation.biome.IrisBiome;
import art.arcane.iris.generation.terrain.IrisRegion;
import art.arcane.iris.world.entity.IrisEntity;
import art.arcane.iris.world.loot.IrisLootTable;

import art.arcane.iris.pack.validation.CompatAction;
import art.arcane.iris.pack.validation.CompatFinding;
import art.arcane.iris.pack.validation.CompatRegistry;
import art.arcane.iris.pack.validation.CompatStatus;
import art.arcane.iris.pack.validation.PackCompatReport;
import art.arcane.iris.pack.loading.IrisData;
import art.arcane.iris.pack.loading.IrisRegistrant;

import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Shared fixtures for the version-content pool cascade tests. The gate walker (lane L1) is not exercised here: the
 * tests stamp {@code compat} on a registrant directly, which is exactly what the walker stores.
 */
public final class PackCompatFixtures {
    public static final String MISSING_BLOCK = "minecraft:sulfur";
    public static final String MISSING_ENTITY = "minecraft:camel";

    private PackCompatFixtures() {
    }

    public static IrisData data(PackCompatReport report) {
        IrisData data = mock(IrisData.class);
        when(data.getCompatReport()).thenReturn(report);
        return data;
    }

    /** Marks a registrant excluded the way the gate walker would for a missing composed block. */
    public static <T extends IrisRegistrant> T excludeBlock(T registrant) {
        return exclude(registrant, CompatRegistry.BLOCK, MISSING_BLOCK);
    }

    /** Marks a registrant excluded the way the gate walker would for a missing entity type. */
    public static <T extends IrisRegistrant> T excludeEntity(T registrant) {
        return exclude(registrant, CompatRegistry.ENTITY, MISSING_ENTITY);
    }

    public static <T extends IrisRegistrant> T exclude(T registrant, CompatRegistry registry, String key) {
        registrant.setCompat(CompatStatus.excludedBy(List.of(new CompatFinding(registry, key,
                CompatAction.EXCLUDED, registrant.getTypeName().toLowerCase(java.util.Locale.ROOT),
                registrant.getLoadKey(), "composition"))));
        return registrant;
    }

    public static IrisBiome biome(String key) {
        IrisBiome biome = new IrisBiome();
        biome.setLoadKey(key);
        return biome;
    }

    public static IrisRegion region(String key) {
        IrisRegion region = new IrisRegion();
        region.setLoadKey(key);
        return region;
    }

    public static IrisEntity entity(String key) {
        IrisEntity entity = new IrisEntity();
        entity.setLoadKey(key);
        return entity;
    }

    public static IrisLootTable lootTable(String key) {
        IrisLootTable table = new IrisLootTable();
        table.setLoadKey(key);
        return table;
    }

    public static CompatFinding find(PackCompatReport report, CompatAction action, String subjectType, String subjectKey) {
        for (CompatFinding finding : report.findings()) {
            if (finding.action() == action
                    && finding.subjectType().equals(subjectType)
                    && finding.subjectKey().equals(subjectKey)) {
                return finding;
            }
        }
        return null;
    }
}
