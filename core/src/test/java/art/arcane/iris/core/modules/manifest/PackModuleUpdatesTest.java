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

package art.arcane.iris.core.modules.manifest;

import art.arcane.iris.core.modules.BuiltInModuleJars;
import art.arcane.iris.core.modules.manifest.ModuleUpdateReport.ModuleChange;
import art.arcane.iris.core.modules.manifest.ModuleUpdateReport.Outcome;
import art.arcane.iris.core.modules.manifest.PackModuleUpdates.Policy;
import art.arcane.iris.core.modules.manifest.ShippedModules.ShippedJar;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** A pack's jars take patches on their own and everything else when the update command asks. */
public class PackModuleUpdatesTest {
    private static final String TERRAIN = "iris:terrain";
    private static final String CARVE = "iris:carve";
    private static final String MATTER = "iris:matter";
    private static final String STRUCTURE = "iris:structure";
    private static final String LOOT = "iris:loot";
    private static final String SPAWN = "iris:spawn";

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    @Test
    public void aNewPackGetsEveryShippedModuleAndAManifestThatListsThem() throws IOException {
        Path pack = folder.newFolder("new").toPath();
        ShippedModules shipped = BuiltInModuleJars.shipped();

        PackModuleUpdates.installAllInto(pack, shipped);

        PackModuleManifest manifest = PackModuleManifest.read(pack).orElseThrow();
        assertEquals(shipped.jars().stream().flatMap(jar -> jar.modules().stream()).count(), manifest.modules().size());
        assertEquals(shipped.module(TERRAIN).orElseThrow().version(), manifest.modules().get(TERRAIN));
        assertEquals(shipped.jars().stream().map(ShippedJar::file).sorted().toList(), jarsOf(pack));
    }

    @Test
    public void onlyTheJarsOfListedModulesAreInstalled() throws IOException {
        Path pack = packListing(Map.of(MATTER, "1.0.0", "acme:rivers", "0.9.0"), Set.of());

        ModuleUpdateReport report = PackModuleUpdates.bringUpToDate(pack, BuiltInModuleJars.shipped(), Policy.PATCHES_ONLY);

        assertEquals(List.of("iris-matter.jar"), jarsOf(pack));
        assertEquals(Outcome.INSTALLED, outcomeOf(report, MATTER));
        assertEquals(1, report.changes().size());
    }

    @Test
    public void aPackWithoutAManifestIsLeftAlone() throws IOException {
        Path pack = folder.newFolder("unmanaged").toPath();

        ModuleUpdateReport report = PackModuleUpdates.bringUpToDate(pack, BuiltInModuleJars.shipped(), Policy.PATCHES_ONLY);

        assertFalse(report.managed());
        assertFalse(Files.exists(pack.resolve("modules")));
    }

    @Test
    public void aPackWhoseJarsAreTheShippedOnesIsNotWrittenTo() throws IOException {
        Path pack = installedPack("current");

        ModuleUpdateReport report = PackModuleUpdates.bringUpToDate(pack, BuiltInModuleJars.shipped(), Policy.PATCHES_ONLY);

        assertTrue(report.written().isEmpty());
        assertTrue(report.kept().isEmpty());
    }

    @Test
    public void aPatchIsTakenWithoutAsking() throws IOException {
        Path pack = installedPack("pack");
        ShippedModules next = release(Map.of(TERRAIN, "1.0.1", MATTER, "1.0.7"));

        ModuleUpdateReport report = PackModuleUpdates.bringUpToDate(pack, next, Policy.PATCHES_ONLY);

        assertEquals(Outcome.UPDATED, outcomeOf(report, TERRAIN));
        assertEquals(Outcome.UPDATED, outcomeOf(report, MATTER));
        assertEquals("iris:terrain 1.0.0 -> 1.0.1", changeOf(report, TERRAIN).describe());
        assertTrue(report.kept().isEmpty());
        assertHoldsShippedJar(pack, next, TERRAIN);
    }

    @Test
    public void aNewMinorOrMajorVersionIsReportedOutOfDateAndKept() throws IOException {
        Path pack = installedPack("pack");
        ShippedModules installed = BuiltInModuleJars.shipped();
        ShippedModules next = release(Map.of(TERRAIN, "2.0.0", MATTER, "1.1.0"));

        ModuleUpdateReport report = PackModuleUpdates.bringUpToDate(pack, next, Policy.PATCHES_ONLY);

        assertEquals(Outcome.KEPT_OUT_OF_DATE, outcomeOf(report, TERRAIN));
        assertEquals(Outcome.KEPT_OUT_OF_DATE, outcomeOf(report, MATTER));
        assertTrue(report.keptOutOfDate());
        assertEquals("iris:terrain 1.0.0 is out of date, 2.0.0 is available", changeOf(report, TERRAIN).describe());
        assertEquals("iris:matter 1.0.0 is out of date, 1.1.0 is available", changeOf(report, MATTER).describe());
        assertHoldsShippedJar(pack, installed, TERRAIN);
        assertHoldsShippedJar(pack, installed, MATTER);
    }

    @Test
    public void aPatchThatUsesCodeOfAKeptModuleIsKeptWithIt() throws IOException {
        Path pack = installedPack("pack");
        ShippedModules installed = BuiltInModuleJars.shipped();
        ShippedModules next = release(Map.of(TERRAIN, "2.0.0", CARVE, "1.0.1", MATTER, "1.0.1"));

        ModuleUpdateReport report = PackModuleUpdates.bringUpToDate(pack, next, Policy.PATCHES_ONLY);

        assertEquals(Outcome.KEPT_OUT_OF_DATE, outcomeOf(report, TERRAIN));
        assertEquals(Outcome.KEPT_SHARED_CODE, outcomeOf(report, CARVE));
        assertEquals(Optional.of(TERRAIN), changeOf(report, CARVE).keptWith());
        assertEquals(Outcome.UPDATED, outcomeOf(report, MATTER));
        assertEquals("iris:carve 1.0.1 uses code of iris:terrain, which was kept; kept 1.0.0", changeOf(report, CARVE).describe());
        assertHoldsShippedJar(pack, installed, TERRAIN);
        assertHoldsShippedJar(pack, installed, CARVE);
        assertHoldsShippedJar(pack, next, MATTER);
    }

    @Test
    public void aKeptModuleHoldsBackTheModulesThatReachItsCodeThroughOthers() throws IOException {
        Path pack = installedPack("pack");
        ShippedModules next = release(Map.of(STRUCTURE, "2.0.0", LOOT, "1.0.1", SPAWN, "1.0.1", MATTER, "1.0.1"));

        ModuleUpdateReport report = PackModuleUpdates.bringUpToDate(pack, next, Policy.PATCHES_ONLY);

        assertEquals(Outcome.KEPT_OUT_OF_DATE, outcomeOf(report, STRUCTURE));
        assertEquals(Outcome.KEPT_SHARED_CODE, outcomeOf(report, LOOT));
        assertEquals(Outcome.KEPT_SHARED_CODE, outcomeOf(report, SPAWN));
        assertEquals(Optional.of(STRUCTURE), changeOf(report, SPAWN).keptWith());
        assertEquals(Outcome.UPDATED, outcomeOf(report, MATTER));
    }

    @Test
    public void theUpdateCommandTakesWhatWasKeptOutOfDate() throws IOException {
        Path pack = installedPack("pack");
        ShippedModules next = release(Map.of(TERRAIN, "2.0.0", CARVE, "1.0.1", MATTER, "1.1.0"));
        PackModuleUpdates.bringUpToDate(pack, next, Policy.PATCHES_ONLY);

        ModuleUpdateReport report = PackModuleUpdates.bringUpToDate(pack, next, Policy.TAKING_EVERY_UPDATE);

        assertEquals(Outcome.UPDATED, outcomeOf(report, TERRAIN));
        assertEquals(Outcome.UPDATED, outcomeOf(report, CARVE));
        assertEquals(Outcome.UPDATED, outcomeOf(report, MATTER));
        assertEquals("iris:terrain 1.0.0 -> 2.0.0", changeOf(report, TERRAIN).describe());
        assertHoldsShippedJar(pack, next, TERRAIN);
        assertEquals("2.0.0", PackModuleLock.read(pack).installed().get(TERRAIN));
    }

    @Test
    public void aPinnedModuleStaysEvenWhenTheUpdateCommandRuns() throws IOException {
        Path pack = installedPack("pinned");
        PackModuleManifest listed = PackModuleManifest.read(pack).orElseThrow();
        new PackModuleManifest(listed.modules(), Set.of(MATTER)).write(pack);
        ShippedModules next = release(Map.of(MATTER, "1.0.1"));

        for (Policy policy : Policy.values()) {
            ModuleUpdateReport report = PackModuleUpdates.bringUpToDate(pack, next, policy);

            assertEquals(policy.name(), Outcome.KEPT_PINNED, outcomeOf(report, MATTER));
            assertEquals("iris:matter 1.0.1 is available, kept 1.0.0 (pinned in modules.json)", changeOf(report, MATTER).describe());
        }
        assertHoldsShippedJar(pack, BuiltInModuleJars.shipped(), MATTER);
    }

    @Test
    public void aModuleNewerThanIrisShipsIsNotDowngraded() throws IOException {
        Path pack = folder.newFolder("ahead").toPath();
        ShippedModules newer = release(Map.of(MATTER, "1.2.0"));
        PackModuleUpdates.installAllInto(pack, newer);
        recordInstalled(pack, newer);

        ModuleUpdateReport report = PackModuleUpdates.bringUpToDate(pack, BuiltInModuleJars.shipped(), Policy.PATCHES_ONLY);

        assertEquals(Outcome.KEPT_NEWER_THAN_SHIPPED, outcomeOf(report, MATTER));
        assertEquals("iris:matter 1.2.0 is newer than the 1.0.0 this Iris ships, kept", changeOf(report, MATTER).describe());
        assertEquals(List.of(MATTER), report.needNewerIris().stream().map(ModuleChange::moduleId).toList());
        assertHoldsShippedJar(pack, newer, MATTER);
    }

    @Test
    public void aRebuildOfTheSameVersionReplacesTheJar() throws IOException {
        Path world = installedPack("world");
        ShippedModules rebuilt = ShippedRelease.rebuilt(folder.newFolder().toPath(), MATTER);

        ModuleUpdateReport report = PackModuleUpdates.bringUpToDate(world, rebuilt, Policy.PATCHES_ONLY);

        assertEquals(Outcome.UPDATED, outcomeOf(report, MATTER));
        assertEquals("iris:matter 1.0.0 replaced by this Iris's build of it", changeOf(report, MATTER).describe());
        assertHoldsShippedJar(world, rebuilt, MATTER);
    }

    @Test
    public void aPackWithoutARecordOfAModulesVersionKeepsItsJar() throws IOException {
        Path world = installedPack("world");
        Files.delete(world.resolve(PackModuleLock.FILE_NAME));
        ShippedModules next = release(Map.of(MATTER, "1.0.1"));

        ModuleUpdateReport report = PackModuleUpdates.bringUpToDate(world, next, Policy.PATCHES_ONLY);

        assertEquals(Outcome.KEPT_OUT_OF_DATE, outcomeOf(report, MATTER));
    }

    @Test
    public void aJarOfAModuleIrisDoesNotShipIsNeverTouched() throws IOException {
        Path pack = installedPack("third-party");
        Path thirdParty = pack.resolve("modules").resolve("acme-rivers.jar");
        Files.writeString(thirdParty, "not Iris's to judge", StandardCharsets.UTF_8);

        PackModuleUpdates.bringUpToDate(pack, release(Map.of(MATTER, "1.1.0")), Policy.TAKING_EVERY_UPDATE);

        assertEquals("not Iris's to judge", Files.readString(thirdParty, StandardCharsets.UTF_8));
    }

    /** A pack with every module of the real release installed and recorded, as a pack that loaded once has. */
    private Path installedPack(String name) throws IOException {
        Path pack = folder.newFolder(name).toPath();
        PackModuleUpdates.installAllInto(pack, BuiltInModuleJars.shipped());
        recordInstalled(pack, BuiltInModuleJars.shipped());
        return pack;
    }

    private Path packListing(Map<String, String> modules, Set<String> pinned) throws IOException {
        Path pack = folder.newFolder().toPath();
        new PackModuleManifest(modules, pinned).write(pack);
        return pack;
    }

    private ShippedModules release(Map<String, String> newVersions) throws IOException {
        return ShippedRelease.withVersions(folder.newFolder().toPath(), newVersions);
    }

    private static void recordInstalled(Path pack, ShippedModules release) throws IOException {
        Map<String, String> versions = new LinkedHashMap<>();
        release.jars().forEach(jar -> jar.modules().forEach(module -> versions.put(module.id(), module.version())));
        new PackModuleLock(versions).writeIfChanged(pack);
    }

    private static List<String> jarsOf(Path pack) throws IOException {
        try (Stream<Path> files = Files.list(pack.resolve("modules"))) {
            return files.map(file -> file.getFileName().toString()).sorted().toList();
        }
    }

    private static void assertHoldsShippedJar(Path pack, ShippedModules release, String moduleId) throws IOException {
        ShippedJar jar = release.jars().stream().filter(candidate -> candidate.holds(moduleId)).findFirst().orElseThrow();
        assertEquals(moduleId, jar.sha256(), PackModuleUpdates.hashOf(Files.readAllBytes(pack.resolve("modules").resolve(jar.file()))));
    }

    private static Outcome outcomeOf(ModuleUpdateReport report, String moduleId) {
        return changeOf(report, moduleId).outcome();
    }

    private static ModuleChange changeOf(ModuleUpdateReport report, String moduleId) {
        return report.changes().stream().filter(change -> change.moduleId().equals(moduleId)).findFirst().orElseThrow();
    }
}
