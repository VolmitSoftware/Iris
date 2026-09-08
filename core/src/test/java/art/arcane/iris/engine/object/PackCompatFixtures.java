package art.arcane.iris.engine.object;

import art.arcane.iris.core.compat.CompatAction;
import art.arcane.iris.core.compat.CompatFinding;
import art.arcane.iris.core.compat.CompatRegistry;
import art.arcane.iris.core.compat.CompatStatus;
import art.arcane.iris.core.compat.PackCompatReport;
import art.arcane.iris.core.loader.IrisData;
import art.arcane.iris.core.loader.IrisRegistrant;

import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Shared fixtures for the version-content pool cascade tests. The gate walker (lane L1) is not exercised here: the
 * tests stamp {@code compat} on a registrant directly, which is exactly what the walker stores.
 */
final class CompatFixtures {
    static final String MISSING_BLOCK = "minecraft:sulfur";
    static final String MISSING_ENTITY = "minecraft:camel";

    private CompatFixtures() {
    }

    static IrisData data(PackCompatReport report) {
        IrisData data = mock(IrisData.class);
        when(data.getCompatReport()).thenReturn(report);
        return data;
    }

    /** Marks a registrant excluded the way the gate walker would for a missing composed block. */
    static <T extends IrisRegistrant> T excludeBlock(T registrant) {
        return exclude(registrant, CompatRegistry.BLOCK, MISSING_BLOCK);
    }

    /** Marks a registrant excluded the way the gate walker would for a missing entity type. */
    static <T extends IrisRegistrant> T excludeEntity(T registrant) {
        return exclude(registrant, CompatRegistry.ENTITY, MISSING_ENTITY);
    }

    static <T extends IrisRegistrant> T exclude(T registrant, CompatRegistry registry, String key) {
        registrant.setCompat(CompatStatus.excludedBy(List.of(new CompatFinding(registry, key,
                CompatAction.EXCLUDED, registrant.getTypeName().toLowerCase(java.util.Locale.ROOT),
                registrant.getLoadKey(), "composition"))));
        return registrant;
    }

    static IrisBiome biome(String key) {
        IrisBiome biome = new IrisBiome();
        biome.setLoadKey(key);
        return biome;
    }

    static IrisRegion region(String key) {
        IrisRegion region = new IrisRegion();
        region.setLoadKey(key);
        return region;
    }

    static IrisEntity entity(String key) {
        IrisEntity entity = new IrisEntity();
        entity.setLoadKey(key);
        return entity;
    }

    static IrisLootTable lootTable(String key) {
        IrisLootTable table = new IrisLootTable();
        table.setLoadKey(key);
        return table;
    }

    static CompatFinding find(PackCompatReport report, CompatAction action, String subjectType, String subjectKey) {
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
