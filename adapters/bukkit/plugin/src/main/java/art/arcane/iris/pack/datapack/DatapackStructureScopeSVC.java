package art.arcane.iris.pack.datapack;

import art.arcane.iris.world.IrisStartupValidation;
import art.arcane.iris.platform.bukkit.nms.DatapackStructureScopeResult;
import art.arcane.iris.platform.bukkit.nms.INMS;
import art.arcane.iris.world.IrisToolbelt;
import art.arcane.iris.platform.generation.BukkitChunkGenerator;
import art.arcane.iris.platform.generation.PlatformChunkGenerator;
import art.arcane.iris.structure.nativegen.IrisImportedStructureControl;
import art.arcane.iris.spi.IrisLogging;
import art.arcane.iris.platform.bukkit.plugin.IrisService;
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
                IrisImportedStructureControl importedStructures = importedStructures(world);
                if (!scopeIndex.isEmpty() || importedStructures != null && importedStructures.hasFrequencyOverrides()) {
                    applyScope(world, importedStructures);
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
        IrisImportedStructureControl importedStructures = importedStructures(event.getWorld());
        if (shouldApplyScope(scopeIndex.isEmpty(), studioEntryBootstrap,
                importedStructures != null && importedStructures.hasFrequencyOverrides())) {
            applyScope(event.getWorld(), importedStructures);
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

    private void applyScope(World world, IrisImportedStructureControl importedStructures) {
        Set<String> declaredSources = declaredSources(world);
        IrisImportedStructureControl activeControl = importedStructures == null
                ? new IrisImportedStructureControl()
                : importedStructures;
        try {
            DatapackStructureScopeResult result = INMS.get().scopeDatapackStructures(
                    world, scopeIndex, declaredSources, activeControl);
            IrisLogging.info("Scoped Iris-managed datapack structure sets for world '"
                    + world.getName() + "': " + result.retainedManagedSets() + " retained, "
                    + result.excludedManagedSets() + " excluded.");
        } catch (Throwable error) {
            throw new IllegalStateException("Could not scope Iris-managed datapack structure sets for world '"
                    + world.getName() + "'", error);
        }
    }

    private IrisImportedStructureControl importedStructures(World world) {
        PlatformChunkGenerator generator = IrisToolbelt.access(world);
        if (generator == null || generator.getTarget() == null
                || generator.getTarget().getDimension() == null) {
            return null;
        }
        return generator.getTarget().getDimension().getImportedStructures();
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
