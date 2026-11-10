package art.arcane.iris.core.service;

import art.arcane.iris.core.IrisStartupValidation;
import art.arcane.iris.core.datapack.DatapackIngestService;
import art.arcane.iris.core.datapack.DatapackStructureScopeIndex;
import art.arcane.iris.core.nms.DatapackStructureScopeResult;
import art.arcane.iris.core.nms.INMS;
import art.arcane.iris.core.tools.IrisToolbelt;
import art.arcane.iris.engine.platform.BukkitChunkGenerator;
import art.arcane.iris.engine.platform.PlatformChunkGenerator;
import art.arcane.iris.engine.framework.NativeStructureFrequencies;
import art.arcane.iris.spi.IrisLogging;
import art.arcane.iris.util.common.plugin.IrisService;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.world.WorldInitEvent;
import org.bukkit.event.world.WorldUnloadEvent;

import java.io.IOException;
import java.util.Set;

public final class DatapackStructureScopeSVC implements IrisService {
    private DatapackStructureScopeIndex scopeIndex = DatapackStructureScopeIndex.create(null);

    @Override
    public void onEnable() {
        try {
            scopeIndex = DatapackStructureScopeIndex.create(
                    DatapackIngestService.installedStructureScopeResources());
        } catch (IOException e) {
            // A missing manifest entry is the condition validateOnStartup already classified as
            // non-fatal; degrade to an empty scope and lock world creation instead of aborting
            // the whole plugin bootstrap (which would skip listeners and journal reconciliation).
            scopeIndex = DatapackStructureScopeIndex.create(null);
            IrisLogging.error("Could not establish ownership for installed datapack structure sets; "
                    + "datapack structure scoping is disabled until the manifest is repaired: " + e.getMessage());
            IrisStartupValidation.markDatapacksInvalid(
                    "Iris could not establish ownership for installed datapack structure sets: " + e.getMessage());
        }
        for (World world : Bukkit.getWorlds()) {
            try {
                NativeStructureFrequencies frequencies = frequencies(world);
                if (!scopeIndex.isEmpty() || frequencies.hasOverrides()) {
                    applyScope(world, frequencies);
                }
            } catch (Throwable e) {
                // Per-world containment: one world's scoping failure must not abort the
                // service enable (and with it the whole bootstrap's containment contract).
                IrisLogging.error("Could not scope datapack structures for world '" + world.getName() + "'.");
                IrisLogging.reportError(e);
            }
        }
    }

    @Override
    public void onDisable() {
        for (World world : Bukkit.getWorlds()) {
            INMS.get().abandonStudioStructureBootstrap(world);
        }
        scopeIndex = DatapackStructureScopeIndex.create(null);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onWorldInit(WorldInitEvent event) {
        boolean studioEntryBootstrap = event.getWorld().getGenerator()
                instanceof BukkitChunkGenerator generator
                && generator.isStudioEntryBootstrapActive();
        NativeStructureFrequencies frequencies = frequencies(event.getWorld());
        if (shouldApplyScope(scopeIndex.isEmpty(), studioEntryBootstrap, frequencies.hasOverrides())) {
            applyScope(event.getWorld(), frequencies);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onWorldUnload(WorldUnloadEvent event) {
        INMS.get().abandonStudioStructureBootstrap(event.getWorld());
    }

    static boolean shouldApplyScope(boolean scopeIndexEmpty, boolean studioEntryBootstrap,
                                    boolean hasFrequencyOverrides) {
        return !scopeIndexEmpty || studioEntryBootstrap || hasFrequencyOverrides;
    }

    private void applyScope(World world, NativeStructureFrequencies frequencies) {
        Set<String> declaredSources = declaredSources(world);
        try {
            DatapackStructureScopeResult result = INMS.get().scopeDatapackStructures(
                    world, scopeIndex, declaredSources, frequencies);
            IrisLogging.info("Scoped Iris-managed datapack structure sets for world '"
                    + world.getName() + "': " + result.retainedManagedSets() + " retained, "
                    + result.excludedManagedSets() + " excluded.");
        } catch (Throwable error) {
            throw new IllegalStateException("Could not scope Iris-managed datapack structure sets for world '"
                    + world.getName() + "'", error);
        }
    }

    private NativeStructureFrequencies frequencies(World world) {
        PlatformChunkGenerator generator = IrisToolbelt.access(world);
        if (generator == null || generator.getTarget() == null
                || generator.getTarget().getDimension() == null) {
            return NativeStructureFrequencies.NONE;
        }
        return NativeStructureFrequencies.of(generator.getTarget().getData(), generator.getTarget().getDimension());
    }

    private Set<String> declaredSources(World world) {
        PlatformChunkGenerator generator = IrisToolbelt.access(world);
        if (generator == null) {
            return Set.of();
        }
        return scopeIndex.declaredSources(
                DatapackIngestService.configuredImports(generator.getTarget().getDimension()));
    }
}
