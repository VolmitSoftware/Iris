package art.arcane.iris.core.runtime;

import art.arcane.iris.spi.CapabilityProbe;
import art.arcane.iris.spi.IrisLogging;
import art.arcane.iris.spi.IrisServices;
import art.arcane.iris.core.IrisSettings;
import art.arcane.iris.core.lifecycle.CapabilitySnapshot;
import art.arcane.iris.core.lifecycle.ServerFamily;
import art.arcane.iris.core.lifecycle.WorldLifecycleService;
import art.arcane.iris.core.service.BoardSVC;
import art.arcane.iris.core.tools.IrisToolbelt;
import art.arcane.iris.engine.platform.BukkitChunkGenerator;
import art.arcane.iris.engine.platform.PlatformChunkGenerator;
import art.arcane.iris.platform.bukkit.BukkitPlatform;
import art.arcane.iris.util.common.scheduling.J;
import io.papermc.lib.PaperLib;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.GameMode;
import org.bukkit.GameRule;
import org.bukkit.HeightMap;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Waterlogged;
import org.bukkit.entity.Player;
import org.bukkit.event.world.TimeSkipEvent;
import org.bukkit.plugin.PluginManager;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.VoxelShape;

import java.lang.reflect.Method;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

public final class WorldRuntimeControlService {
    private static final int MAX_SAFE_ENTRY_HORIZONTAL_RADIUS = 15;
    private static final int SAFE_ENTRY_UPWARD_ALLOWANCE = 32;
    private static final double BLOCK_CENTER = 0.5D;
    private static final double COLLISION_EPSILON = 0.000001D;
    private static final Set<Material> UNSAFE_ENTRY_MATERIALS = Set.of(
            Material.CACTUS,
            Material.CAMPFIRE,
            Material.COBWEB,
            Material.END_GATEWAY,
            Material.END_PORTAL,
            Material.FIRE,
            Material.MAGMA_BLOCK,
            Material.NETHER_PORTAL,
            Material.POINTED_DRIPSTONE,
            Material.POWDER_SNOW,
            Material.SOUL_CAMPFIRE,
            Material.SOUL_FIRE,
            Material.SWEET_BERRY_BUSH,
            Material.WITHER_ROSE
    );
    private static volatile WorldRuntimeControlService instance;

    private final CapabilitySnapshot capabilities;
    private final WorldRuntimeControlBackend backend;
    private final String capabilityDescription;

    private WorldRuntimeControlService(CapabilitySnapshot capabilities) {
        this.capabilities = capabilities;
        this.backend = selectBackend(capabilities);
        this.capabilityDescription = "family=" + capabilities.serverFamily().id()
                + ", backend=" + backend.backendName()
                + ", " + backend.describeCapabilities();
    }

    public static void reset() {
        synchronized (WorldRuntimeControlService.class) {
            instance = null;
        }
    }

    public static WorldRuntimeControlService get() {
        WorldRuntimeControlService current = instance;
        if (current != null) {
            return current;
        }

        synchronized (WorldRuntimeControlService.class) {
            if (instance != null) {
                return instance;
            }

            CapabilitySnapshot capabilities = WorldLifecycleService.get().capabilities();
            instance = new WorldRuntimeControlService(capabilities);
            IrisLogging.info("WorldRuntimeControl capabilities: %s", instance.capabilityDescription);
            return instance;
        }
    }

    public String backendName() {
        return backend.backendName();
    }

    public String capabilityDescription() {
        return capabilityDescription;
    }

    public OptionalLong readDayTime(World world) {
        return backend.readDayTime(world);
    }

    public boolean applyStudioWorldRules(World world) {
        if (world == null) {
            return false;
        }

        IrisServices.get(art.arcane.iris.core.link.MultiverseCoreLink.class).removeIfPresent(world);
        setIntGameRule(world, 0, "SPAWN_CHUNK_RADIUS", "spawnChunkRadius");
        enableStudioEntitySpawning(world);
        if (!IrisSettings.get().getStudio().isDisableTimeAndWeather()) {
            return true;
        }

        setBooleanGameRule(world, false, "ADVANCE_WEATHER", "DO_WEATHER_CYCLE", "WEATHER_CYCLE", "doWeatherCycle", "weatherCycle");
        setBooleanGameRule(world, false, "ADVANCE_TIME", "DO_DAYLIGHT_CYCLE", "DAYLIGHT_CYCLE", "doDaylightCycle", "daylightCycle");
        applyNoonTimeLock(world);
        return true;
    }

    public boolean applyObjectStudioWorldRules(World world) {
        if (world == null) {
            return false;
        }

        applyStudioWorldRules(world);

        setBooleanGameRule(world, false, "DO_FIRE_TICK", "doFireTick");
        setBooleanGameRule(world, false, "DO_MOB_LOOT", "doMobLoot");
        setBooleanGameRule(world, true, "DO_IMMEDIATE_RESPAWN", "doImmediateRespawn");
        setBooleanGameRule(world, false, "FALL_DAMAGE", "fallDamage");
        setBooleanGameRule(world, false, "FIRE_DAMAGE", "fireDamage");
        setBooleanGameRule(world, false, "DROWNING_DAMAGE", "drowningDamage");
        setBooleanGameRule(world, false, "FREEZE_DAMAGE", "freezeDamage");
        setBooleanGameRule(world, false, "MOB_GRIEFING", "mobGriefing");
        setBooleanGameRule(world, false, "DO_TILE_DROPS", "doTileDrops");
        setBooleanGameRule(world, true, "KEEP_INVENTORY", "keepInventory");
        setIntGameRule(world, 0, "RANDOM_TICK_SPEED", "randomTickSpeed");
        setIntGameRule(world, 0, "SPAWN_RADIUS", "spawnRadius");
        setIntGameRule(world, 0, "MAX_ENTITY_CRAMMING", "maxEntityCramming");
        applyNoonTimeLock(world);
        return true;
    }

    static void enableStudioEntitySpawning(World world) {
        setBooleanGameRule(world, true, "DO_MOB_SPAWNING", "doMobSpawning");
        setBooleanGameRule(world, true, "DO_TRADER_SPAWNING", "doTraderSpawning");
        setBooleanGameRule(world, true, "DO_PATROL_SPAWNING", "doPatrolSpawning");
        setBooleanGameRule(world, true, "DO_INSOMNIA", "doInsomnia");
        setBooleanGameRule(world, true, "DO_WARDEN_SPAWNING", "doWardenSpawning");
    }

    public boolean applyNoonTimeLock(World world) {
        if (world == null) {
            return false;
        }

        if (!hasMutableClock(world)) {
            return false;
        }

        OptionalLong currentTime = readDayTime(world);
        if (currentTime.isEmpty()) {
            return false;
        }

        long skipAmount = (6000L - currentTime.getAsLong()) % 24000L;
        if (skipAmount < 0L) {
            skipAmount += 24000L;
        }

        long effectiveSkip = skipAmount;
        if (TIME_SKIP_EVENT_AVAILABLE) {
            long fired = fireTimeSkipEvent(world, skipAmount);
            if (fired == TIME_SKIP_CANCELLED) {
                return false;
            }
            effectiveSkip = fired;
        }

        try {
            boolean written = backend.writeDayTime(world, currentTime.getAsLong() + effectiveSkip);
            if (!written) {
                return false;
            }
            backend.syncTime(world);
            return true;
        } catch (Throwable e) {
            IrisLogging.reportError("Failed to lock the clock of world \"" + world.getName()
                    + "\" to noon; the world keeps its own time.", e);
            return false;
        }
    }

    private static final long TIME_SKIP_CANCELLED = Long.MIN_VALUE;
    private static final boolean TIME_SKIP_EVENT_AVAILABLE = probeTimeSkipEvent();

    private static boolean probeTimeSkipEvent() {
        try {
            Class.forName("org.bukkit.event.world.TimeSkipEvent$SkipReason");
            return true;
        } catch (Throwable e) {
            return false;
        }
    }

    private long fireTimeSkipEvent(World world, long skipAmount) {
        try {
            TimeSkipEvent event = new TimeSkipEvent(world, TimeSkipEvent.SkipReason.CUSTOM, skipAmount);
            PluginManager pluginManager = Bukkit.getPluginManager();
            if (pluginManager != null) {
                pluginManager.callEvent(event);
            }
            if (event.isCancelled()) {
                return TIME_SKIP_CANCELLED;
            }
            return event.getSkipAmount();
        } catch (Throwable e) {
            return skipAmount;
        }
    }

    public CompletableFuture<Chunk> requestChunkAsync(World world, int chunkX, int chunkZ, boolean generate) {
        return backend.requestChunkAsync(world, chunkX, chunkZ, generate);
    }

    public CompletableFuture<Chunk> requestChunkAsync(
            World world,
            int chunkX,
            int chunkZ,
            boolean generate,
            boolean urgent
    ) {
        return backend.requestChunkAsync(world, chunkX, chunkZ, generate, urgent);
    }

    public void prepareGenerator(World world) {
        if (world == null) {
            return;
        }

        try {
            art.arcane.iris.engine.platform.PlatformChunkGenerator provider = art.arcane.iris.core.tools.IrisToolbelt.access(world);
            if (provider == null) {
                return;
            }

            art.arcane.iris.engine.framework.Engine engine = provider.getEngine();
            if (engine == null) {
                return;
            }

            engine.getMantle().getComponents();
            engine.getMantle().getRealRadius();
        } catch (Throwable e) {
            IrisLogging.reportError("Failed to prepare generator state for world \"" + world.getName() + "\".", e);
        }
    }

    public Location resolveEntryAnchor(World world) {
        if (world == null) {
            return null;
        }

        PlatformChunkGenerator provider = IrisToolbelt.access(world);
        return resolveEntryAnchor(world, provider);
    }

    static Location resolveEntryAnchor(World world, PlatformChunkGenerator provider) {
        if (world == null) {
            return null;
        }

        if (provider != null && provider.isStudio() && provider instanceof BukkitChunkGenerator bukkitProvider) {
            Location initialSpawn = bukkitProvider.getInitialSpawnLocation(world);
            if (initialSpawn != null) {
                return initialSpawn.clone();
            }
        }

        Location spawnLocation = world.getSpawnLocation();
        if (spawnLocation != null) {
            return spawnLocation.clone();
        }

        int minY = world.getMinHeight() + 1;
        int y = Math.max(minY, 96);
        return new Location(world, 0.5D, y, 0.5D);
    }

    public CompletableFuture<Location> resolveSafeEntry(World world, Location source) {
        if (world == null || source == null) {
            return CompletableFuture.completedFuture(null);
        }

        int chunkX = source.getBlockX() >> 4;
        int chunkZ = source.getBlockZ() >> 4;
        CompletableFuture<Location> future = new CompletableFuture<>();
        boolean scheduled = J.runRegion(world, chunkX, chunkZ, () -> {
            try {
                future.complete(findTopSafeLocationWithTicket(world, source, chunkX, chunkZ));
            } catch (Throwable t) {
                future.completeExceptionally(t);
            }
        });
        if (!scheduled) {
            future.completeExceptionally(new IllegalStateException(
                    "Failed to schedule safe-entry resolve for " + world.getName() + "@" + chunkX + "," + chunkZ + "."));
        }
        return future;
    }

    private static Location findTopSafeLocationWithTicket(
            World world,
            Location source,
            int chunkX,
            int chunkZ
    ) {
        boolean ticketAdded = world.addPluginChunkTicket(chunkX, chunkZ, BukkitPlatform.plugin());
        try {
            return findTopSafeLocation(world, source);
        } finally {
            if (ticketAdded) {
                world.removePluginChunkTicket(chunkX, chunkZ, BukkitPlatform.plugin());
            }
        }
    }

    public CompletableFuture<Boolean> teleport(Player player, Location location) {
        return scheduleTeleport(player, location, null);
    }

    public CompletableFuture<Boolean> teleportInMode(
            Player player,
            Location location,
            GameMode gameMode
    ) {
        return scheduleTeleport(
                player,
                location,
                Objects.requireNonNull(gameMode, "Teleport game mode"));
    }

    static CompletableFuture<Boolean> scheduleTeleport(
            Player player,
            Location location,
            GameMode gameMode
    ) {
        return scheduleTeleport(player, location, gameMode, PaperLib::teleportAsync);
    }

    static CompletableFuture<Boolean> scheduleTeleport(
            Player player,
            Location location,
            GameMode gameMode,
            TeleportExecutor teleporter
    ) {
        if (player == null || location == null) {
            return CompletableFuture.completedFuture(false);
        }
        Objects.requireNonNull(teleporter, "teleporter");

        CompletableFuture<Boolean> future = new CompletableFuture<>();
        GameModeRestore modeRestore = new GameModeRestore(player);
        AtomicReference<CompletableFuture<Boolean>> activeTeleport = new AtomicReference<>();
        future.whenComplete((success, failure) -> {
            if (Boolean.TRUE.equals(success)) {
                return;
            }
            CompletableFuture<Boolean> teleport = activeTeleport.get();
            if (teleport != null && !teleport.isDone()) {
                teleport.cancel(false);
            }
            modeRestore.restore();
        });
        boolean scheduled = J.runEntity(player, () -> {
            try {
                if (future.isDone()) {
                    return;
                }
                if (gameMode != null) {
                    modeRestore.apply(gameMode);
                    if (future.isDone()) {
                        modeRestore.restore();
                        return;
                    }
                }
                CompletableFuture<Boolean> teleportFuture = teleporter.teleport(player, location);
                if (teleportFuture == null) {
                    future.complete(false);
                    return;
                }
                activeTeleport.set(teleportFuture);
                if (future.isDone()) {
                    teleportFuture.cancel(false);
                    modeRestore.restore();
                    return;
                }

                teleportFuture.whenComplete((success, throwable) -> {
                    if (throwable != null) {
                        future.completeExceptionally(throwable);
                        return;
                    }

                    if (Boolean.TRUE.equals(success)) {
                        if (future.complete(true)) {
                            J.runEntity(player, () -> IrisServices.get(BoardSVC.class).updatePlayer(player));
                        }
                        return;
                    }

                    future.complete(false);
                });
            } catch (Throwable t) {
                future.completeExceptionally(t);
            }
        });
        if (!scheduled) {
            future.completeExceptionally(new IllegalStateException("Failed to schedule teleport for " + player.getName() + "."));
        }

        return future;
    }

    public boolean hasMutableClock(World world) {
        try {
            Object handle = invokeNoArg(world, "getHandle");
            if (handle == null) {
                return false;
            }

            Object dimensionTypeHolder = invokeNoArg(handle, "dimensionTypeRegistration");
            Object dimensionType = unwrapDimensionType(dimensionTypeHolder);
            if (dimensionType == null) {
                return false;
            }

            return !dimensionTypeHasFixedTime(dimensionType);
        } catch (Throwable e) {
            return false;
        }
    }

    private static WorldRuntimeControlBackend selectBackend(CapabilitySnapshot capabilities) {
        ServerFamily family = capabilities.serverFamily();
        if (family.isPaperLike()) {
            return new PaperLikeRuntimeControlBackend(capabilities);
        }

        return new BukkitPublicRuntimeControlBackend(capabilities);
    }

    static Location findTopSafeLocation(World world, Location source) {
        int sourceX = source.getBlockX();
        int sourceZ = source.getBlockZ();
        float yaw = source.getYaw();
        float pitch = source.getPitch();
        int chunkX = sourceX >> 4;
        int chunkZ = sourceZ >> 4;
        if (!world.isChunkLoaded(chunkX, chunkZ)) {
            return null;
        }

        int minimumFloorY = world.getMinHeight();
        int maximumFloorY = world.getMaxHeight() - 3;
        if (minimumFloorY > maximumFloorY) {
            return null;
        }

        int minimumX = chunkX << 4;
        int minimumZ = chunkZ << 4;
        int maximumX = minimumX + 15;
        int maximumZ = minimumZ + 15;
        for (int radius = 0; radius <= MAX_SAFE_ENTRY_HORIZONTAL_RADIUS; radius++) {
            for (int offsetX = -radius; offsetX <= radius; offsetX++) {
                for (int offsetZ = -radius; offsetZ <= radius; offsetZ++) {
                    if (Math.max(Math.abs(offsetX), Math.abs(offsetZ)) != radius) {
                        continue;
                    }

                    int x = sourceX + offsetX;
                    int z = sourceZ + offsetZ;
                    if (x < minimumX || x > maximumX || z < minimumZ || z > maximumZ) {
                        continue;
                    }

                    Location safeLocation = findSafeLocationInColumn(
                            world,
                            x,
                            z,
                            minimumFloorY,
                            maximumFloorY,
                            source.getBlockY() - 1,
                            yaw,
                            pitch
                    );
                    if (safeLocation != null) {
                        return safeLocation;
                    }
                }
            }
        }

        return null;
    }

    private static Location findSafeLocationInColumn(
            World world,
            int x,
            int z,
            int minimumFloorY,
            int maximumFloorY,
            int preferredFloorY,
            float yaw,
            float pitch
    ) {
        int highestY = world.getHighestBlockYAt(x, z, HeightMap.MOTION_BLOCKING_NO_LEAVES);
        int preferredMaximumY = Math.min(maximumFloorY, preferredFloorY + SAFE_ENTRY_UPWARD_ALLOWANCE);
        int startingFloorY = Math.max(minimumFloorY, Math.min(preferredMaximumY, highestY));
        for (int floorY = startingFloorY; floorY >= minimumFloorY; floorY--) {
            Block floor = world.getBlockAt(x, floorY, z);
            if (!isSafeFloor(floor)) {
                continue;
            }

            Block feet = world.getBlockAt(x, floorY + 1, z);
            Block head = world.getBlockAt(x, floorY + 2, z);
            if (isClearEntryBlock(feet) && isClearEntryBlock(head)) {
                return new Location(world, x + BLOCK_CENTER, floorY + 1D, z + BLOCK_CENTER, yaw, pitch);
            }
        }

        return null;
    }

    private static boolean isSafeFloor(Block block) {
        Material material = block.getType();
        if (material == null
                || isAir(material)
                || material.name().endsWith("_LEAVES")
                || UNSAFE_ENTRY_MATERIALS.contains(material)
                || block.isLiquid()
                || block.isPassable()
                || isWaterlogged(block)) {
            return false;
        }

        VoxelShape collisionShape = block.getCollisionShape();
        if (collisionShape == null) {
            return false;
        }

        for (BoundingBox boundingBox : collisionShape.getBoundingBoxes()) {
            if (boundingBox.getMinX() <= BLOCK_CENTER
                    && boundingBox.getMaxX() >= BLOCK_CENTER
                    && boundingBox.getMinZ() <= BLOCK_CENTER
                    && boundingBox.getMaxZ() >= BLOCK_CENTER
                    && boundingBox.getMaxY() > COLLISION_EPSILON
                    && boundingBox.getMaxY() <= 1D + COLLISION_EPSILON) {
                return true;
            }
        }

        return false;
    }

    private static boolean isClearEntryBlock(Block block) {
        Material material = block.getType();
        if (material == null
                || block.isLiquid()
                || isWaterlogged(block)
                || UNSAFE_ENTRY_MATERIALS.contains(material)
                || !block.isPassable()) {
            return false;
        }

        VoxelShape collisionShape = block.getCollisionShape();
        return collisionShape != null && collisionShape.getBoundingBoxes().isEmpty();
    }

    private static boolean isAir(Material material) {
        return material == Material.AIR || material == Material.CAVE_AIR || material == Material.VOID_AIR;
    }

    private static boolean isWaterlogged(Block block) {
        BlockData blockData = block.getBlockData();
        return blockData instanceof Waterlogged waterlogged && waterlogged.isWaterlogged();
    }

    @SuppressWarnings("unchecked")
    private static void setBooleanGameRule(World world, boolean value, String... names) {
        GameRule<Boolean> gameRule = resolveBooleanGameRule(world, names);
        if (gameRule != null) {
            world.setGameRule(gameRule, value);
        }
    }

    @SuppressWarnings("unchecked")
    private static void setIntGameRule(World world, int value, String... names) {
        GameRule<Integer> gameRule = resolveIntGameRule(world, names);
        if (gameRule != null) {
            world.setGameRule(gameRule, value);
        }
    }

    @SuppressWarnings("unchecked")
    private static GameRule<Integer> resolveIntGameRule(World world, String... names) {
        if (world == null || names == null || names.length == 0) {
            return null;
        }

        Set<String> candidates = buildRuleNameCandidates(names);
        for (String name : candidates) {
            if (name == null || name.isBlank()) {
                continue;
            }

            Object declared = CapabilityProbe.attempt("GameRule." + name,
                    () -> GameRule.class.getField(name).get(null), null);
            if (declared instanceof GameRule<?> gameRule && Integer.class.equals(gameRule.getType())) {
                return (GameRule<Integer>) gameRule;
            }

            GameRule<?> byName = CapabilityProbe.attempt("GameRule#getByName(" + name + ")",
                    () -> GameRule.getByName(name), null);
            if (byName != null && Integer.class.equals(byName.getType())) {
                return (GameRule<Integer>) byName;
            }
        }

        String[] availableRules = world.getGameRules();
        if (availableRules == null || availableRules.length == 0) {
            return null;
        }

        Set<String> normalizedCandidates = new LinkedHashSet<>();
        for (String candidate : candidates) {
            if (candidate != null && !candidate.isBlank()) {
                normalizedCandidates.add(normalizeRuleName(candidate));
            }
        }

        for (String availableRule : availableRules) {
            String normalizedAvailable = normalizeRuleName(availableRule);
            if (!normalizedCandidates.contains(normalizedAvailable)) {
                continue;
            }

            GameRule<?> byName = CapabilityProbe.attempt("GameRule#getByName(" + availableRule + ")",
                    () -> GameRule.getByName(availableRule), null);
            if (byName != null && Integer.class.equals(byName.getType())) {
                return (GameRule<Integer>) byName;
            }
        }

        return null;
    }

    @SuppressWarnings("unchecked")
    private static GameRule<Boolean> resolveBooleanGameRule(World world, String... names) {
        if (world == null || names == null || names.length == 0) {
            return null;
        }

        Set<String> candidates = buildRuleNameCandidates(names);
        for (String name : candidates) {
            if (name == null || name.isBlank()) {
                continue;
            }

            Object declared = CapabilityProbe.attempt("GameRule." + name,
                    () -> GameRule.class.getField(name).get(null), null);
            if (declared instanceof GameRule<?> gameRule && Boolean.class.equals(gameRule.getType())) {
                return (GameRule<Boolean>) gameRule;
            }

            GameRule<?> byName = CapabilityProbe.attempt("GameRule#getByName(" + name + ")",
                    () -> GameRule.getByName(name), null);
            if (byName != null && Boolean.class.equals(byName.getType())) {
                return (GameRule<Boolean>) byName;
            }
        }

        String[] availableRules = world.getGameRules();
        if (availableRules == null || availableRules.length == 0) {
            return null;
        }

        Set<String> normalizedCandidates = new LinkedHashSet<>();
        for (String candidate : candidates) {
            if (candidate != null && !candidate.isBlank()) {
                normalizedCandidates.add(normalizeRuleName(candidate));
            }
        }

        for (String availableRule : availableRules) {
            String normalizedAvailable = normalizeRuleName(availableRule);
            if (!normalizedCandidates.contains(normalizedAvailable)) {
                continue;
            }

            GameRule<?> byName = CapabilityProbe.attempt("GameRule#getByName(" + availableRule + ")",
                    () -> GameRule.getByName(availableRule), null);
            if (byName != null && Boolean.class.equals(byName.getType())) {
                return (GameRule<Boolean>) byName;
            }
        }

        return null;
    }

    private static Set<String> buildRuleNameCandidates(String... names) {
        Set<String> candidates = new LinkedHashSet<>();
        for (String name : names) {
            if (name == null || name.isBlank()) {
                continue;
            }

            candidates.add(name);
            candidates.add(name.toUpperCase());
            candidates.add(name.toLowerCase());
        }

        return candidates;
    }

    private static String normalizeRuleName(String name) {
        if (name == null) {
            return "";
        }

        StringBuilder builder = new StringBuilder(name.length());
        for (int i = 0; i < name.length(); i++) {
            char current = name.charAt(i);
            if (Character.isLetterOrDigit(current)) {
                builder.append(Character.toLowerCase(current));
            }
        }
        return builder.toString();
    }

    private static boolean dimensionTypeHasFixedTime(Object dimensionType) throws ReflectiveOperationException {
        Object fixedTimeFlag;
        try {
            fixedTimeFlag = invokeNoArg(dimensionType, "hasFixedTime");
        } catch (NoSuchMethodException ignored) {
            Object fixedTime = invokeNoArg(dimensionType, "fixedTime");
            if (fixedTime instanceof OptionalLong optionalLong) {
                return optionalLong.isPresent();
            }
            if (fixedTime instanceof Optional<?> optional) {
                return optional.isPresent();
            }
            return false;
        }

        return fixedTimeFlag instanceof Boolean && (Boolean) fixedTimeFlag;
    }

    private static Object unwrapDimensionType(Object dimensionTypeHolder) throws ReflectiveOperationException {
        if (dimensionTypeHolder == null) {
            return null;
        }

        Class<?> holderClass = dimensionTypeHolder.getClass();
        if (holderClass.getName().startsWith("net.minecraft.world.level.dimension.")) {
            return dimensionTypeHolder;
        }

        Method valueMethod = holderClass.getMethod("value");
        return valueMethod.invoke(dimensionTypeHolder);
    }

    private static Object invokeNoArg(Object instance, String methodName) throws ReflectiveOperationException {
        Method method = instance.getClass().getMethod(methodName);
        return method.invoke(instance);
    }

    @FunctionalInterface
    interface TeleportExecutor {
        CompletableFuture<Boolean> teleport(Player player, Location location);
    }

    private static final class GameModeRestore {
        private final Player player;
        private final AtomicBoolean changed;
        private final AtomicBoolean restored;
        private GameMode previousMode;

        private GameModeRestore(Player player) {
            this.player = player;
            changed = new AtomicBoolean(false);
            restored = new AtomicBoolean(false);
        }

        private void apply(GameMode targetMode) {
            GameMode currentMode = player.getGameMode();
            if (currentMode == targetMode) {
                return;
            }
            previousMode = currentMode;
            changed.set(true);
            player.setGameMode(targetMode);
        }

        private void restore() {
            if (!changed.get() || !restored.compareAndSet(false, true)) {
                return;
            }
            Runnable restoration = () -> {
                try {
                    player.setGameMode(previousMode);
                } catch (Throwable failure) {
                    IrisLogging.reportError("Failed to restore a player's game mode after an unsuccessful teleport.", failure);
                }
            };
            if (!J.runEntity(player, restoration)) {
                IrisLogging.reportError(
                        "Failed to restore a player's game mode after an unsuccessful teleport.",
                        new IllegalStateException("The player entity scheduler rejected the game-mode restoration."));
            }
        }
    }
}
