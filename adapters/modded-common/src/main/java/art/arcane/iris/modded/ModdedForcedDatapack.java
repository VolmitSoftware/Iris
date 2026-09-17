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

package art.arcane.iris.modded;

import art.arcane.iris.spi.IrisPlatforms;
import art.arcane.iris.core.loader.IrisData;
import art.arcane.iris.core.localization.IrisLanguage;
import art.arcane.iris.core.localization.RuntimeUiMessages;
import art.arcane.iris.core.nms.datapack.DataVersion;
import art.arcane.iris.core.nms.datapack.IDataFixer;
import art.arcane.iris.core.pack.PackDirectoryResolver;
import art.arcane.iris.core.pack.PackValidationResult;
import art.arcane.iris.core.pack.PackValidator;
import art.arcane.iris.engine.object.world.dimension.IrisDimension;
import art.arcane.iris.engine.object.world.dimension.IrisDimensionType;
import art.arcane.volmlib.util.collection.KList;
import art.arcane.volmlib.util.collection.KSet;
import net.minecraft.network.chat.Component;
import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.PackSelectionConfig;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.PathPackResources;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraft.server.packs.repository.RepositorySource;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.stream.Stream;

public final class ModdedForcedDatapack {
    private static final String PACK_ID = "iris_worldgen";
    private static final String PACK_FOLDER = "iris";
    private static final String HASH_FILE_NAME = "packs.hash";
    // Bump this whenever the emitted datapack content changes for reasons the pack-directory hash cannot see.
    // v2: custom biomes now inherit their vanilla derivative's biome tags, so every already-published pack has
    // to regenerate once.
    private static final String HASH_SALT = "iris-forced-datapack-v2";
    private static final long PACKS_HASH_TTL_NANOS = 2_000_000_000L;
    private static final Object LOCK = new Object();
    private static final AtomicBoolean LOADED = new AtomicBoolean(false);
    private static final AtomicBoolean STALE_SERVE_LOGGED = new AtomicBoolean(false);
    private static volatile PublishedState published;
    private static volatile HashMemo packsHashMemo;

    private ModdedForcedDatapack() {
    }

    public static RepositorySource repositorySource() {
        return (Consumer<Pack> consumer) -> {
            Pack pack = servePack();
            consumer.accept(pack);
            LOADED.set(true);
        };
    }

    /**
     * Serves the published datapack without regenerating it whenever the installed packs still hash to what
     * was generated last. That fast path is lock-free; everything else takes LOCK and rechecks, so a boot-time
     * daemon regeneration and a loadPacks regeneration can never stage concurrently (publishDirectory moves the
     * live directory aside, which would break a concurrent read).
     *
     * <p>A hash mismatch is never served: the HASH_SALT bump alone mismatches every install on its first boot
     * after an upgrade, and serving that directory hands Create World a pack without the current biome tags.
     * A published pack whose hash cannot be computed at all is still served, with one warning.
     */
    private static Pack servePack() {
        String currentHash = packsHashOrEmpty();
        PublishedState current = publishedState();
        if (current != null && !currentHash.isEmpty() && current.packsHash().equals(currentHash)) {
            try {
                return requireReadablePack(current.directory());
            } catch (RuntimeException unreadable) {
                published = null;
                ModdedIrisLog.error("Iris could not read the published forced datapack at {}; regenerating",
                        current.directory(), unreadable);
            }
        }
        synchronized (LOCK) {
            String hash = packsHashOrEmpty();
            PublishedState state = publishedState();
            String reason;
            if (state == null) {
                reason = "no published pack";
            } else if (!hash.isEmpty() && !state.packsHash().equals(hash)) {
                reason = "stale cache (hash changed)";
            } else {
                if (hash.isEmpty() && STALE_SERVE_LOGGED.compareAndSet(false, true)) {
                    ModdedIrisLog.warn("Iris cannot hash the installed packs; serving the last generated forced datapack from {} unverified",
                            state.directory());
                }
                try {
                    return requireReadablePack(state.directory());
                } catch (RuntimeException unreadable) {
                    published = null;
                    ModdedIrisLog.error("Iris could not read the published forced datapack at {}; regenerating",
                            state.directory(), unreadable);
                }
                reason = "unreadable published pack";
            }
            ModdedIrisLog.info("Iris forced datapack cache is unusable ({}); generating it once now", reason);
            return buildPack();
        }
    }

    public static void verifyInjected() {
        if (LOADED.get()) {
            return;
        }
        Path packsRoot = packsRoot();
        List<File> packs = PackDirectoryResolver.listVisiblePackDirectories(packsRoot.toFile());
        if (packs.isEmpty()) {
            return;
        }
        ModdedIrisLog.error("===============================================================");
        ModdedIrisLog.error("Iris forced datapack '{}' was never loaded by this server.", PACK_ID);
        ModdedIrisLog.error("{} installed pack(s) at {} contributed no dimension types or custom biomes.", packs.size(), packsRoot);
        ModdedIrisLog.error("Datapack source injection failed for this loader (mixin/event not applied), so world creation will fail and restarting will not fix it.");
        ModdedIrisLog.error("===============================================================");
    }

    public static Path datapackRoot() {
        return ModdedEngineBootstrap.loader().configDir().resolve("irisworldgen").resolve("generated").resolve("datapack");
    }

    private static Path packDirectory() {
        return datapackRoot().resolve(PACK_FOLDER);
    }

    private static Pack buildPack() {
        try {
            return requireReadablePack(regenerate());
        } catch (RuntimeException | Error generationFailure) {
            Path lastKnownGood = packDirectory();
            if (Files.isRegularFile(lastKnownGood.resolve("pack.mcmeta"))) {
                ModdedIrisLog.error("Iris kept the last known-good generated datapack after regeneration failed",
                        generationFailure);
                return requireReadablePack(lastKnownGood);
            }
            throw generationFailure;
        }
    }

    private static Pack requireReadablePack(Path directory) {
        PackLocationInfo location = new PackLocationInfo(
                PACK_ID,
                Component.literal(IrisLanguage.plain(RuntimeUiMessages.FORCED_DATAPACK_NAME)),
                PackSource.BUILT_IN,
                Optional.empty());
        PackSelectionConfig selection = new PackSelectionConfig(true, Pack.Position.TOP, true);
        PathPackResources.PathResourcesSupplier supplier = new PathPackResources.PathResourcesSupplier(directory);
        Pack pack = Pack.readMetaAndCreate(location, supplier, PackType.SERVER_DATA, selection);
        if (pack == null) {
            throw new IllegalStateException("Iris forced datapack at " + directory
                    + " produced no readable pack metadata");
        }
        return pack;
    }

    public static Path regenerate() {
        synchronized (LOCK) {
            try {
                return write();
            } catch (Throwable e) {
                ModdedIrisLog.error("Iris failed to generate the forced startup datapack", e);
                if (e instanceof RuntimeException runtimeException) {
                    throw runtimeException;
                }
                if (e instanceof Error fatalError) {
                    throw fatalError;
                }
                throw new IllegalStateException("Iris failed to generate the forced startup datapack", e);
            }
        }
    }

    /**
     * Regenerates only when the installed packs no longer hash to the published datapack. Used by the boot
     * trigger so a steady-state restart does not pay the full staging cost.
     */
    public static boolean regenerateIfStale(String reason) {
        synchronized (LOCK) {
            String currentHash = packsHashOrEmpty();
            PublishedState state = publishedState();
            if (state != null && !currentHash.isEmpty() && state.packsHash().equals(currentHash)) {
                ModdedIrisLog.debug("Iris forced datapack is current ({}); skipping regeneration", reason);
                return false;
            }
            ModdedIrisLog.info("Iris regenerating the forced datapack ({})", reason);
            regenerate();
            return true;
        }
    }

    /**
     * Off-thread regeneration trigger. Call sites that run on the server thread must use this so a command
     * or lifecycle hook never blocks on pack staging.
     */
    public static void scheduleRegeneration(String reason) {
        Runnable task = () -> {
            try {
                regenerateIfStale(reason);
            } catch (Throwable failure) {
                ModdedIrisLog.error("Iris forced datapack regeneration failed ({})", reason, failure);
            }
        };
        ModdedScheduler scheduler = ModdedEngineBootstrap.schedulerOrNull();
        if (scheduler != null) {
            scheduler.async(task);
            return;
        }
        Thread thread = new Thread(task, "iris-modded-datapack-regen");
        thread.setDaemon(true);
        thread.start();
    }

    private static Path write() throws IOException {
        String packsHash = packsHash();
        Path datapackRoot = datapackRoot();
        Files.createDirectories(datapackRoot);
        Path stagingDirectory = Files.createTempDirectory(datapackRoot, PACK_FOLDER + ".staging-");
        try {
            writeStagedPack(stagingDirectory);
            requireReadablePack(stagingDirectory);
            publishDirectory(stagingDirectory, packDirectory());
            writePublishedHash(packsHash);
            published = new PublishedState(packDirectory(), packsHash);
            // Publish the hash this run was built from as the memo too: a memo captured before staging would
            // otherwise mismatch what was just published and send the next serve straight back into buildPack.
            packsHashMemo = new HashMemo(packsHash, System.nanoTime());
            STALE_SERVE_LOGGED.set(false);
            return packDirectory();
        } catch (IOException | RuntimeException | Error failure) {
            try {
                clean(stagingDirectory);
            } catch (Throwable cleanupError) {
                failure.addSuppressed(cleanupError);
            }
            throw failure;
        }
    }

    private static PublishedState publishedState() {
        PublishedState current = published;
        if (current != null) {
            return current;
        }
        Path directory = packDirectory();
        if (!Files.isRegularFile(directory.resolve("pack.mcmeta"))) {
            return null;
        }
        PublishedState loaded = new PublishedState(directory, readPublishedHash());
        published = loaded;
        return loaded;
    }

    private static String readPublishedHash() {
        Path hashFile = datapackRoot().resolve(HASH_FILE_NAME);
        if (!Files.isRegularFile(hashFile)) {
            return "";
        }
        try {
            return Files.readString(hashFile, StandardCharsets.UTF_8).trim();
        } catch (IOException unreadable) {
            ModdedIrisLog.warn("Iris could not read the forced datapack hash at {}", hashFile, unreadable);
            return "";
        }
    }

    private static void writePublishedHash(String hash) throws IOException {
        Path hashFile = datapackRoot().resolve(HASH_FILE_NAME);
        Path temp = hashFile.resolveSibling(HASH_FILE_NAME + ".tmp-" + UUID.randomUUID());
        Files.writeString(temp, hash, StandardCharsets.UTF_8);
        try {
            Files.move(temp, hashFile, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException atomicUnsupported) {
            Files.move(temp, hashFile, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    /**
     * Short-TTL memo: loadPacks runs on every PackRepository reload and the hash walks every installed pack
     * file (thousands on a studio install), so back-to-back reloads must not re-walk the tree. The window is
     * small enough that an operator dropping in a pack still gets picked up on the next reload.
     */
    private static String packsHashOrEmpty() {
        long now = System.nanoTime();
        HashMemo memo = packsHashMemo;
        if (memo != null && now - memo.takenAtNanos() < PACKS_HASH_TTL_NANOS) {
            return memo.hash();
        }
        String hash;
        try {
            hash = packsHash();
        } catch (IOException | RuntimeException failure) {
            ModdedIrisLog.warn("Iris could not hash the installed packs directory", failure);
            hash = "";
        }
        packsHashMemo = new HashMemo(hash, now);
        return hash;
    }

    /**
     * Content hash over the installed packs: relative path, size and mtime of every regular file, plus the
     * pack format and loader the generated datapack is shaped for.
     */
    private static String packsHash() throws IOException {
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException missing) {
            throw new IllegalStateException("SHA-256 is unavailable", missing);
        }
        digest.update((HASH_SALT + '|' + ModdedEngineBootstrap.loader().platformName()
                + '|' + DataVersion.getLatest().getPackFormat() + '\n').getBytes(StandardCharsets.UTF_8));
        Path root = packsRoot();
        if (Files.isDirectory(root)) {
            List<String> entries = new ArrayList<>();
            Files.walkFileTree(root, new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult preVisitDirectory(Path directory, BasicFileAttributes attributes) {
                    return !directory.equals(root)
                            && PackDirectoryResolver.isHiddenName(directory.getFileName().toString())
                            ? FileVisitResult.SKIP_SUBTREE
                            : FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attributes) {
                    if (attributes.isRegularFile()) {
                        entries.add(root.relativize(file).toString().replace('\\', '/')
                                + '|' + attributes.size() + '|' + attributes.lastModifiedTime().toMillis());
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFileFailed(Path file, IOException failure) {
                    entries.add(root.relativize(file).toString().replace('\\', '/') + "|unreadable");
                    return FileVisitResult.CONTINUE;
                }
            });
            entries.sort(Comparator.naturalOrder());
            for (String entry : entries) {
                digest.update(entry.getBytes(StandardCharsets.UTF_8));
                digest.update((byte) '\n');
            }
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    private static void writeStagedPack(Path stagingDirectory) throws IOException {
        Map<String, KSet<String>> seenBiomes = new LinkedHashMap<>();
        IDataFixer fixer = DataVersion.getLatest().get();

        int packCount = 0;
        KList<String> presetIds = new KList<>();
        File root = packsRoot().toFile();
        List<File> packs = PackDirectoryResolver.listVisiblePackDirectoriesOrThrow(root);
        for (File pack : packs) {
            if (stagePack(pack, fixer, stagingDirectory, seenBiomes, presetIds)) {
                packCount++;
            }
        }

        writePackMeta(stagingDirectory);
        // Forge only: FML has no block-drops event Iris can use, so drops are routed through a global loot
        // modifier. NeoForge does not need one - IrisNeoForgeBootstrap listens to BlockDropsEvent and both
        // replaces and appends drops there, so emitting a modifier would double-apply them.
        if ("forge".equalsIgnoreCase(ModdedEngineBootstrap.loader().platformName())) {
            writeForgeBlockLootModifier(stagingDirectory);
        }
        if (!presetIds.isEmpty()) {
            writeWorldPresetTag(stagingDirectory, presetIds);
        }
        ModdedIrisLog.info("Iris forced startup datapack staged: {} pack(s), {} world preset(s), {} custom biome(s) at {}", packCount, presetIds.size(), countBiomes(seenBiomes), stagingDirectory);
        if (packCount == 0) {
            ModdedIrisLog.warn("Iris installed NO worldgen packs into the forced datapack - custom biomes and their colors will NOT generate. Install a pack with /iris download pack=overworld, /iris download pack=underworld, or /iris download link=<zip-url>, then restart before creating an Iris world.");
        }
    }

    private static boolean stagePack(File sourcePack, IDataFixer fixer, Path stagingDirectory,
                                     Map<String, KSet<String>> seenBiomes,
                                     KList<String> presetIds) throws IOException {
        PackValidationResult validation;
        try {
            validation = PackValidator.validateForDatapackBootstrap(sourcePack);
        } catch (Throwable validationFailure) {
            ModdedIrisLog.error("Iris excluded pack '{}' from Create World because validation failed",
                    sourcePack.getName(), validationFailure);
            rethrowIfUnrecoverable(validationFailure);
            return false;
        }
        if (!validation.isLoadable()) {
            ModdedIrisLog.error("Iris excluded pack '{}' from Create World: {} blocking validation error(s); first error: {}",
                    sourcePack.getName(), validation.getBlockingErrors().size(),
                    validation.getBlockingErrors().getFirst());
            return false;
        }

        Path packStagingDirectory = Files.createTempDirectory(
                stagingDirectory.getParent(), PACK_FOLDER + ".pack-" + sourcePack.getName() + "-");
        Map<String, KSet<String>> packBiomes = new LinkedHashMap<>();
        KList<String> packPresetIds = new KList<>();
        KList<File> packFolders = new KList<>();
        packFolders.add(packStagingDirectory.toFile());
        boolean installed;
        try {
            installed = installPack(sourcePack, fixer, packFolders, packBiomes, packPresetIds);
        } catch (Throwable installationFailure) {
            ModdedIrisLog.error("Iris excluded pack '{}' from Create World because datapack serialization failed",
                    sourcePack.getName(), installationFailure);
            rethrowIfUnrecoverable(installationFailure);
            installed = false;
        }

        try {
            if (!installed) {
                return false;
            }
            mergeDirectory(packStagingDirectory, stagingDirectory);
            mergeBiomes(seenBiomes, packBiomes);
            presetIds.addAll(packPresetIds);
            return true;
        } finally {
            try {
                clean(packStagingDirectory);
            } catch (Throwable cleanupFailure) {
                ModdedIrisLog.warn("Iris could not remove temporary datapack staging for pack '{}'",
                        sourcePack.getName(), cleanupFailure);
            }
        }
    }

    /**
     * Per-pack staging isolates every failure so one broken pack cannot brick Create World for all of them.
     * Only a VM-level failure (heap exhausted, native stack blown) is rethrown; LinkageError and
     * ExceptionInInitializerError are exactly the per-pack failures that must stay contained.
     */
    private static void rethrowIfUnrecoverable(Throwable failure) {
        if (failure instanceof OutOfMemoryError outOfMemory) {
            throw outOfMemory;
        }
        if (failure instanceof StackOverflowError stackOverflow) {
            throw stackOverflow;
        }
    }

    private static void mergeDirectory(Path sourceDirectory, Path destinationDirectory) throws IOException {
        List<Path> entries = new ArrayList<>();
        try (Stream<Path> walk = Files.walk(sourceDirectory)) {
            walk.sorted(Comparator.comparingInt(Path::getNameCount)).forEach(entries::add);
        }
        for (Path source : entries) {
            Path relative = sourceDirectory.relativize(source);
            Path destination = destinationDirectory.resolve(relative);
            if (Files.isDirectory(source)) {
                Files.createDirectories(destination);
            } else if (!Files.exists(destination)) {
                Files.copy(source, destination);
            }
        }
    }

    private static void mergeBiomes(Map<String, KSet<String>> destination,
                                    Map<String, KSet<String>> source) {
        for (Map.Entry<String, KSet<String>> entry : source.entrySet()) {
            destination.computeIfAbsent(entry.getKey(), ignored -> new KSet<>())
                    .addAll(entry.getValue());
        }
    }

    private static boolean installPack(File packFolder, IDataFixer fixer, KList<File> folders,
                                       Map<String, KSet<String>> seenBiomes,
                                       KList<String> presetIds) throws IOException {
        String packName = packFolder.getName();
        File dimensionsDirectory = new File(packFolder, "dimensions");
        if (!dimensionsDirectory.isDirectory()) {
            return false;
        }
        File[] dimensionFiles = dimensionsDirectory.listFiles(
                (File file) -> file.isFile() && file.getName().endsWith(".json"));
        if (dimensionFiles == null) {
            throw new IOException("Iris could not read dimensions for pack '" + packName + "'");
        }
        if (dimensionFiles.length == 0) {
            return false;
        }
        Arrays.sort(dimensionFiles, Comparator.comparing(File::getName));
        IrisData data = IrisData.get(packFolder);
        for (File dimensionFile : dimensionFiles) {
            String dimensionKey = dimensionFile.getName().substring(0, dimensionFile.getName().length() - ".json".length());
            IrisDimension dimension = data.getDimensionLoader().load(dimensionKey);
            if (dimension == null) {
                throw new IllegalStateException("Iris pack '" + packName + "' dimension '"
                        + dimensionKey + "' did not load while building the forced datapack");
            }
            String biomePathPrefix = ModdedWorldgenIds.biomePathPrefix(packName, dimensionKey);
            dimension.installBiomes(fixer, () -> data, folders, "irisworldgen", biomePathPrefix,
                    biomesForNamespace(seenBiomes, biomePathPrefix));
            dimension.installBiomes(fixer, () -> data, folders,
                    biomesForNamespace(seenBiomes, dimension.getLoadKey()));
            writeDimensionType(folders, fixer, dimension, packName, dimensionKey);
            String presetRef = ModdedWorldgenIds.presetRef(packName, dimensionKey);
            writeWorldPreset(folders, packName, dimensionKey, presetRef);
            presetIds.add(presetRef);
        }
        return true;
    }

    static KSet<String> biomesForNamespace(Map<String, KSet<String>> biomes, String namespace) {
        return biomes.computeIfAbsent(namespace, ignored -> new KSet<>());
    }

    static <T> T requireRegisteredDimensionType(String typeRef, Optional<T> registeredType,
                                                String pack, String packDimensionKey) {
        return registeredType.orElseThrow(() -> new IllegalStateException(
                "Iris dimension type '" + typeRef + "' for pack '" + pack + "' dimension '"
                        + packDimensionKey + "' is not loaded. Restart the server so the forced Iris datapack registers it before creating the world."
                        + (LOADED.get() ? "" : " The forced Iris datapack has not been loaded by this server at all"
                        + " (datapack source injection failed; see the Iris boot ERROR), so a restart alone will not register it.")));
    }

    private static void writeWorldPreset(KList<File> folders, String packName, String dimensionKey,
                                         String presetRef) throws IOException {
        String dimensionRef = dimensionKey.equals(packName) ? packName : packName + ":" + dimensionKey;
        String json = worldPresetJson(dimensionRef,
                ModdedWorldgenIds.dimensionTypeRef(packName, dimensionKey));
        String presetPath = presetRef.substring(presetRef.indexOf(':') + 1);
        for (File datapackRoot : folders) {
            Path output = datapackRoot.toPath().resolve("data").resolve("irisworldgen")
                    .resolve("worldgen").resolve("world_preset").resolve(presetPath + ".json");
            Files.createDirectories(output.getParent());
            Files.writeString(output, json, StandardCharsets.UTF_8);
        }
    }

    private static String worldPresetJson(String dimensionRef, String dimensionTypeRef) {
        return "{\n"
                + "  \"dimensions\": {\n"
                + "    \"minecraft:overworld\": {\n"
                + "      \"type\": \"" + dimensionTypeRef + "\",\n"
                + "      \"generator\": {\n"
                + "        \"type\": \"irisworldgen:iris\",\n"
                + "        \"biome_source\": {\n"
                + "          \"type\": \"minecraft:fixed\",\n"
                + "          \"biome\": \"minecraft:plains\"\n"
                + "        },\n"
                + "        \"dimension\": \"" + dimensionRef + "\"\n"
                + "      }\n"
                + "    },\n"
                + "    \"minecraft:the_nether\": {\n"
                + "      \"type\": \"minecraft:the_nether\",\n"
                + "      \"generator\": {\n"
                + "        \"type\": \"minecraft:noise\",\n"
                + "        \"settings\": \"minecraft:nether\",\n"
                + "        \"biome_source\": {\n"
                + "          \"type\": \"minecraft:multi_noise\",\n"
                + "          \"preset\": \"minecraft:nether\"\n"
                + "        }\n"
                + "      }\n"
                + "    },\n"
                + "    \"minecraft:the_end\": {\n"
                + "      \"type\": \"minecraft:the_end\",\n"
                + "      \"generator\": {\n"
                + "        \"type\": \"minecraft:noise\",\n"
                + "        \"settings\": \"minecraft:end\",\n"
                + "        \"biome_source\": {\n"
                + "          \"type\": \"minecraft:the_end\"\n"
                + "        }\n"
                + "      }\n"
                + "    }\n"
                + "  }\n"
                + "}\n";
    }

    private static void writeWorldPresetTag(Path packDirectory, KList<String> presetIds) throws IOException {
        StringBuilder values = new StringBuilder();
        for (int i = 0; i < presetIds.size(); i++) {
            if (i > 0) {
                values.append(",\n");
            }
            values.append("    \"").append(presetIds.get(i)).append("\"");
        }
        String json = "{\n"
                + "  \"replace\": false,\n"
                + "  \"values\": [\n"
                + values
                + "\n  ]\n"
                + "}\n";
        Path output = packDirectory.resolve("data").resolve("minecraft").resolve("tags").resolve("worldgen").resolve("world_preset").resolve("normal.json");
        Files.createDirectories(output.getParent());
        Files.writeString(output, json, StandardCharsets.UTF_8);
    }

    static void writeDimensionType(KList<File> folders, IDataFixer fixer, IrisDimension dimension,
                                   String pack, String packDimensionKey) throws IOException {
        IrisDimensionType type = dimension.getDimensionType();
        String json = type.toJson(fixer);
        String typeRef = ModdedWorldgenIds.dimensionTypeRef(pack, packDimensionKey);
        String typePath = typeRef.substring(typeRef.indexOf(':') + 1);
        for (File datapackRoot : folders) {
            Path output = datapackRoot.toPath().resolve("data").resolve("irisworldgen")
                    .resolve("dimension_type").resolve(typePath + ".json");
            Files.createDirectories(output.getParent());
            Files.writeString(output, json, StandardCharsets.UTF_8);
            // Load-bearing, not dead: worlds created before the scoped pack path reference
            // irisworldgen:<dimensionTypeKey> in their level.dat, and ModdedWorldEngines accepts that legacy
            // key when validating the runtime dimension contract. Removing this emission unloads those worlds.
            Path legacyOutput = datapackRoot.toPath().resolve("data").resolve("irisworldgen")
                    .resolve("dimension_type").resolve(dimension.getDimensionTypeKey() + ".json");
            if (!Files.exists(legacyOutput)) {
                Files.createDirectories(legacyOutput.getParent());
                Files.writeString(legacyOutput, json, StandardCharsets.UTF_8);
            }
        }
    }

    static void writeForgeBlockLootModifier(Path packDirectory) throws IOException {
        Path list = packDirectory.resolve("data").resolve("forge").resolve("loot_modifiers").resolve("global_loot_modifiers.json");
        Files.createDirectories(list.getParent());
        Files.writeString(list, "{\n"
                + "  \"replace\": false,\n"
                + "  \"entries\": [\"irisworldgen:block_drops\"]\n"
                + "}\n", StandardCharsets.UTF_8);

        Path modifier = packDirectory.resolve("data").resolve("irisworldgen").resolve("loot_modifiers").resolve("block_drops.json");
        Files.createDirectories(modifier.getParent());
        Files.writeString(modifier, "{\n"
                + "  \"type\": \"irisworldgen:block_drops\",\n"
                + "  \"conditions\": []\n"
                + "}\n", StandardCharsets.UTF_8);
    }

    private static void writePackMeta(Path packDirectory) throws IOException {
        int packFormat = DataVersion.getLatest().getPackFormat();
        String json = "{\n"
                + "  \"pack\": {\n"
                + "    \"description\": \"Iris world generation biomes and dimension types for installed packs.\",\n"
                + "    \"pack_format\": " + packFormat + ",\n"
                + "    \"min_format\": " + packFormat + ",\n"
                + "    \"max_format\": " + packFormat + "\n"
                + "  }\n"
                + "}\n";
        Files.writeString(packDirectory.resolve("pack.mcmeta"), json, StandardCharsets.UTF_8);
    }

    private static int countBiomes(Map<String, KSet<String>> biomes) {
        int count = 0;
        for (KSet<String> values : biomes.values()) {
            count += values.size();
        }
        return count;
    }

    private static void clean(Path packDirectory) throws IOException {
        if (!Files.exists(packDirectory)) {
            return;
        }
        List<Path> entries = new ArrayList<>();
        try (Stream<Path> walk = Files.walk(packDirectory)) {
            walk.sorted(Comparator.comparingInt(Path::getNameCount).reversed()).forEach(entries::add);
        }
        for (Path entry : entries) {
            Files.deleteIfExists(entry);
        }
    }

    static void publishDirectory(Path stagingDirectory, Path publishedDirectory) throws IOException {
        Path backupDirectory = publishedDirectory.resolveSibling(
                publishedDirectory.getFileName() + ".backup-" + UUID.randomUUID());
        boolean hadPublishedDirectory = Files.exists(publishedDirectory);
        if (hadPublishedDirectory) {
            Files.move(publishedDirectory, backupDirectory, StandardCopyOption.ATOMIC_MOVE);
        }
        try {
            Files.move(stagingDirectory, publishedDirectory, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException | RuntimeException | Error failure) {
            if (hadPublishedDirectory) {
                try {
                    Files.move(backupDirectory, publishedDirectory, StandardCopyOption.ATOMIC_MOVE);
                } catch (Throwable rollbackError) {
                    failure.addSuppressed(rollbackError);
                }
            }
            throw failure;
        }
        if (hadPublishedDirectory) {
            try {
                clean(backupDirectory);
            } catch (Throwable cleanupError) {
                ModdedIrisLog.warn("Iris published the forced datapack but could not remove backup {}",
                        backupDirectory, cleanupError);
            }
        }
    }

    private static Path packsRoot() {
        return IrisPlatforms.get().packsFolderNoCreate().toPath();
    }

    private record PublishedState(Path directory, String packsHash) {
    }

    private record HashMemo(String hash, long takenAtNanos) {
    }
}
