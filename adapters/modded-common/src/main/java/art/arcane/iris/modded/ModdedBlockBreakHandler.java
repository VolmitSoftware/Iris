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

import art.arcane.volmlib.nativelib.terrain.NativeWorld;
import art.arcane.volmlib.nativelib.minecraft26_2.modded.NativeBlockProperties;
import art.arcane.volmlib.nativelib.terrain.NativeBlockPoint;
import art.arcane.volmlib.nativelib.minecraft26_2.modded.NativeProtocolPlayer;
import art.arcane.volmlib.nativelib.minecraft26_2.modded.NativeWorldGenerators;
import art.arcane.volmlib.nativelib.minecraft26_2.modded.NativeBlockBreakContext;

import art.arcane.volmlib.nativelib.minecraft26_2.modded.NativeItemStack;

import art.arcane.volmlib.nativelib.minecraft26_2.modded.NativeBlockDropHooks;

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
import art.arcane.volmlib.nativelib.terrain.NativeBlockState;
import art.arcane.iris.modded.service.ModdedTreeFellerService;
import art.arcane.volmlib.util.collection.KList;
import art.arcane.volmlib.util.math.RNG;
import art.arcane.volmlib.util.matter.MatterMarker;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

public final class ModdedBlockBreakHandler {
    private static final ConcurrentHashMap<BreakKey, PendingBreak> PENDING = new ConcurrentHashMap<>();

    private ModdedBlockBreakHandler() {
    }

    public static void prepare(NativeBlockBreakContext context) {
        NativeWorld level = context.world();
        NativeProtocolPlayer player = context.player();
        NativeBlockPoint position = context.position();
        NativeBlockState brokenState = context.state();
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
                    position.x(), position.y(), position.z());
            return;
        }
        BreakKey key = new BreakKey(level, position);
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

    public static void cancel(NativeWorld level, NativeBlockPoint position) {
        PENDING.remove(new BreakKey(level, position));
    }

    public static Result complete(NativeWorld level, NativeBlockPoint position, NativeBlockState fallbackState) {
        BreakKey key = new BreakKey(level, position);
        PendingBreak pending = PENDING.remove(key);
        PendingBreak resolved = pending == null ? new PendingBreak(fallbackState, null) : pending;
        return evaluateSafely(level, position, resolved);
    }

    public static Result completePrepared(NativeWorld level, NativeBlockPoint position) {
        BreakKey key = new BreakKey(level, position);
        PendingBreak pending = PENDING.get(key);
        if (pending == null || level.getBlock(position.x(), position.y(), position.z()).equals(pending.brokenState())) {
            return null;
        }
        return PENDING.remove(key, pending) ? evaluateSafely(level, position, pending) : null;
    }

    private static void finishPending(BreakKey key, PendingBreak pending, NativeBlockPoint position) {
        if (!PENDING.remove(key, pending)) {
            return;
        }
        NativeWorld level = key.level();
        if (level.getBlock(position.x(), position.y(), position.z()).equals(pending.brokenState())) {
            return;
        }
        Result result = evaluateSafely(level, position, pending);
        if (!result.routeCombinedDrops(List.of())) {
            NativeBlockDropHooks.spawn(level, position, result.drops());
        }
    }

    private static Result evaluateSafely(NativeWorld level, NativeBlockPoint position, PendingBreak pending) {
        try {
            return evaluate(level, position, pending);
        } catch (Throwable error) {
            ModdedIrisLog.error("Iris block-break processing failed at {},{},{} in {}", position.x(), position.y(), position.z(),
                    level.name(), error);
            return Result.empty();
        }
    }

    private static Result evaluate(NativeWorld level, NativeBlockPoint position, PendingBreak pending) {
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

    public static Result evaluateManagedDrops(NativeWorld level, NativeBlockPoint position, NativeBlockState brokenState) {
        Engine engine = engineFor(level);
        if (engine == null || engine.isClosed()) {
            return Result.empty();
        }
        try {
            return evaluateDrops(level, position, brokenState, engine);
        } catch (Throwable error) {
            ModdedIrisLog.error("Iris managed block-drop processing failed at {},{},{} in {}", position.x(), position.y(), position.z(),
                    level.name(), error);
            return Result.empty();
        }
    }

    public static void completeManagedBreak(NativeWorld level, NativeBlockPoint position) {
        Engine engine = engineFor(level);
        if (engine != null && !engine.isClosed()) {
            removeMarker(engine, position);
            clearTreeProvenance(engine, position);
        }
    }

    public static void clearPlacedProvenance(NativeWorld level, NativeBlockPoint position) {
        completeManagedBreak(level, position);
    }

    private static Result evaluateDrops(
            NativeWorld level,
            NativeBlockPoint position,
            NativeBlockState brokenState,
            Engine engine
    ) {
        KList<IrisBlockDrops> providers = providers(engine, position, brokenState);
        if (providers.isEmpty()) {
            return Result.empty();
        }

        KList<NativeItemStack> drops = new KList<>();
        boolean replaceVanillaDrops = false;
        for (IrisBlockDrops provider : providers) {
            replaceVanillaDrops |= provider.isReplaceVanillaDrops();
            for (IrisLoot loot : provider.getDrops()) {
                if (!LootResolver.oneIn(RNG.r, loot.getRarity())) {
                    continue;
                }
                NativeItemStack stack = ModdedItemTranslator.stack(loot, RNG.r, ModdedItemTranslator.context(level));
                if (stack != null && !stack.isEmpty()) {
                    drops.add(stack);
                }
            }
        }
        return new Result(drops, replaceVanillaDrops, null);
    }

    private static void removeMarker(Engine engine, NativeBlockPoint position) {
        int mantleY = position.y() - engine.getMinHeight();
        MatterMarker marker = engine.getMantle().getMantle().get(position.x(), mantleY, position.z(), MatterMarker.class);
        if (marker == null) {
            return;
        }
        String tag = marker.getTag();
        if ("cave_floor".equals(tag) || "cave_ceiling".equals(tag)) {
            return;
        }
        IrisMarker configured = engine.getData().getMarkerLoader().load(tag);
        if (configured == null || configured.isRemoveOnChange()) {
            engine.getMantle().getMantle().remove(position.x(), mantleY, position.z(), MatterMarker.class);
        }
    }

    private static void clearTreeProvenance(Engine engine, NativeBlockPoint position) {
        int mantleY = position.y() - engine.getMinHeight();
        engine.getMantle().getMantle().remove(position.x(), mantleY, position.z(), String.class);
        engine.getMantle().getMantle().remove(position.x(), mantleY, position.z(), TreeBlockMaterial.class);
    }

    private static KList<IrisBlockDrops> providers(Engine engine, NativeBlockPoint position, NativeBlockState brokenState) {
        KList<IrisBlockDrops> providers = new KList<>();
        IrisData data = engine.getData();
        int relativeY = position.y() - engine.getMinHeight();
        IrisBiome biome = engine.getBiome(position.x(), relativeY, position.z());
        if (biome != null) {
            addMatching(providers, biome.getBlockDrops(), brokenState, data);
        }
        if (skipsParents(providers)) {
            return providers;
        }
        IrisRegion region = engine.getRegion(position.x(), relativeY, position.z());
        if (region != null) {
            addMatching(providers, region.getBlockDrops(), brokenState, data);
        }
        addMatching(providers, engine.getDimension().getBlockDrops(), brokenState, data);
        return providers;
    }

    private static void addMatching(KList<IrisBlockDrops> matches, KList<IrisBlockDrops> candidates, NativeBlockState brokenState, IrisData data) {
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

    private static boolean matches(IrisBlockDrops provider, NativeBlockState brokenState, IrisData data) {
        for (IrisBlockData configuredBlock : provider.getBlocks()) {
            NativeBlockState resolved = configuredBlock.getBlockData(data);
            if (resolved == null) {
                continue;
            }
            if (matchesState(resolved, brokenState, provider.isExactBlocks())) {
                return true;
            }
        }
        return false;
    }

    static boolean matchesState(NativeBlockState configuredState, NativeBlockState brokenState, boolean exact) {
        return NativeBlockProperties.matches(configuredState, brokenState, exact);
    }

    public static Engine engineFor(NativeWorld level) {
        IrisModdedChunkGenerator irisGenerator = NativeWorldGenerators.find(level, IrisModdedChunkGenerator.class);
        if (irisGenerator == null) {
            return null;
        }
        Engine engine = irisGenerator.engineIfBound();
        if (engine != null) {
            return engine;
        }
        try {
            return irisGenerator.commandEngine();
        } catch (Throwable error) {
            ModdedIrisLog.error("Iris could not resolve the engine for a block break in {}", level.name(), error);
            return null;
        }
    }

    private static ModdedTreeFellerService treeFellerService() {
        return ModdedEngineBootstrap.services().service(ModdedTreeFellerService.class);
    }

    public static final class Result implements NativeBlockDropHooks.PolicyResult {
        private final KList<NativeItemStack> drops;
        private final boolean replaceVanillaDrops;
        private final ModdedTreeFellerService.OriginDropRoute route;

        private Result(
                KList<NativeItemStack> drops,
                boolean replaceVanillaDrops,
                ModdedTreeFellerService.OriginDropRoute route
        ) {
            this.drops = drops;
            this.replaceVanillaDrops = replaceVanillaDrops;
            this.route = route;
        }

        public KList<NativeItemStack> drops() {
            return drops;
        }

        public boolean replaceVanillaDrops() {
            return replaceVanillaDrops;
        }

        public boolean hasRoute() {
            return route != null;
        }

        public boolean routeCombinedDrops(Iterable<NativeItemStack> vanillaDrops) {
            if (route == null) {
                return false;
            }
            return route.route(combinedDrops(vanillaDrops));
        }

        public KList<NativeItemStack> combinedDrops(Iterable<NativeItemStack> vanillaDrops) {
            KList<NativeItemStack> combined = new KList<>();
            if (!replaceVanillaDrops) {
                for (NativeItemStack stack : vanillaDrops) {
                    if (stack != null && !stack.isEmpty()) {
                        combined.add(stack.copy());
                    }
                }
            }
            for (NativeItemStack stack : drops) {
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

    private record BreakKey(NativeWorld level, NativeBlockPoint position) {
    }

    private record PendingBreak(
            NativeBlockState brokenState,
            ModdedTreeFellerService.PreparedOrigin preparedOrigin
    ) {
    }
}
