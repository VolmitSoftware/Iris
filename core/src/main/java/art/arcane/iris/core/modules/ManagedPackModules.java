/*
 * Iris is a World Generator for Minecraft Servers
 * Copyright (c) 2026 Arcane Arts (Volmit Software)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package art.arcane.iris.core.modules;

import art.arcane.iris.core.PackUse;
import art.arcane.iris.core.modules.manifest.ModuleUpdateReport;
import art.arcane.iris.core.modules.manifest.PackModuleLock;
import art.arcane.iris.core.modules.manifest.PackModuleManifest;
import art.arcane.iris.core.modules.manifest.PackModuleUpdates;
import art.arcane.iris.core.modules.manifest.PackModuleUpdates.Policy;
import art.arcane.iris.core.modules.manifest.ShippedModules;
import art.arcane.iris.spi.IrisLogging;
import com.volmit.host.DiscoveredModule;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Keeps the module jars of one pack folder in step with its {@code modules.json} and with the jars Iris ships
 * ({@link PackModuleUpdates}), logs the jars it wrote, and records what the pack loaded in {@code modules.lock.json}. What
 * the pack kept back goes to its author when they open a studio ({@link KeptModuleUpdates#keptAtLoad}), not to the log.
 */
final class ManagedPackModules {
    private static final String UNMANAGED_KEY = "pack-without-modules-manifest:";
    private static final String NEEDS_NEWER_IRIS_KEY = "pack-needs-newer-iris:";

    private final File packFolder;
    private final ShippedModules shipped;

    ManagedPackModules(File packFolder, ShippedModules shipped) {
        this.packFolder = packFolder;
        this.shipped = shipped;
    }

    /**
     * Installs the pack's missing jars and takes patches before the jars load. A module with a new minor or major
     * version stays as it is and is reported out of date.
     *
     * @throws PackModuleManifest.ManifestFormatException when {@code modules.json} is not a manifest
     */
    ModuleUpdateReport bringUpToDate() {
        return bringUpToDate(Policy.PATCHES_ONLY);
    }

    /** Takes every update Iris ships, new minor and major versions included; the update command runs it. */
    ModuleUpdateReport takeEveryUpdate() {
        return bringUpToDate(Policy.TAKING_EVERY_UPDATE);
    }

    /** Writes the versions the pack loaded, and warns about modules the manifest lists that no jar provides. */
    void recordLoaded(List<DiscoveredModule> loaded) {
        Map<String, String> versions = new LinkedHashMap<>();
        loaded.stream().map(DiscoveredModule::info).forEach(info -> versions.put(info.id().toString(), info.version()));
        try {
            Optional<PackModuleManifest> manifest = PackModuleManifest.read(packFolder.toPath());
            if (manifest.isEmpty()) {
                return;
            }
            new PackModuleLock(versions).writeIfChanged(packFolder.toPath());
            warnAboutListedModulesWithoutJar(manifest.get(), versions.keySet());
        } catch (IOException | PackModuleManifest.ManifestFormatException failure) {
            IrisLogging.warn("%s: cannot record its installed modules in %s: %s", label(), PackModuleLock.FILE_NAME, failure.getMessage());
        }
    }

    /** "World survival" for a world's copy of a pack, "Pack overworld" for a pack folder. */
    String label() {
        return worldFolder().map(world -> "World " + world.getFileName()).orElse("Pack " + packFolder.getName());
    }

    private ModuleUpdateReport bringUpToDate(Policy policy) {
        try {
            ModuleUpdateReport report = PackModuleUpdates.bringUpToDate(packFolder.toPath(), shipped, policy);
            log(report);
            return report;
        } catch (IOException failure) {
            IrisLogging.error("%s: cannot update its modules, so it loads the jars it has: %s", label(), failure.toString());
            return new ModuleUpdateReport(true, List.of(), List.of());
        }
    }

    private void log(ModuleUpdateReport report) {
        if (!report.managed()) {
            warnAboutMissingManifest();
            return;
        }
        report.written().forEach(change -> IrisLogging.info("%s: %s", label(), change.describe()));
        report.needNewerIris().forEach(change -> warnOncePerStart(change.describeNeedForNewerIris()));
    }

    /** A server loads a world's pack several times while it starts; what the pack kept is the same each time. */
    private void warnOncePerStart(String warning) {
        IrisLogging.warnOnce(NEEDS_NEWER_IRIS_KEY + packFolder.getAbsolutePath() + ":" + warning, "%s: %s", label(), warning);
    }

    private void warnAboutMissingManifest() {
        IrisLogging.warnOnce(UNMANAGED_KEY + packFolder.getAbsolutePath(), "%s has no %s, so Iris installs and updates no modules"
                + " for it. A pack without modules generates only its layout (biomes and heights). /iris std create writes the file"
                + " for a new pack; for this one, add %s with the modules it uses%s", label(), PackModuleManifest.FILE_NAME,
                PackModuleManifest.FILE_NAME, exampleManifest());
    }

    private String exampleManifest() {
        if (shipped.isEmpty()) {
            return ".";
        }
        String listed = shipped.jars().stream()
                .flatMap(jar -> jar.modules().stream())
                .map(module -> "\"" + module.id() + "\": \"" + module.version() + "\"")
                .collect(Collectors.joining(", "));
        return ": {\"modules\": {" + listed + "}}";
    }

    private void warnAboutListedModulesWithoutJar(PackModuleManifest manifest, Set<String> loadedIds) {
        List<String> missing = manifest.modules().keySet().stream().filter(id -> !loadedIds.contains(id)).toList();
        if (missing.isEmpty()) {
            return;
        }
        IrisLogging.warn("%s: %s lists %s, but no jar in its %s folder provides %s. Iris installs only the modules it ships; put"
                + " the jar of any other module there yourself.", label(), PackModuleManifest.FILE_NAME, String.join(", ", missing),
                PackModuleSet.MODULES_FOLDER, missing.size() == 1 ? "it" : "them");
    }

    private Optional<Path> worldFolder() {
        return PackUse.worldFolderHolding(packFolder.toPath());
    }
}
