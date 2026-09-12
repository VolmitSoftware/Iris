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

import art.arcane.iris.pack.datapack.IrisDatapackCompiler;
import art.arcane.iris.pack.loading.IrisData;
import art.arcane.iris.localization.IrisLanguage;
import art.arcane.iris.localization.RuntimeUiMessages;
import art.arcane.iris.platform.bukkit.nms.datapack.DataVersion;
import art.arcane.iris.platform.bukkit.nms.datapack.IDataFixer;
import art.arcane.iris.pack.PackDirectoryResolver;
import art.arcane.iris.pack.PackValidationResult;
import art.arcane.iris.pack.PackValidator;
import art.arcane.iris.world.history.GenerationEpoch;
import art.arcane.iris.world.history.GenerationHistory;
import art.arcane.iris.world.history.GenerationHistoryPaths;
import art.arcane.iris.world.history.GenerationRegistryContract;
import art.arcane.iris.world.history.GenerationRegistryContract.PhysicalResourceKey;
import art.arcane.iris.world.history.GenerationRegistryContractFactory;
import art.arcane.iris.generation.biome.IrisBiome;
import art.arcane.iris.generation.biome.IrisBiomeCustom;
import art.arcane.iris.generation.biome.IrisCustomBiomeAliasResolver;
import art.arcane.iris.generation.terrain.IrisDimension;
import art.arcane.iris.generation.terrain.IrisDimensionType;
import art.arcane.iris.spi.IrisPlatforms;
import art.arcane.volmlib.util.collection.KList;
import art.arcane.volmlib.util.collection.KSet;
import net.minecraft.network.chat.Component;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.PackSelectionConfig;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.PathPackResources;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraft.server.packs.repository.RepositorySource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.storage.LevelResource;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.stream.Stream;

public final class ModdedForcedDatapack {
    private static final String PACK_ID = "iris_worldgen";
    private static final String PACK_FOLDER = "iris";
    private static final String HASH_FILE_NAME = "packs.hash";
    private static final String HASH_SALT = "iris-forced-datapack-v3-content-addressed-biomes";
    private static final long PACKS_HASH_TTL_NANOS = 2_000_000_000L;
    private static final Object LOCK = new Object();
    private static final AtomicBoolean LOADED = new AtomicBoolean(false);
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
            } else if (hash.isEmpty()) {
                reason = "current registry inputs could not be verified";
            } else {
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
        return requireReadablePack(regenerate());
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

    private static String packsHash() throws IOException {
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException missing) {
            throw new IllegalStateException("SHA-256 is unavailable", missing);
        }
        digest.update((HASH_SALT + '|' + ModdedEngineBootstrap.loader().platformName()
                + '|' + DataVersion.getLatest().getPackFormat() + '\n').getBytes(StandardCharsets.UTF_8));
        String inputFingerprint = IrisDatapackCompiler.computeInputFingerprint(
                compilationPackRoots(),
                List.of(),
                DataVersion.getLatest().get(),
                false
        );
        digest.update(inputFingerprint.getBytes(StandardCharsets.UTF_8));
        return HexFormat.of().formatHex(digest.digest());
    }

    private static void writeStagedPack(Path stagingDirectory) throws IOException {
        Map<String, KSet<String>> seenBiomes = new LinkedHashMap<>();
        IDataFixer fixer = DataVersion.getLatest().get();

        int packCount = 0;
        KList<String> presetIds = new KList<>();
        Path worldRoot = worldRootOrNull();
        Set<PackSelection> legacySelections = unadoptedSelections(worldRoot);
        List<File> packRoots = compilationPackRoots(worldRoot);
        for (File pack : packRoots) {
            PackStageContext context = packStageContext(pack.toPath(), legacySelections);
            if (stagePack(context, fixer, stagingDirectory, seenBiomes, presetIds)) {
                packCount++;
            }
        }

        IrisDatapackCompiler.installRetainedRegistrySources(packRoots, List.of(stagingDirectory.toFile()), fixer);
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

    private static boolean stagePack(PackStageContext context, IDataFixer fixer, Path stagingDirectory,
                                     Map<String, KSet<String>> seenBiomes,
                                     KList<String> presetIds) throws IOException {
        File sourcePack = context.packRoot().toFile();
        PackValidationResult validation;
        try {
            validation = PackValidator.validateForDatapackBootstrap(sourcePack);
        } catch (Throwable validationFailure) {
            if (context.required()) {
                throw requiredPackFailure(context, "validation", validationFailure);
            }
            ModdedIrisLog.error("Iris excluded pack '{}' from Create World because validation failed",
                    sourcePack.getName(), validationFailure);
            rethrowIfUnrecoverable(validationFailure);
            return false;
        }
        if (!validation.isLoadable()) {
            if (context.required()) {
                throw new IOException("Retained Iris generation pack at " + context.packRoot()
                        + " has " + validation.getBlockingErrors().size()
                        + " blocking validation error(s): " + validation.getBlockingErrors().getFirst());
            }
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
            installed = installPack(context, fixer, packFolders, packBiomes, packPresetIds);
        } catch (Throwable installationFailure) {
            if (context.required()) {
                throw requiredPackFailure(context, "datapack serialization", installationFailure);
            }
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

    static void mergeDirectory(Path sourceDirectory, Path destinationDirectory) throws IOException {
        List<Path> entries = new ArrayList<>();
        try (Stream<Path> walk = Files.walk(sourceDirectory)) {
            walk.sorted(Comparator.comparingInt(Path::getNameCount)).forEach(entries::add);
        }
        for (Path source : entries) {
            Path relative = sourceDirectory.relativize(source);
            Path destination = destinationDirectory.resolve(relative);
            if (Files.isDirectory(source)) {
                if (Files.exists(destination) && !Files.isDirectory(destination)) {
                    throw new IOException("Conflicting Iris datapack output at " + destination + ".");
                }
                Files.createDirectories(destination);
            } else if (!Files.exists(destination)) {
                Files.copy(source, destination);
            } else if (!Files.isRegularFile(destination) || Files.mismatch(source, destination) != -1L) {
                throw new IOException("Conflicting Iris datapack output at " + destination + ".");
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

    private static boolean installPack(PackStageContext context, IDataFixer fixer, KList<File> folders,
                                       Map<String, KSet<String>> seenBiomes,
                                       KList<String> presetIds) throws IOException {
        File packFolder = context.packRoot().toFile();
        String packName = context.packName();
        File dimensionsDirectory = new File(packFolder, "dimensions");
        if (!dimensionsDirectory.isDirectory()) {
            return false;
        }
        IrisData data = IrisData.openDatapackCompiler(packFolder);
        try {
            if (context.retainedContract() != null) {
                data.bindGenerationRegistryContract(context.retainedContract());
            }
            String[] dimensionKeys = data.getDimensionLoader().getPossibleKeys();
            if (dimensionKeys == null || dimensionKeys.length == 0) {
                return false;
            }
            int installedDimensions = 0;
            List<String> sortedDimensionKeys = Stream.of(dimensionKeys).sorted().toList();
            for (String dimensionKey : sortedDimensionKeys) {
                if (context.dimensionKey() != null && !context.dimensionKey().equals(dimensionKey)) {
                    continue;
                }
                IrisDimension dimension = data.getDimensionLoader().load(dimensionKey);
                if (dimension == null) {
                    throw new IllegalStateException("Iris pack '" + packName + "' dimension '"
                            + dimensionKey + "' did not load while building the forced datapack");
                }
                dimension.installBiomes(
                        fixer,
                        () -> data,
                        folders,
                        biomesForNamespace(seenBiomes, "iris"),
                        context
                );
                writeDimensionType(
                        folders,
                        fixer,
                        dimension,
                        context.dimensionTypeRef(dimensionKey),
                        context.emitLegacyDimensionTypeAlias(),
                        context
                );
                if (context.emitPresets()) {
                    String presetRef = ModdedWorldgenIds.presetRef(packName, dimensionKey);
                    writeWorldPreset(folders, packName, dimensionKey, presetRef);
                    presetIds.add(presetRef);
                }
                installedDimensions++;
            }
            if (context.required() && installedDimensions == 0) {
                throw new IOException("Retained Iris generation pack at " + context.packRoot()
                        + " does not contain dimension '" + context.dimensionKey() + "'.");
            }
            return installedDimensions > 0;
        } finally {
            data.close();
        }
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
        writeDimensionType(
                folders,
                fixer,
                dimension,
                ModdedWorldgenIds.dimensionTypeRef(pack, packDimensionKey),
                true,
                IrisCustomBiomeAliasResolver.none()
        );
    }

    private static void writeDimensionType(
            KList<File> folders,
            IDataFixer fixer,
            IrisDimension dimension,
            String typeRef,
            boolean emitLegacyAlias,
            IrisCustomBiomeAliasResolver sourceResolver
    ) throws IOException {
        IrisDimensionType type = dimension.getDimensionType();
        String json = Objects.requireNonNull(sourceResolver, "sourceResolver").generatedSource(
                GenerationRegistryContractFactory.DIMENSION_TYPE_REGISTRY,
                typeRef,
                type.toJson(fixer),
                fixer
        );
        String typePath = typeRef.substring(typeRef.indexOf(':') + 1);
        String typeNamespace = typeRef.substring(0, typeRef.indexOf(':'));
        for (File datapackRoot : folders) {
            Path output = datapackRoot.toPath().resolve("data").resolve(typeNamespace)
                    .resolve("dimension_type").resolve(typePath + ".json");
            Files.createDirectories(output.getParent());
            Files.writeString(output, json, StandardCharsets.UTF_8);
            if (!emitLegacyAlias) {
                continue;
            }
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

    private static List<File> compilationPackRoots() throws IOException {
        return compilationPackRoots(worldRootOrNull());
    }

    private static List<File> compilationPackRoots(Path worldRoot) throws IOException {
        Path dataDirectory = packsRoot().getParent();
        Path historyRoot = worldRoot == null
                ? dataDirectory.resolve(".iris-world-unavailable")
                : worldRoot;
        return IrisDatapackCompiler.collectPackRoots(dataDirectory, historyRoot);
    }

    private static Path worldRootOrNull() throws IOException {
        MinecraftServer server = ModdedEngineBootstrap.currentServer();
        if (server != null) {
            return server.getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize();
        }
        if (ModdedEngineBootstrap.loader().clientEnvironment()) {
            return null;
        }
        return MainWorldService.configuredWorldRootIfPresent();
    }

    private static Set<PackSelection> unadoptedSelections(Path worldRoot) throws IOException {
        if (worldRoot == null) {
            return Set.of();
        }
        Set<PackSelection> selections = new LinkedHashSet<>();
        for (ModdedDimensionRegistryStore.PersistentDimension dimension
                : ModdedDimensionRegistryStore.loadWorldRoot(worldRoot)) {
            Identifier identifier = Identifier.tryParse(dimension.id());
            if (identifier == null) {
                throw new IOException("Invalid persistent Iris dimension ID '" + dimension.id() + "'.");
            }
            ResourceKey<Level> key = ResourceKey.create(Registries.DIMENSION, identifier);
            Path dimensionRoot = DimensionType.getStorageFolder(key, worldRoot).toAbsolutePath().normalize();
            Path generationRoot = GenerationHistoryPaths.forDimension(dimensionRoot).generationRoot();
            if (!Files.exists(generationRoot)) {
                selections.add(new PackSelection(dimension.pack(), dimension.dimension()));
            }
        }
        return Set.copyOf(selections);
    }

    private static PackStageContext packStageContext(
            Path packRoot,
            Set<PackSelection> unadoptedSelections
    ) throws IOException {
        Path normalizedRoot = packRoot.toAbsolutePath().normalize();
        RetainedPack retained = retainedPack(normalizedRoot);
        if (retained != null) {
            ModdedWorldgenIds.ScopedDimension identity = ModdedWorldgenIds
                    .scopedDimensionType(retained.epoch().dimensionContract().dimensionTypeKey())
                    .orElseThrow(() -> new IOException("Retained Iris dimension type key is not scoped: "
                            + retained.epoch().dimensionContract().dimensionTypeKey()));
            if (!identity.dimension().equals(retained.epoch().dimensionContract().dimensionKey())) {
                throw new IOException("Retained Iris dimension type identity does not match its epoch contract.");
            }
            return PackStageContext.retained(normalizedRoot, identity.pack(), retained.epoch());
        }

        String packName = normalizedRoot.getFileName().toString();
        Set<String> legacyDimensions = new LinkedHashSet<>();
        for (PackSelection selection : unadoptedSelections) {
            if (selection.pack().equals(packName)) {
                legacyDimensions.add(selection.dimension());
            }
        }
        return PackStageContext.installed(normalizedRoot, packName, legacyDimensions);
    }

    private static RetainedPack retainedPack(Path packRoot) throws IOException {
        Path epochRoot = packRoot.getParent();
        Path epochsRoot = epochRoot == null ? null : epochRoot.getParent();
        Path generationRoot = epochsRoot == null ? null : epochsRoot.getParent();
        Path irisRoot = generationRoot == null ? null : generationRoot.getParent();
        Path dimensionRoot = irisRoot == null ? null : irisRoot.getParent();
        if (epochRoot == null
                || epochsRoot == null
                || generationRoot == null
                || irisRoot == null
                || dimensionRoot == null
                || !"pack".equals(packRoot.getFileName().toString())
                || !GenerationHistoryPaths.EPOCHS_DIRECTORY_NAME.equals(epochsRoot.getFileName().toString())
                || !GenerationHistoryPaths.GENERATION_DIRECTORY_NAME.equals(generationRoot.getFileName().toString())
                || !GenerationHistoryPaths.IRIS_DIRECTORY_NAME.equals(irisRoot.getFileName().toString())) {
            return null;
        }
        GenerationHistory history = GenerationHistory.open(dimensionRoot);
        String epochId = epochRoot.getFileName().toString();
        GenerationEpoch epoch = history.manifest().epoch(epochId).orElseThrow(
                () -> new IOException("Retained Iris pack is not referenced by generation history: " + packRoot)
        );
        if (!history.paths().packRoot(epochId).toAbsolutePath().normalize().equals(packRoot)) {
            throw new IOException("Retained Iris pack path does not match generation history: " + packRoot);
        }
        return new RetainedPack(history, epoch);
    }

    private static IOException requiredPackFailure(
            PackStageContext context,
            String operation,
            Throwable failure
    ) {
        if (failure instanceof IOException ioFailure) {
            return ioFailure;
        }
        return new IOException("Retained Iris generation pack at " + context.packRoot()
                + " failed " + operation + ".", failure);
    }

    private static Path packsRoot() {
        return IrisPlatforms.get().packsFolderNoCreate().toPath();
    }

    private record PackSelection(String pack, String dimension) {
    }

    private record RetainedPack(GenerationHistory history, GenerationEpoch epoch) {
    }

    private record PackStageContext(
            Path packRoot,
            String packName,
            String dimensionKey,
            String retainedDimensionTypeRef,
            boolean emitPresets,
            boolean emitLegacyDimensionTypeAlias,
            boolean required,
            Set<String> legacyDimensions,
            GenerationRegistryContract retainedContract
    ) implements IrisCustomBiomeAliasResolver {
        private static PackStageContext installed(
                Path packRoot,
                String packName,
                Set<String> legacyDimensions
        ) {
            return new PackStageContext(
                    packRoot,
                    packName,
                    null,
                    null,
                    true,
                    true,
                    false,
                    Set.copyOf(legacyDimensions),
                    null
            );
        }

        private static PackStageContext retained(
                Path packRoot,
                String packName,
                GenerationEpoch epoch
        ) {
            return new PackStageContext(
                    packRoot,
                    packName,
                    epoch.dimensionContract().dimensionKey(),
                    epoch.dimensionContract().dimensionTypeKey(),
                    false,
                    true,
                    true,
                    Set.of(),
                    epoch.registryContract()
            );
        }

        private String dimensionTypeRef(String installedDimensionKey) {
            return retainedDimensionTypeRef == null
                    ? ModdedWorldgenIds.dimensionTypeRef(packName, installedDimensionKey)
                    : retainedDimensionTypeRef;
        }

        @Override
        public String physicalResourceKey(
                IrisDimension dimension,
                IrisBiome biome,
                IrisBiomeCustom customBiome,
                String currentPhysicalResourceKey
        ) throws IOException {
            if (retainedContract == null) {
                return currentPhysicalResourceKey;
            }
            return dimension.getLoader().customBiomeResourceKey(dimension, customBiome);
        }

        @Override
        public List<String> aliases(
                IrisDimension dimension,
                IrisBiome biome,
                IrisBiomeCustom customBiome,
                String physicalResourceKey
        ) {
            List<String> candidates = ModdedWorldgenIds.legacyBiomeRefs(
                    packName,
                    dimension.getLoadKey(),
                    customBiome.getId()
            );
            if (legacyDimensions.contains(dimension.getLoadKey())) {
                return candidates;
            }
            if (retainedContract == null) {
                return List.of();
            }
            List<String> aliases = new ArrayList<>(candidates.size());
            for (String candidate : candidates) {
                PhysicalResourceKey key = new PhysicalResourceKey(
                        GenerationRegistryContractFactory.BIOME_REGISTRY,
                        candidate
                );
                if (retainedContract.definitions().containsKey(key)) {
                    aliases.add(candidate);
                }
            }
            return List.copyOf(aliases);
        }

        @Override
        public String generatedSource(
                String registryKey,
                String resourceKey,
                String currentSource,
                IDataFixer fixer
        ) throws IOException {
            if (retainedContract == null) {
                return currentSource;
            }
            PhysicalResourceKey key = new PhysicalResourceKey(registryKey, resourceKey);
            return GenerationRegistryContractFactory.renderGeneratedSource(retainedContract, key, fixer);
        }
    }

    private record PublishedState(Path directory, String packsHash) {
    }

    private record HashMemo(String hash, long takenAtNanos) {
    }
}
