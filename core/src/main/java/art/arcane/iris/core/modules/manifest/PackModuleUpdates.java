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

import art.arcane.iris.core.modules.manifest.ModuleUpdateReport.ModuleChange;
import art.arcane.iris.core.modules.manifest.ModuleUpdateReport.Outcome;
import art.arcane.iris.core.modules.manifest.ShippedModules.ShippedJar;
import art.arcane.iris.core.modules.manifest.ShippedModules.ShippedModule;
import com.volmit.host.PackModules;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;

/**
 * Brings the module jars of one pack up to what this Iris ships, as far as the pack allows. The pack's
 * {@code modules.json} names the modules it uses; Iris installs the shipped jar of each that has none and replaces a jar
 * that differs from the shipped one, except:
 * <ul>
 *   <li>a module the manifest pins,</li>
 *   <li>a module the pack holds in a newer version than Iris ships,</li>
 *   <li>a module whose major or minor version changed: it may place different blocks or read settings differently, so
 *       the pack's author decides ({@link Policy#TAKING_EVERY_UPDATE}). Only a patch is taken unasked,</li>
 *   <li>a module that shares code with one of the above. A pack loads its jars in one class loader, and a jar compiled
 *       against the new version of another may not link against the kept one.</li>
 * </ul>
 * A pack without the manifest, and a module jar Iris does not ship, are left alone.
 */
public final class PackModuleUpdates {
    public enum Policy {
        /** What happens on its own before a pack loads: patches only. */
        PATCHES_ONLY(true),
        /** What the pack's author, or a world's owner, asks for with the update command. */
        TAKING_EVERY_UPDATE(false);

        private final boolean keepsNewReleases;

        Policy(boolean keepsNewReleases) {
            this.keepsNewReleases = keepsNewReleases;
        }
    }

    private static final String HASH_ALGORITHM = "SHA-256";
    private static final String STAGED_JAR_SUFFIX = ".installing";

    /** A shipped jar the manifest asks for, with what happens to the pack's copy of it. */
    private static final class ManagedJar {
        private final ShippedJar shipped;
        private final Path installed;
        private Outcome outcome;
        private String keptWith;

        private ManagedJar(ShippedJar shipped, Path installed, Outcome outcome) {
            this.shipped = shipped;
            this.installed = installed;
            this.outcome = outcome;
        }

        private void keepBecauseOf(ManagedJar kept) {
            outcome = Outcome.KEPT_SHARED_CODE;
            keptWith = kept.keptWith != null ? kept.keptWith : kept.shipped.modules().getFirst().id();
        }
    }

    private final Path packFolder;
    private final Path modulesFolder;
    private final PackModuleManifest manifest;
    private final PackModuleLock lock;
    private final ShippedModules shipped;
    private final Policy policy;

    private PackModuleUpdates(Path packFolder, PackModuleManifest manifest, PackModuleLock lock, ShippedModules shipped, Policy policy) {
        this.packFolder = packFolder;
        this.modulesFolder = packFolder.resolve(PackModules.MODULES_DIRECTORY);
        this.manifest = manifest;
        this.lock = lock;
        this.shipped = shipped;
        this.policy = policy;
    }

    /**
     * @throws PackModuleManifest.ManifestFormatException when the pack's {@code modules.json} is not a manifest
     */
    public static ModuleUpdateReport bringUpToDate(Path packFolder, ShippedModules shipped, Policy policy) throws IOException {
        Optional<PackModuleManifest> manifest = PackModuleManifest.read(packFolder);
        if (manifest.isEmpty()) {
            return ModuleUpdateReport.unmanaged();
        }
        return new PackModuleUpdates(packFolder, manifest.get(), PackModuleLock.read(packFolder), shipped, policy).bringUpToDate();
    }

    /**
     * Gives a new pack every module Iris ships: writes its {@code modules.json} and installs the jars. A build that ships
     * no module, as Iris run from its classes in its own tests, leaves the pack without both.
     */
    public static void installAllInto(Path newPackFolder, ShippedModules shipped) throws IOException {
        if (shipped.isEmpty()) {
            return;
        }
        PackModuleManifest.listingAll(shipped).write(newPackFolder);
        bringUpToDate(newPackFolder, shipped, Policy.TAKING_EVERY_UPDATE);
    }

    private ModuleUpdateReport bringUpToDate() throws IOException {
        List<ManagedJar> jars = new ArrayList<>();
        for (ShippedJar jar : shipped.jars()) {
            if (jar.modules().stream().map(ShippedModule::id).anyMatch(manifest::lists)) {
                jars.add(decide(jar));
            }
        }
        keepJarsThatShareCodeWithKeptOnes(jars);
        Map<String, String> installedVersions = new HashMap<>(lock.installed());
        for (ManagedJar jar : jars) {
            if (jar.outcome.wroteJar()) {
                install(jar);
                jar.shipped.modules().forEach(module -> installedVersions.put(module.id(), module.version()));
            }
        }
        new PackModuleLock(installedVersions).writeIfChanged(packFolder);
        return new ModuleUpdateReport(true, jars.stream().flatMap(jar -> changesOf(jar).stream()).toList(), modulesBuiltWithNewerVersions());
    }

    private ManagedJar decide(ShippedJar jar) throws IOException {
        Path installed = modulesFolder.resolve(jar.file());
        return new ManagedJar(jar, installed, outcomeFor(jar, installed));
    }

    private Outcome outcomeFor(ShippedJar jar, Path installed) throws IOException {
        if (!Files.isRegularFile(installed)) {
            return Outcome.INSTALLED;
        }
        if (hashOf(Files.readAllBytes(installed)).equals(jar.sha256())) {
            return Outcome.CURRENT;
        }
        if (anyModule(jar, module -> manifest.pins(module.id()))) {
            return Outcome.KEPT_PINNED;
        }
        if (anyModule(jar, this::isInstalledNewer)) {
            return Outcome.KEPT_NEWER_THAN_SHIPPED;
        }
        if (policy.keepsNewReleases && anyModule(jar, this::isNewRelease)) {
            return Outcome.KEPT_OUT_OF_DATE;
        }
        return Outcome.UPDATED;
    }

    private static boolean anyModule(ShippedJar jar, Predicate<ShippedModule> condition) {
        return jar.modules().stream().anyMatch(condition);
    }

    private boolean isInstalledNewer(ShippedModule module) {
        Optional<ModuleVersion> installed = installedVersionOf(module);
        Optional<ModuleVersion> offered = ModuleVersion.parse(module.version());
        return installed.isPresent() && offered.isPresent() && installed.get().isNewerThan(offered.get());
    }

    /** A version Iris cannot compare may be a new release, so the pack keeps what it has. */
    private boolean isNewRelease(ShippedModule module) {
        Optional<ModuleVersion> installed = installedVersionOf(module);
        Optional<ModuleVersion> offered = ModuleVersion.parse(module.version());
        return installed.isEmpty() || offered.isEmpty() || !offered.get().isSameReleaseAs(installed.get());
    }

    private Optional<ModuleVersion> installedVersionOf(ShippedModule module) {
        return lock.versionOf(module.id()).flatMap(ModuleVersion::parse);
    }

    /**
     * An updated jar was compiled against the shipped version of every jar it shares classes with. When one of those
     * stays at its installed version, the updated jar stays too, and so on along the jars that share code with it.
     */
    private void keepJarsThatShareCodeWithKeptOnes(List<ManagedJar> jars) throws IOException {
        Deque<ManagedJar> kept = new ArrayDeque<>(jars.stream().filter(jar -> jar.outcome.keptInstalledJar()).toList());
        List<ManagedJar> updating = jars.stream().filter(jar -> jar.outcome == Outcome.UPDATED).toList();
        if (kept.isEmpty() || updating.isEmpty()) {
            return;
        }
        Map<ManagedJar, JarClasses> classes = new HashMap<>();
        for (ManagedJar jar : kept) {
            classes.put(jar, JarClasses.read(Files.readAllBytes(jar.installed)));
        }
        for (ManagedJar jar : updating) {
            classes.put(jar, JarClasses.read(shipped.bytesOf(jar.shipped)).mergedWith(JarClasses.read(Files.readAllBytes(jar.installed))));
        }
        while (!kept.isEmpty()) {
            ManagedJar keptJar = kept.pop();
            for (ManagedJar candidate : updating) {
                if (candidate.outcome == Outcome.UPDATED && shareCode(classes.get(candidate), classes.get(keptJar))) {
                    candidate.keepBecauseOf(keptJar);
                    kept.push(candidate);
                }
            }
        }
    }

    private static boolean shareCode(JarClasses first, JarClasses second) {
        return first.names(second) || second.names(first);
    }

    /** Writes beside the target and moves over it, so a pack never loads half a jar. */
    private void install(ManagedJar jar) throws IOException {
        Files.createDirectories(modulesFolder);
        Path staged = modulesFolder.resolve(jar.shipped.file() + STAGED_JAR_SUFFIX);
        Files.write(staged, shipped.bytesOf(jar.shipped));
        Files.move(staged, jar.installed, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    }

    private List<ModuleChange> changesOf(ManagedJar jar) {
        return jar.shipped.modules().stream()
                .map(module -> new ModuleChange(module.id(), installedVersionIn(jar, module), module.version(), jar.outcome,
                        Optional.empty(), Optional.ofNullable(jar.keptWith)))
                .toList();
    }

    private Optional<String> installedVersionIn(ManagedJar jar, ShippedModule module) {
        return jar.outcome == Outcome.INSTALLED ? Optional.empty() : lock.versionOf(module.id());
    }

    private List<ModuleChange> modulesBuiltWithNewerVersions() {
        List<ModuleChange> newer = new ArrayList<>();
        manifest.modules().forEach((moduleId, builtWith) -> shipped.module(moduleId)
                .filter(module -> isNewer(builtWith, module.version()))
                .ifPresent(module -> newer.add(new ModuleChange(moduleId, lock.versionOf(moduleId), module.version(), Outcome.CURRENT,
                        Optional.of(builtWith), Optional.empty()))));
        return newer;
    }

    private static boolean isNewer(String version, String than) {
        Optional<ModuleVersion> first = ModuleVersion.parse(version);
        Optional<ModuleVersion> second = ModuleVersion.parse(than);
        return first.isPresent() && second.isPresent() && first.get().isNewerThan(second.get());
    }

    static String hashOf(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance(HASH_ALGORITHM).digest(bytes));
        } catch (NoSuchAlgorithmException missing) {
            throw new IllegalStateException("This JDK has no " + HASH_ALGORITHM, missing);
        }
    }
}
