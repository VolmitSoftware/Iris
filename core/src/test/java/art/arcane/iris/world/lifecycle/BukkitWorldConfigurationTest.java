package art.arcane.iris.world.lifecycle;

import art.arcane.iris.generation.runtime.IrisEngineMantle;
import art.arcane.iris.world.WorldSlotKey;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;
import java.net.StandardProtocolFamily;
import java.net.UnixDomainSocketAddress;
import java.nio.channels.ServerSocketChannel;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeNoException;
import static org.junit.Assume.assumeTrue;

public class BukkitWorldConfigurationTest {
    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void readsOnlyCanonicalCustomIrisGeneratorBindings() throws Exception {
        File configuration = temporaryFolder.newFile("binding-bukkit.yml");
        Path levelRoot = temporaryFolder.newFolder("binding-level-root").toPath();
        Files.createDirectories(levelRoot.resolve("dimensions/iris/moon"));
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("worlds.world.generator", "Iris:overworld");
        yaml.set("worlds.world_nether.generator", "Iris:underworld");
        yaml.set("worlds.world_the_end.generator", "Iris:theend");
        yaml.set("worlds.moon.generator", "Iris:noncanonical_short_name");
        yaml.set("worlds.world_iris_moon.generator", "Iris:moon_pack");
        yaml.set("worlds.world_iris_other.generator", "Other:generator");
        yaml.save(configuration);

        List<BukkitWorldConfiguration.IrisGeneratorBinding> bindings =
                BukkitWorldConfiguration.readIrisGeneratorBindings(configuration, "world", levelRoot);

        assertEquals(List.of(new BukkitWorldConfiguration.IrisGeneratorBinding(
                "world_iris_moon",
                new WorldSlotKey("iris", "moon"),
                "moon_pack"
        )), bindings);
    }

    @Test
    public void rejectsCustomIrisBindingWithoutSelectedDimension() throws Exception {
        File configuration = temporaryFolder.newFile("missing-binding-dimension.yml");
        Path levelRoot = temporaryFolder.newFolder("missing-binding-level-root").toPath();
        Files.createDirectories(levelRoot.resolve("dimensions/iris/moon"));
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("worlds.world_iris_moon.generator", "Iris");
        yaml.save(configuration);

        IOException failure = assertThrows(
                IOException.class,
                () -> BukkitWorldConfiguration.readIrisGeneratorBindings(configuration, "world", levelRoot)
        );

        assertTrue(failure.getMessage().contains("must select a dimension"));
    }

    @Test
    public void ignoresNoncanonicalShortNamesThatCollapseOntoCanonicalIrisKeys() throws Exception {
        File configuration = temporaryFolder.newFile("ambiguous-binding-name.yml");
        Path levelRoot = temporaryFolder.newFolder("ambiguous-binding-level-root").toPath();
        Files.createDirectories(levelRoot.resolve("dimensions/iris/moon"));
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("worlds.moon.generator", "Iris:moon_pack");
        yaml.set("worlds.world_iris_moon.generator", "Iris:moon_pack");
        yaml.save(configuration);

        assertEquals(List.of(new BukkitWorldConfiguration.IrisGeneratorBinding(
                "world_iris_moon",
                new WorldSlotKey("iris", "moon"),
                "moon_pack"
        )), BukkitWorldConfiguration.readIrisGeneratorBindings(configuration, "world", levelRoot));
    }

    @Test
    public void admitsOnlyExistingCustomWorldDirectoriesUnderSelectedLevelRoot() throws Exception {
        File configuration = temporaryFolder.newFile("root-admission-bukkit.yml");
        Path selectedRoot = temporaryFolder.newFolder("selected-level-root").toPath();
        Path otherRoot = temporaryFolder.newFolder("other-level-root").toPath();
        Files.createDirectories(selectedRoot.resolve("dimensions/iris/moon"));
        Files.createDirectories(otherRoot.resolve("dimensions/iris/moon"));
        Files.createDirectories(selectedRoot.resolve("dimensions/iris"));
        Files.writeString(selectedRoot.resolve("dimensions/iris/file"), "not a world directory");
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("worlds.world_iris_moon.generator", "Iris:moon_pack");
        yaml.set("worlds.other_iris_moon.generator", "Iris:other_pack");
        yaml.set("worlds.world_iris_missing.generator", "Iris:missing_pack");
        yaml.set("worlds.world_iris_file.generator", "Iris:file_pack");
        yaml.save(configuration);

        List<BukkitWorldConfiguration.IrisGeneratorBinding> bindings =
                BukkitWorldConfiguration.readIrisGeneratorBindings(configuration, "world", selectedRoot);

        assertEquals(List.of(new BukkitWorldConfiguration.IrisGeneratorBinding(
                "world_iris_moon",
                new WorldSlotKey("iris", "moon"),
                "moon_pack"
        )), bindings);
    }

    @Test
    public void admitsCurrentCraftBukkitConfiguredWorldStorage() throws Exception {
        Path worldContainer = temporaryFolder.newFolder("configured-binding-server").toPath();
        File configuration = worldContainer.resolve("bukkit.yml").toFile();
        Path levelRoot = Files.createDirectory(worldContainer.resolve("world"));
        Files.createDirectories(worldContainer.resolve("world_iris_moon/dimensions/iris/moon"));
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("worlds.world_iris_moon.generator", "Iris:overworld");
        yaml.save(configuration);

        assertEquals(List.of(new BukkitWorldConfiguration.IrisGeneratorBinding(
                "world_iris_moon",
                new WorldSlotKey("iris", "moon"),
                "overworld"
        )), BukkitWorldConfiguration.readIrisGeneratorBindings(configuration, "world", levelRoot));
    }

    @Test
    public void excludesSymlinkedCustomWorldTarget() throws Exception {
        File configuration = temporaryFolder.newFile("symlink-admission-bukkit.yml");
        Path levelRoot = temporaryFolder.newFolder("symlink-level-root").toPath();
        Path namespaceRoot = Files.createDirectories(levelRoot.resolve("dimensions/iris"));
        Path outside = temporaryFolder.newFolder("symlink-world-outside").toPath();
        try {
            Files.createSymbolicLink(namespaceRoot.resolve("moon"), outside);
        } catch (IOException | UnsupportedOperationException | SecurityException exception) {
            assumeNoException(exception);
        }
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("worlds.world_iris_moon.generator", "Iris:moon_pack");
        yaml.save(configuration);

        assertTrue(BukkitWorldConfiguration.readIrisGeneratorBindings(
                configuration,
                "world",
                levelRoot
        ).isEmpty());
    }

    @Test
    public void auditSeparatesUsableStorageFromOrphansAndBrokenSnapshots() throws Exception {
        File configuration = temporaryFolder.newFile("audit-bukkit.yml");
        Path levelRoot = temporaryFolder.newFolder("audit-level-root").toPath();
        Files.createDirectories(levelRoot.resolve("dimensions/iris/healthy/iris/pack"));
        Files.createDirectories(levelRoot.resolve("dimensions/iris/broken/region"));
        Files.writeString(levelRoot.resolve("dimensions/iris/broken/region/r.0.0.mca"), "terrain");
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("worlds.world_iris_healthy.generator", "Iris:overworld");
        yaml.set("worlds.world_iris_broken.generator", "Iris:overworld");
        yaml.set("worlds.world_iris_gone.generator", "Iris:overworld");
        yaml.save(configuration);

        List<BukkitWorldConfiguration.IrisWorldStorageEntry> audited =
                BukkitWorldConfiguration.auditIrisWorldStorage(configuration, "world", levelRoot);

        assertEquals(3, audited.size());
        assertEquals(BukkitWorldConfiguration.IrisWorldStorageState.UNUSABLE, stateOf(audited, "world_iris_broken"));
        assertEquals(BukkitWorldConfiguration.IrisWorldStorageState.MISSING, stateOf(audited, "world_iris_gone"));
        assertEquals(BukkitWorldConfiguration.IrisWorldStorageState.PRESENT, stateOf(audited, "world_iris_healthy"));
        assertEquals(
                levelRoot.resolve("dimensions/iris/broken").toAbsolutePath().normalize(),
                entryOf(audited, "world_iris_broken").storagePath());
        assertTrue(entryOf(audited, "world_iris_broken").detail().contains("generation history"));
    }

    @Test
    public void auditTreatsANonDirectoryStorageSlotAsAnOrphanTheServerNeverLoads() throws Exception {
        File configuration = temporaryFolder.newFile("audit-file-slot.yml");
        Path levelRoot = temporaryFolder.newFolder("audit-file-level-root").toPath();
        Files.createDirectories(levelRoot.resolve("dimensions/iris"));
        Files.writeString(levelRoot.resolve("dimensions/iris/file"), "not a world directory");
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("worlds.world_iris_file.generator", "Iris:overworld");
        yaml.save(configuration);

        assertEquals(
                BukkitWorldConfiguration.IrisWorldStorageState.MISSING,
                stateOf(BukkitWorldConfiguration.auditIrisWorldStorage(configuration, "world", levelRoot),
                        "world_iris_file"));
    }

    /**
     * Deleting a live world's folder and letting the server save it back leaves a five-file data/ skeleton
     * with no regions and no iris/ directory. Nothing in it can be overwritten, so it must not hold the
     * server hostage on the next boot.
     */
    @Test
    public void auditTreatsAServerRewrittenHuskAsAnOrphan() throws Exception {
        File configuration = temporaryFolder.newFile("audit-husk.yml");
        Path levelRoot = temporaryFolder.newFolder("audit-husk-level-root").toPath();
        Files.createDirectories(levelRoot.resolve("dimensions/iris/husk/data/paper"));
        Files.createDirectories(levelRoot.resolve("dimensions/iris/husk/data/minecraft"));
        Files.writeString(levelRoot.resolve("dimensions/iris/husk/data/paper/level_overrides.dat"), "x");
        Files.writeString(levelRoot.resolve("dimensions/iris/husk/data/minecraft/raids.dat"), "x");
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("worlds.world_iris_husk.generator", "Iris:overworld");
        yaml.save(configuration);

        List<BukkitWorldConfiguration.IrisWorldStorageEntry> audited =
                BukkitWorldConfiguration.auditIrisWorldStorage(configuration, "world", levelRoot);

        assertEquals(BukkitWorldConfiguration.IrisWorldStorageState.EMPTY, stateOf(audited, "world_iris_husk"));
        assertEquals(
                levelRoot.resolve("dimensions/iris/husk").toAbsolutePath().normalize(),
                entryOf(audited, "world_iris_husk").storagePath());
    }

    @Test
    public void auditFailsClosedForMantleDataWithoutAPackSnapshot() throws Exception {
        File configuration = temporaryFolder.newFile("audit-mantle.yml");
        Path levelRoot = temporaryFolder.newFolder("audit-mantle-level-root").toPath();
        Path mantleRoot = levelRoot.resolve("dimensions/iris/mantled")
                .resolve(IrisEngineMantle.STORAGE_FOLDER_NAME);
        Files.createDirectories(mantleRoot);
        Files.writeString(mantleRoot.resolve("0.0.ttp"), "mantle");
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("worlds.world_iris_mantled.generator", "Iris:overworld");
        yaml.save(configuration);

        assertEquals(
                BukkitWorldConfiguration.IrisWorldStorageState.UNUSABLE,
                stateOf(BukkitWorldConfiguration.auditIrisWorldStorage(configuration, "world", levelRoot),
                        "world_iris_mantled"));
    }

    /**
     * An iris/ directory without iris/pack is an Iris world whose pack snapshot broke, not an empty folder.
     */
    @Test
    public void auditFailsClosedForAnIrisMarkerWithoutAPackSnapshot() throws Exception {
        File configuration = temporaryFolder.newFile("audit-marker.yml");
        Path levelRoot = temporaryFolder.newFolder("audit-marker-level-root").toPath();
        Files.createDirectories(levelRoot.resolve("dimensions/iris/marked/iris/engine-data"));
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("worlds.world_iris_marked.generator", "Iris:overworld");
        yaml.save(configuration);

        assertEquals(
                BukkitWorldConfiguration.IrisWorldStorageState.UNUSABLE,
                stateOf(BukkitWorldConfiguration.auditIrisWorldStorage(configuration, "world", levelRoot),
                        "world_iris_marked"));
    }

    /**
     * IrisWorldStorage refuses to resolve through a symbolic link, so a linked dimension path is storage Iris
     * cannot use - not storage that is not there. Reporting it as a cold orphan tells the operator to restore
     * a folder that is already present.
     */
    @Test
    public void auditFailsClosedForASymlinkedDimensionPath() throws Exception {
        File configuration = temporaryFolder.newFile("audit-symlink.yml");
        Path levelRoot = temporaryFolder.newFolder("audit-symlink-level-root").toPath();
        Files.createDirectories(levelRoot.resolve("dimensions/iris"));
        Path elsewhere = temporaryFolder.newFolder("audit-symlink-target").toPath();
        Files.createDirectories(elsewhere.resolve("iris/pack"));
        try {
            Files.createSymbolicLink(levelRoot.resolve("dimensions/iris/linked"), elsewhere);
        } catch (IOException | UnsupportedOperationException unsupported) {
            assumeNoException("symbolic links are not creatable on this filesystem", unsupported);
        }
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("worlds.world_iris_linked.generator", "Iris:overworld");
        yaml.save(configuration);

        List<BukkitWorldConfiguration.IrisWorldStorageEntry> audited =
                BukkitWorldConfiguration.auditIrisWorldStorage(configuration, "world", levelRoot);

        assertEquals(BukkitWorldConfiguration.IrisWorldStorageState.UNUSABLE, stateOf(audited, "world_iris_linked"));
        assertTrue(entryOf(audited, "world_iris_linked").detail(),
                entryOf(audited, "world_iris_linked").detail().contains("symbolic link"));
    }

    @Test
    public void auditStillReportsATrulyAbsentDimensionPathAsAnOrphan() throws Exception {
        File configuration = temporaryFolder.newFile("audit-absent.yml");
        Path levelRoot = temporaryFolder.newFolder("audit-absent-level-root").toPath();
        Files.createDirectories(levelRoot.resolve("dimensions/iris"));
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("worlds.world_iris_absent.generator", "Iris:overworld");
        yaml.save(configuration);

        assertEquals(
                BukkitWorldConfiguration.IrisWorldStorageState.MISSING,
                stateOf(BukkitWorldConfiguration.auditIrisWorldStorage(configuration, "world", levelRoot),
                        "world_iris_absent"));
    }

    @Test
    public void missingStorageIsReportedOncePerWorld() throws Exception {
        MissingWorldStorageLog.reset();
        File configuration = temporaryFolder.newFile("orphan-warning-bukkit.yml");
        Path levelRoot = temporaryFolder.newFolder("orphan-warning-level-root").toPath();
        Files.createDirectories(levelRoot.resolve("dimensions/iris"));
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("worlds.world_iris_gone.generator", "Iris:overworld");
        yaml.save(configuration);

        assertFalse(MissingWorldStorageLog.hasWarned("world_iris_gone"));
        BukkitWorldConfiguration.readIrisGeneratorBindings(configuration, "world", levelRoot);

        assertTrue(MissingWorldStorageLog.hasWarned("world_iris_gone"));
        MissingWorldStorageLog.reset();
    }

    private static BukkitWorldConfiguration.IrisWorldStorageState stateOf(
            List<BukkitWorldConfiguration.IrisWorldStorageEntry> entries,
            String configuredWorldName
    ) {
        return entryOf(entries, configuredWorldName).state();
    }

    private static BukkitWorldConfiguration.IrisWorldStorageEntry entryOf(
            List<BukkitWorldConfiguration.IrisWorldStorageEntry> entries,
            String configuredWorldName
    ) {
        return entries.stream()
                .filter(entry -> entry.configuredWorldName().equals(configuredWorldName))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No audit entry for " + configuredWorldName));
    }

    @Test
    public void registersAndRemovesWorldAtomically() throws Exception {
        File configuration = temporaryFolder.newFile("bukkit.yml");

        assertEquals(BukkitWorldConfiguration.Registration.CREATED,
                BukkitWorldConfiguration.register(configuration, "probe", "overworld", 1337L));
        assertEquals(BukkitWorldConfiguration.Registration.UNCHANGED,
                BukkitWorldConfiguration.register(configuration, "probe", "overworld", 1337L));

        YamlConfiguration loaded = YamlConfiguration.loadConfiguration(configuration);
        assertEquals("Iris:overworld", loaded.getString("worlds.probe.generator"));
        assertEquals(1337L, loaded.getLong("worlds.probe.seed"));
        assertTrue(BukkitWorldConfiguration.remove(configuration, "probe"));
        assertFalse(BukkitWorldConfiguration.remove(configuration, "probe"));
        assertNull(YamlConfiguration.loadConfiguration(configuration).getConfigurationSection("worlds"));
    }

    @Test
    public void conflictingRegistrationLeavesOriginalUntouched() throws Exception {
        File configuration = temporaryFolder.newFile("bukkit.yml");
        BukkitWorldConfiguration.register(configuration, "probe", "overworld", 1337L);

        IOException failure = assertThrows(IOException.class,
                () -> BukkitWorldConfiguration.register(configuration, "probe", "theend", 42L));

        assertTrue(failure.getMessage().contains("different definition"));
        YamlConfiguration loaded = YamlConfiguration.loadConfiguration(configuration);
        assertEquals("Iris:overworld", loaded.getString("worlds.probe.generator"));
        assertEquals(1337L, loaded.getLong("worlds.probe.seed"));
    }

    @Test
    public void malformedConfigurationIsPreserved() throws Exception {
        File configuration = temporaryFolder.newFile("bukkit.yml");
        String malformed = "worlds: [unterminated";
        Files.writeString(configuration.toPath(), malformed);

        IOException failure = assertThrows(IOException.class,
                () -> BukkitWorldConfiguration.register(configuration, "probe", "overworld", 1337L));

        assertTrue(failure.getMessage().contains("invalid"));
        assertEquals(malformed, Files.readString(configuration.toPath()));
    }

    @Test
    public void unsafeWorldNameIsRejected() throws Exception {
        File configuration = temporaryFolder.newFile("bukkit.yml");

        assertThrows(IllegalArgumentException.class,
                () -> BukkitWorldConfiguration.register(configuration, "nested.world", "overworld", 1337L));
        assertEquals(0L, configuration.length());
    }

    @Test
    public void matchingRemovalIsCommittedOnce() throws Exception {
        File configuration = temporaryFolder.newFile("bukkit.yml");
        BukkitWorldConfiguration.register(configuration, "iris-one", "overworld", null);
        BukkitWorldConfiguration.register(configuration, "iris-two", "overworld", null);
        BukkitWorldConfiguration.register(configuration, "persistent", "overworld", null);

        assertEquals(2, BukkitWorldConfiguration.removeMatching(
                configuration,
                worldName -> worldName.startsWith("iris-")));

        YamlConfiguration loaded = YamlConfiguration.loadConfiguration(configuration);
        assertNull(loaded.get("worlds.iris-one"));
        assertNull(loaded.get("worlds.iris-two"));
        assertEquals("Iris:overworld", loaded.getString("worlds.persistent.generator"));
    }

    @Test
    public void conditionalRemovalPreservesChangedRegistration() throws Exception {
        File configuration = temporaryFolder.newFile("bukkit.yml");
        BukkitWorldConfiguration.register(configuration, "probe", "overworld", 1337L);

        assertFalse(BukkitWorldConfiguration.removeIfMatching(
                configuration,
                "probe",
                "overworld",
                42L));
        assertFalse(BukkitWorldConfiguration.removeIfMatching(
                configuration,
                "probe",
                "theend",
                1337L));

        YamlConfiguration preserved = YamlConfiguration.loadConfiguration(configuration);
        assertEquals("Iris:overworld", preserved.getString("worlds.probe.generator"));
        assertEquals(1337L, preserved.getLong("worlds.probe.seed"));
        assertTrue(BukkitWorldConfiguration.removeIfMatching(
                configuration,
                "probe",
                "overworld",
                1337L));
        assertNull(YamlConfiguration.loadConfiguration(configuration).get("worlds.probe"));
    }

    @Test
    public void replacementChangesOnlyGeneratorAndSeed() throws Exception {
        File configuration = temporaryFolder.newFile("bukkit.yml");
        YamlConfiguration initial = new YamlConfiguration();
        initial.set("settings.allow-end", true);
        initial.set("worlds.world_nether.generator", "VanillaNether");
        initial.set("worlds.world_nether.seed", 7L);
        initial.set("worlds.world_nether.environment", "NETHER");
        initial.set("worlds.world_nether.keep-spawn-loaded", false);
        initial.save(configuration);

        BukkitWorldConfiguration.WorldGeneratorSnapshot original =
                BukkitWorldConfiguration.snapshot(configuration, "world_nether");
        BukkitWorldConfiguration.GeneratorReplacement replacement =
                BukkitWorldConfiguration.replaceIfMatching(
                        configuration,
                        "world_nether",
                        original,
                        "underworld",
                        1337L
                );

        assertTrue(replacement.applied());
        assertEquals(original, replacement.observed());
        assertEquals("VanillaNether", original.generator());
        assertEquals(Long.valueOf(7L), original.seed());
        assertEquals("Iris:underworld", replacement.replacement().generator());
        assertEquals(Long.valueOf(1337L), replacement.replacement().seed());
        YamlConfiguration loaded = YamlConfiguration.loadConfiguration(configuration);
        assertEquals("Iris:underworld", loaded.getString("worlds.world_nether.generator"));
        assertEquals(1337L, loaded.getLong("worlds.world_nether.seed"));
        assertEquals("NETHER", loaded.getString("worlds.world_nether.environment"));
        assertFalse(loaded.getBoolean("worlds.world_nether.keep-spawn-loaded"));
        assertTrue(loaded.getBoolean("settings.allow-end"));
    }

    @Test
    public void staleReplacementSnapshotDoesNotWrite() throws Exception {
        File configuration = temporaryFolder.newFile("bukkit.yml");
        BukkitWorldConfiguration.register(configuration, "probe", "overworld", 1337L);
        BukkitWorldConfiguration.WorldGeneratorSnapshot original =
                BukkitWorldConfiguration.snapshot(configuration, "probe");
        BukkitWorldConfiguration.GeneratorReplacement first =
                BukkitWorldConfiguration.replaceIfMatching(
                        configuration,
                        "probe",
                        original,
                        "underworld",
                        42L
                );
        assertTrue(first.applied());
        String beforeStaleAttempt = Files.readString(configuration.toPath());

        BukkitWorldConfiguration.GeneratorReplacement stale =
                BukkitWorldConfiguration.replaceIfMatching(
                        configuration,
                        "probe",
                        original,
                        "theend",
                        99L
                );

        assertFalse(stale.applied());
        assertEquals(first.replacement(), stale.observed());
        assertEquals(beforeStaleAttempt, Files.readString(configuration.toPath()));
    }

    @Test
    public void restorationPreservesConcurrentUnrelatedFields() throws Exception {
        File configuration = temporaryFolder.newFile("bukkit.yml");
        YamlConfiguration initial = new YamlConfiguration();
        initial.set("worlds.world_nether.generator", "VanillaNether");
        initial.set("worlds.world_nether.seed", 7L);
        initial.set("worlds.world_nether.environment", "NETHER");
        initial.save(configuration);
        BukkitWorldConfiguration.WorldGeneratorSnapshot original =
                BukkitWorldConfiguration.snapshot(configuration, "world_nether");
        BukkitWorldConfiguration.GeneratorReplacement replacement =
                BukkitWorldConfiguration.replaceIfMatching(
                        configuration,
                        "world_nether",
                        original,
                        "underworld",
                        1337L
                );

        YamlConfiguration concurrent = YamlConfiguration.loadConfiguration(configuration);
        concurrent.set("worlds.world_nether.environment", "CUSTOM");
        concurrent.set("worlds.world_nether.extra", "preserve");
        concurrent.save(configuration);

        assertTrue(BukkitWorldConfiguration.restoreIfMatching(
                configuration,
                "world_nether",
                replacement.replacement(),
                original
        ));
        YamlConfiguration restored = YamlConfiguration.loadConfiguration(configuration);
        assertEquals("VanillaNether", restored.getString("worlds.world_nether.generator"));
        assertEquals(7L, restored.getLong("worlds.world_nether.seed"));
        assertEquals("CUSTOM", restored.getString("worlds.world_nether.environment"));
        assertEquals("preserve", restored.getString("worlds.world_nether.extra"));
    }

    @Test
    public void staleRestorationDoesNotOverwriteChangedGenerator() throws Exception {
        File configuration = temporaryFolder.newFile("bukkit.yml");
        BukkitWorldConfiguration.register(configuration, "probe", "overworld", 1337L);
        BukkitWorldConfiguration.WorldGeneratorSnapshot original =
                BukkitWorldConfiguration.snapshot(configuration, "probe");
        BukkitWorldConfiguration.GeneratorReplacement replacement =
                BukkitWorldConfiguration.replaceIfMatching(
                        configuration,
                        "probe",
                        original,
                        "underworld",
                        42L
                );
        YamlConfiguration concurrent = YamlConfiguration.loadConfiguration(configuration);
        concurrent.set("worlds.probe.generator", "ExternalGenerator");
        concurrent.save(configuration);
        String beforeRestore = Files.readString(configuration.toPath());

        assertFalse(BukkitWorldConfiguration.restoreIfMatching(
                configuration,
                "probe",
                replacement.replacement(),
                original
        ));
        assertEquals(beforeRestore, Files.readString(configuration.toPath()));
    }

    @Test
    public void restorationRemovesSectionsCreatedByReplacement() throws Exception {
        File configuration = temporaryFolder.newFile("bukkit.yml");
        YamlConfiguration initial = new YamlConfiguration();
        initial.set("settings.allow-end", true);
        initial.save(configuration);
        BukkitWorldConfiguration.WorldGeneratorSnapshot original =
                BukkitWorldConfiguration.snapshot(configuration, "world_nether");
        BukkitWorldConfiguration.GeneratorReplacement replacement =
                BukkitWorldConfiguration.replaceIfMatching(
                        configuration,
                        "world_nether",
                        original,
                        "underworld",
                        null
                );

        assertTrue(BukkitWorldConfiguration.restoreIfMatching(
                configuration,
                "world_nether",
                replacement.replacement(),
                original
        ));
        YamlConfiguration restored = YamlConfiguration.loadConfiguration(configuration);
        assertNull(restored.getConfigurationSection("worlds"));
        assertTrue(restored.getBoolean("settings.allow-end"));
    }

    @Test
    public void restorationRetainsConcurrentFieldsInNewWorldSection() throws Exception {
        File configuration = temporaryFolder.newFile("bukkit.yml");
        BukkitWorldConfiguration.WorldGeneratorSnapshot original =
                BukkitWorldConfiguration.snapshot(configuration, "world_nether");
        BukkitWorldConfiguration.GeneratorReplacement replacement =
                BukkitWorldConfiguration.replaceIfMatching(
                        configuration,
                        "world_nether",
                        original,
                        "underworld",
                        1337L
                );
        YamlConfiguration concurrent = YamlConfiguration.loadConfiguration(configuration);
        concurrent.set("worlds.world_nether.environment", "NETHER");
        concurrent.save(configuration);

        assertTrue(BukkitWorldConfiguration.restoreIfMatching(
                configuration,
                "world_nether",
                replacement.replacement(),
                original
        ));
        YamlConfiguration restored = YamlConfiguration.loadConfiguration(configuration);
        assertNull(restored.get("worlds.world_nether.generator"));
        assertNull(restored.get("worlds.world_nether.seed"));
        assertEquals("NETHER", restored.getString("worlds.world_nether.environment"));
    }

    @Test
    public void snapshotRefusesMalformedGeneratorWithoutChangingFile() throws Exception {
        File configuration = temporaryFolder.newFile("bukkit.yml");
        String malformed = "worlds:\n  probe:\n    generator:\n      nested: value\n    seed: 7\n";
        Files.writeString(configuration.toPath(), malformed);

        IOException failure = assertThrows(IOException.class,
                () -> BukkitWorldConfiguration.snapshot(configuration, "probe"));

        assertTrue(failure.getMessage().contains("generator"));
        assertEquals(malformed, Files.readString(configuration.toPath()));
    }

    @Test
    public void replacementRejectsSymbolicConfigurationWithoutChangingLinkOrTarget() throws Exception {
        Path target = temporaryFolder.newFile("shared-bukkit.yml").toPath();
        String original = "settings:\n  allow-end: true\n";
        Files.writeString(target, original);
        Path link = temporaryFolder.getRoot().toPath().resolve("bukkit.yml");
        try {
            Files.createSymbolicLink(link, target.getFileName());
        } catch (IOException | UnsupportedOperationException failure) {
            assumeNoException(failure);
        }
        BukkitWorldConfiguration.WorldGeneratorSnapshot expected =
                new BukkitWorldConfiguration.WorldGeneratorSnapshot(false, false, false, null, false, null);

        assertThrows(IOException.class, () -> BukkitWorldConfiguration.replaceIfMatching(
                link.toFile(),
                "world_nether",
                expected,
                "underworld",
                1337L
        ));

        assertTrue(Files.isSymbolicLink(link));
        assertEquals(original, Files.readString(target));
    }

    @Test
    public void snapshotRejectsSpecialConfigurationWithoutOpeningIt() throws Exception {
        assumeTrue(FileSystems.getDefault().supportedFileAttributeViews().contains("unix"));
        Path socket = temporaryFolder.getRoot().toPath().resolve("bukkit.yml");
        try (ServerSocketChannel channel = ServerSocketChannel.open(StandardProtocolFamily.UNIX)) {
            try {
                channel.bind(UnixDomainSocketAddress.of(socket));
            } catch (IOException | UnsupportedOperationException failure) {
                assumeNoException(failure);
            }

            assertThrows(IOException.class, () -> BukkitWorldConfiguration.snapshot(
                    socket.toFile(),
                    "world_nether"
            ));
        }
    }
}
