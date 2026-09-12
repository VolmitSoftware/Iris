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

import art.arcane.iris.pack.loading.IrisData;
import art.arcane.iris.generation.runtime.Engine;
import art.arcane.iris.structure.placement.LootResolver;
import art.arcane.iris.generation.decoration.tree.TreeBlockMaterial;
import art.arcane.iris.generation.biome.IrisBiome;
import art.arcane.iris.generation.block.IrisBlockData;
import art.arcane.iris.world.loot.IrisBlockDrops;
import art.arcane.iris.world.loot.IrisLoot;
import art.arcane.iris.world.entity.IrisMarker;
import art.arcane.iris.generation.terrain.IrisRegion;
import art.arcane.iris.spi.PlatformBlockState;
import art.arcane.iris.modded.service.ModdedTreeFellerService;
import art.arcane.volmlib.util.collection.KList;
import art.arcane.volmlib.util.math.RNG;
import art.arcane.volmlib.util.matter.MatterMarker;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

public final class ModdedBlockBreakHandler {
    private static final ConcurrentHashMap<BreakKey, PendingBreak> PENDING = new ConcurrentHashMap<>();

    private ModdedBlockBreakHandler() {
    }

    public static void prepare(
            ServerLevel level,
            ServerPlayer player,
            BlockPos position,
            BlockState brokenState
    ) {
        if (ModdedTreeFellerService.isBreakProbe()) {
            return;
        }
        if (engineFor(level) == null) {
            return;
        }
        ModdedScheduler scheduler = ModdedEngineBootstrap.schedulerOrNull();
        if (scheduler == null) {
            // finishPending is the only thing that evicts an unconsumed entry. With no scheduler there is no
            // sweep, so an entry inserted here would leak for the rest of the server uptime.
            ModdedIrisLog.debug("Iris skipped block-break provenance at {},{},{}: scheduler unavailable",
                    position.getX(), position.getY(), position.getZ());
            return;
        }
        BreakKey key = new BreakKey(level, position.asLong());
        ModdedTreeFellerService treeFeller = treeFellerService();
        ModdedTreeFellerService.PreparedOrigin preparedOrigin = treeFeller == null
                ? null
                : treeFeller.prepare(level, player, position, brokenState);
        PendingBreak pending = new PendingBreak(brokenState, preparedOrigin);
        PENDING.put(key, pending);
        scheduler.laterGlobal(() -> finishPending(key, pending, position), 1);
    }

    public static void clear() {
        PENDING.clear();
    }

    public static void cancel(ServerLevel level, BlockPos position) {
        PENDING.remove(new BreakKey(level, position.asLong()));
    }

    public static Result complete(ServerLevel level, BlockPos position, BlockState fallbackState) {
        BreakKey key = new BreakKey(level, position.asLong());
        PendingBreak pending = PENDING.remove(key);
        PendingBreak resolved = pending == null ? new PendingBreak(fallbackState, null) : pending;
        return evaluateSafely(level, position, resolved);
    }

    public static Result completePrepared(ServerLevel level, BlockPos position) {
        BreakKey key = new BreakKey(level, position.asLong());
        PendingBreak pending = PENDING.get(key);
        if (pending == null || level.getBlockState(position).equals(pending.brokenState())) {
            return null;
        }
        return PENDING.remove(key, pending) ? evaluateSafely(level, position, pending) : null;
    }

    public static void spawn(ServerLevel level, BlockPos position, KList<ItemStack> drops) {
        for (ItemStack stack : drops) {
            ItemEntity item = createDrop(level, position, stack);
            if (item != null) {
                level.addFreshEntity(item);
            }
        }
    }

    public static ItemEntity createDrop(ServerLevel level, BlockPos position, ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return null;
        }
        RandomSource random = level.getRandom();
        double x = position.getX() + 0.5D + Mth.nextDouble(random, -0.25D, 0.25D);
        double y = position.getY() + 0.5D + Mth.nextDouble(random, -0.25D, 0.25D) - EntityTypes.ITEM.getHeight() / 2D;
        double z = position.getZ() + 0.5D + Mth.nextDouble(random, -0.25D, 0.25D);
        ItemEntity item = new ItemEntity(level, x, y, z, stack);
        item.setDefaultPickUpDelay();
        return item;
    }

    private static void finishPending(BreakKey key, PendingBreak pending, BlockPos position) {
        if (!PENDING.remove(key, pending)) {
            return;
        }
        ServerLevel level = key.level();
        if (level.getBlockState(position).equals(pending.brokenState())) {
            return;
        }
        Result result = evaluateSafely(level, position, pending);
        if (!result.routeCombinedDrops(List.of())) {
            spawn(level, position, result.drops());
        }
    }

    private static Result evaluateSafely(ServerLevel level, BlockPos position, PendingBreak pending) {
        try {
            return evaluate(level, position, pending);
        } catch (Throwable error) {
            ModdedIrisLog.error("Iris block-break processing failed at {},{},{} in {}", position.getX(), position.getY(), position.getZ(),
                    level.dimension().identifier(), error);
            return Result.empty();
        }
    }

    private static Result evaluate(ServerLevel level, BlockPos position, PendingBreak pending) {
        Engine engine = engineFor(level);
        if (engine == null || engine.isClosed()) {
            return Result.empty();
        }

        removeMarker(engine, position);
        Result drops = evaluateDrops(level, position, pending.brokenState(), engine);
        ModdedTreeFellerService treeFeller = treeFellerService();
        ModdedTreeFellerService.OriginDropRoute route = treeFeller == null || pending.preparedOrigin() == null
                ? null
                : treeFeller.completeOrigin(pending.preparedOrigin());
        if (route == null) {
            clearTreeProvenance(engine, position);
        }
        return drops.withRoute(route);
    }

    public static Result evaluateManagedDrops(ServerLevel level, BlockPos position, BlockState brokenState) {
        Engine engine = engineFor(level);
        if (engine == null || engine.isClosed()) {
            return Result.empty();
        }
        try {
            return evaluateDrops(level, position, brokenState, engine);
        } catch (Throwable error) {
            ModdedIrisLog.error("Iris managed block-drop processing failed at {},{},{} in {}", position.getX(), position.getY(), position.getZ(),
                    level.dimension().identifier(), error);
            return Result.empty();
        }
    }

    public static void completeManagedBreak(ServerLevel level, BlockPos position) {
        Engine engine = engineFor(level);
        if (engine != null && !engine.isClosed()) {
            removeMarker(engine, position);
            clearTreeProvenance(engine, position);
        }
    }

    public static void clearPlacedProvenance(ServerLevel level, BlockPos position) {
        completeManagedBreak(level, position);
    }

    private static Result evaluateDrops(
            ServerLevel level,
            BlockPos position,
            BlockState brokenState,
            Engine engine
    ) {
        KList<IrisBlockDrops> providers = providers(engine, position, brokenState);
        if (providers.isEmpty()) {
            return Result.empty();
        }

        KList<ItemStack> drops = new KList<>();
        boolean replaceVanillaDrops = false;
        for (IrisBlockDrops provider : providers) {
            replaceVanillaDrops |= provider.isReplaceVanillaDrops();
            for (IrisLoot loot : provider.getDrops()) {
                if (!LootResolver.oneIn(RNG.r, loot.getRarity())) {
                    continue;
                }
                ItemStack stack = ModdedItemTranslator.stack(loot, RNG.r, level);
                if (stack != null && !stack.isEmpty()) {
                    drops.add(stack);
                }
            }
        }
        return new Result(drops, replaceVanillaDrops, null);
    }

    private static void removeMarker(Engine engine, BlockPos position) {
        int mantleY = position.getY() - engine.getMinHeight();
        MatterMarker marker = engine.getMantle().getMantle().get(position.getX(), mantleY, position.getZ(), MatterMarker.class);
        if (marker == null) {
            return;
        }
        String tag = marker.getTag();
        if ("cave_floor".equals(tag) || "cave_ceiling".equals(tag)) {
            return;
        }
        IrisMarker configured = engine.getData().getMarkerLoader().load(tag);
        if (configured == null || configured.isRemoveOnChange()) {
            engine.getMantle().getMantle().remove(position.getX(), mantleY, position.getZ(), MatterMarker.class);
        }
    }

    private static void clearTreeProvenance(Engine engine, BlockPos position) {
        int mantleY = position.getY() - engine.getMinHeight();
        engine.getMantle().getMantle().remove(position.getX(), mantleY, position.getZ(), String.class);
        engine.getMantle().getMantle().remove(position.getX(), mantleY, position.getZ(), TreeBlockMaterial.class);
    }

    private static KList<IrisBlockDrops> providers(Engine engine, BlockPos position, BlockState brokenState) {
        KList<IrisBlockDrops> providers = new KList<>();
        IrisData data = engine.getData();
        int relativeY = position.getY() - engine.getMinHeight();
        IrisBiome biome = engine.getBiome(position.getX(), relativeY, position.getZ());
        if (biome != null) {
            addMatching(providers, biome.getBlockDrops(), brokenState, data);
        }
        if (skipsParents(providers)) {
            return providers;
        }
        IrisRegion region = engine.getRegion(position.getX(), relativeY, position.getZ());
        if (region != null) {
            addMatching(providers, region.getBlockDrops(), brokenState, data);
        }
        addMatching(providers, engine.getDimension().getBlockDrops(), brokenState, data);
        return providers;
    }

    private static void addMatching(KList<IrisBlockDrops> matches, KList<IrisBlockDrops> candidates, BlockState brokenState, IrisData data) {
        for (IrisBlockDrops candidate : candidates) {
            if (matches(candidate, brokenState, data)) {
                matches.add(candidate);
            }
        }
    }

    private static boolean skipsParents(KList<IrisBlockDrops> providers) {
        for (IrisBlockDrops provider : providers) {
            if (provider.isSkipParents()) {
                return true;
            }
        }
        return false;
    }

    private static boolean matches(IrisBlockDrops provider, BlockState brokenState, IrisData data) {
        for (IrisBlockData configuredBlock : provider.getBlocks()) {
            PlatformBlockState resolved = configuredBlock.getBlockData(data);
            if (resolved == null || !(resolved.nativeHandle() instanceof BlockState configuredState)) {
                continue;
            }
            if (matchesState(configuredState, brokenState, provider.isExactBlocks())) {
                return true;
            }
        }
        return false;
    }

    static boolean matchesState(BlockState configuredState, BlockState brokenState, boolean exact) {
        return exact ? configuredState.equals(brokenState) : configuredState.getBlock() == brokenState.getBlock();
    }

    public static Engine engineFor(ServerLevel level) {
        ChunkGenerator generator = level.getChunkSource().getGenerator();
        if (!(generator instanceof IrisModdedChunkGenerator irisGenerator)) {
            return null;
        }
        Engine engine = irisGenerator.engineIfBound();
        if (engine != null) {
            return engine;
        }
        try {
            return irisGenerator.commandEngine();
        } catch (Throwable error) {
            ModdedIrisLog.error("Iris could not resolve the engine for a block break in {}", level.dimension().identifier(), error);
            return null;
        }
    }

    private static ModdedTreeFellerService treeFellerService() {
        return ModdedEngineBootstrap.services().service(ModdedTreeFellerService.class);
    }

    public static final class Result {
        private final KList<ItemStack> drops;
        private final boolean replaceVanillaDrops;
        private final ModdedTreeFellerService.OriginDropRoute route;

        private Result(
                KList<ItemStack> drops,
                boolean replaceVanillaDrops,
                ModdedTreeFellerService.OriginDropRoute route
        ) {
            this.drops = drops;
            this.replaceVanillaDrops = replaceVanillaDrops;
            this.route = route;
        }

        public KList<ItemStack> drops() {
            return drops;
        }

        public boolean replaceVanillaDrops() {
            return replaceVanillaDrops;
        }

        public boolean routeCombinedDrops(Iterable<ItemStack> vanillaDrops) {
            if (route == null) {
                return false;
            }
            return route.route(combinedDrops(vanillaDrops));
        }

        public KList<ItemStack> combinedDrops(Iterable<ItemStack> vanillaDrops) {
            KList<ItemStack> combined = new KList<>();
            if (!replaceVanillaDrops) {
                for (ItemStack stack : vanillaDrops) {
                    if (stack != null && !stack.isEmpty()) {
                        combined.add(stack.copy());
                    }
                }
            }
            for (ItemStack stack : drops) {
                if (stack != null && !stack.isEmpty()) {
                    combined.add(stack.copy());
                }
            }
            return combined;
        }

        private Result withRoute(ModdedTreeFellerService.OriginDropRoute route) {
            return new Result(drops, replaceVanillaDrops, route);
        }

        private static Result empty() {
            return new Result(new KList<>(), false, null);
        }
    }

    private record BreakKey(ServerLevel level, long position) {
    }

    private record PendingBreak(
            BlockState brokenState,
            ModdedTreeFellerService.PreparedOrigin preparedOrigin
    ) {
    }
}
