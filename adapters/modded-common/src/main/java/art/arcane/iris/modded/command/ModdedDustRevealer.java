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

package art.arcane.iris.modded.command;

import art.arcane.iris.modded.ModdedIrisLog;
import art.arcane.iris.localization.IrisLanguage;
import art.arcane.iris.localization.RuntimeUiMessages;
import art.arcane.iris.generation.runtime.Engine;
import art.arcane.iris.generation.runtime.GenerationSessionException;
import art.arcane.iris.generation.runtime.GenerationSessionLease;
import art.arcane.iris.generation.biome.IrisBiome;
import art.arcane.iris.generation.terrain.IrisRegion;
import art.arcane.iris.modded.ModdedBlockState;
import art.arcane.iris.modded.ModdedEngineBootstrap;
import art.arcane.iris.modded.ModdedScheduler;
import art.arcane.iris.generation.context.IrisContext;
import art.arcane.volmlib.util.localization.MessageArgument;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.biome.Biome;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

public final class ModdedDustRevealer {
    private static final int MAX_HITS = 2_048;
    private static final int PARTICLE_BATCH_SIZE = 64;
    private static final DustParticleOptions REVEAL_DUST = new DustParticleOptions(0xFFD24A, 1.2F);
    private static final ConcurrentHashMap<UUID, RevealRun> ACTIVE_RUNS = new ConcurrentHashMap<>();

    private ModdedDustRevealer() {
    }

    public static void reveal(ServerPlayer player, ServerLevel level, BlockPos pos) {
        Engine engine = IrisModdedCommands.engineFor(level);
        if (engine == null) {
            player.sendSystemMessage(Component.literal(
                    IrisLanguage.plain(RuntimeUiMessages.DUST_IRIS_WORLD_REQUIRED)));
            return;
        }
        describe(player, level, engine, pos);

        int relativeY = pos.getY() - engine.getMinHeight();
        String key = safe(
                "object lookup at " + coordinates(pos),
                () -> engine.getObjectPlacementKey(pos.getX(), relativeY, pos.getZ()));
        if (key == null) {
            return;
        }
        ModdedScheduler scheduler = ModdedEngineBootstrap.schedulerOrNull();
        if (scheduler == null) {
            player.sendSystemMessage(Component.literal(
                    IrisLanguage.plain(RuntimeUiMessages.DUST_REVEAL_FAILED)));
            return;
        }
        level.playSound(null, pos, SoundEvents.LODESTONE_COMPASS_LOCK,
                SoundSource.PLAYERS, 1.0F, 0.1F);
        player.sendSystemMessage(Component.literal(IrisLanguage.plain(
                RuntimeUiMessages.DUST_FOUND_OBJECT,
                MessageArgument.untrusted("object", key)
        )));

        RevealRun run = new RevealRun(
                player.getUUID(),
                player,
                level,
                engine,
                pos.immutable(),
                key,
                level.getMinY(),
                level.getMaxY() + 1,
                new AtomicBoolean());
        RevealRun previous = ACTIVE_RUNS.put(player.getUUID(), run);
        if (previous != null) {
            previous.cancelled().set(true);
        }
        scheduler.async(() -> discover(scheduler, run));
    }

    static void clear() {
        for (RevealRun run : ACTIVE_RUNS.values()) {
            run.cancelled().set(true);
        }
        ACTIVE_RUNS.clear();
    }

    private static void discover(ModdedScheduler scheduler, RevealRun run) {
        try (GenerationSessionLease lease = run.engine().acquireGenerationLease("modded_dust_reveal");
             IrisContext.Scope ignored = IrisContext.open(run.engine(), lease.sessionId(), null)) {
            List<BlockPos> hits = collect(run);
            if (!run.cancelled().get()) {
                scheduler.global(() -> revealBatch(scheduler, run, hits, 0));
            }
        } catch (GenerationSessionException error) {
            revealFailure(scheduler, run, error);
        } catch (Throwable error) {
            revealFailure(scheduler, run, error);
        }
    }

    static List<BlockPos> collect(RevealRun run) {
        return collect(
                run.origin(),
                run.key(),
                run.engine().getMinHeight(),
                run.minY(),
                run.maxYExclusive(),
                run.cancelled(),
                (int x, int relativeY, int z) ->
                        run.engine().getObjectPlacementKey(x, relativeY, z));
    }

    static List<BlockPos> collect(BlockPos origin, String key, int engineMinY,
                                  int minY, int maxYExclusive, AtomicBoolean cancelled,
                                  ObjectPlacementLookup lookup) {
        List<BlockPos> hits = new ArrayList<>();
        Set<BlockPos> visited = new HashSet<>();
        Deque<BlockPos> frontier = new ArrayDeque<>();
        frontier.add(origin);
        visited.add(origin);
        while (!frontier.isEmpty() && hits.size() < MAX_HITS && !cancelled.get()) {
            BlockPos current = frontier.poll();
            hits.add(current);
            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = -1; dy <= 1; dy++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        if (dx == 0 && dy == 0 && dz == 0) {
                            continue;
                        }
                        BlockPos next = current.offset(dx, dy, dz);
                        if (next.getY() < minY
                                || next.getY() >= maxYExclusive
                                || !visited.add(next)) {
                            continue;
                        }
                        String nextKey = lookup.at(
                                next.getX(), next.getY() - engineMinY, next.getZ());
                        if (key.equals(nextKey)) {
                            frontier.add(next);
                        }
                    }
                }
            }
        }
        return List.copyOf(hits);
    }

    private static void revealBatch(ModdedScheduler scheduler, RevealRun run,
                                    List<BlockPos> hits, int from) {
        if (!active(run)) {
            // Drop the registry entry on abort too, or the run record pins the player, level
            // and engine until server stop. No-op if a newer run already replaced it.
            ACTIVE_RUNS.remove(run.playerId(), run);
            return;
        }
        int to = Math.min(hits.size(), from + PARTICLE_BATCH_SIZE);
        for (int index = from; index < to; index++) {
            BlockPos hit = hits.get(index);
            run.level().sendParticles(run.player(), REVEAL_DUST, true, true,
                    hit.getX() + 0.5D, hit.getY() + 0.5D, hit.getZ() + 0.5D,
                    3, 0.25D, 0.25D, 0.25D, 0.0D);
        }
        if (to > from) {
            BlockPos soundAt = hits.get(from);
            run.level().playSound(null, soundAt, SoundEvents.AMETHYST_BLOCK_CHIME,
                    SoundSource.PLAYERS, 0.5F,
                    ThreadLocalRandom.current().nextFloat(0.2F, 2.0F));
        }
        if (to < hits.size()) {
            scheduler.laterGlobal(() -> revealBatch(scheduler, run, hits, to), 1);
            return;
        }
        ACTIVE_RUNS.remove(run.playerId(), run);
        run.player().sendSystemMessage(Component.literal(IrisLanguage.plain(
                hits.size() >= MAX_HITS
                        ? RuntimeUiMessages.DUST_REVEALED_CAPPED
                        : RuntimeUiMessages.DUST_REVEALED,
                MessageArgument.trusted("count", hits.size()),
                MessageArgument.untrusted("object", run.key())
        )));
    }

    private static boolean active(RevealRun run) {
        return !run.cancelled().get()
                && ACTIVE_RUNS.get(run.playerId()) == run
                && !run.player().hasDisconnected()
                && !run.player().isRemoved()
                && run.player().level() == run.level()
                && !run.engine().isClosing()
                && !run.engine().isClosed();
    }

    private static void revealFailure(ModdedScheduler scheduler, RevealRun run, Throwable error) {
        ModdedIrisLog.error("Iris dust reveal failed for {} at {}", run.key(), coordinates(run.origin()), error);
        scheduler.global(() -> {
            if (ACTIVE_RUNS.remove(run.playerId(), run)) {
                run.player().sendSystemMessage(Component.literal(
                        IrisLanguage.plain(RuntimeUiMessages.DUST_REVEAL_FAILED)));
            }
        });
    }

    private static void describe(ServerPlayer player, ServerLevel level, Engine engine, BlockPos pos) {
        int x = pos.getX();
        int y = pos.getY();
        int z = pos.getZ();
        int minHeight = engine.getMinHeight();
        int relativeY = y - minHeight;
        Integer surfaceRelative = safe(
                "surface height lookup at " + coordinates(pos),
                () -> engine.getHeight(x, z, true));
        Integer surfaceY = surfaceRelative == null ? null : surfaceRelative + minHeight;
        Integer offset = surfaceY == null ? null : y - surfaceY;

        String objectKey = safe(
                "object lookup at " + coordinates(pos),
                () -> engine.getObjectPlacementKey(x, relativeY, z));
        IrisBiome surfaceBiome = safe(
                "surface biome lookup at " + x + ", " + z,
                () -> engine.getSurfaceBiome(x, z));
        IrisBiome biomeHere = safe(
                "biome lookup at " + coordinates(pos),
                () -> engine.getBiome(x, relativeY, z));
        IrisBiome caveBiome = safe(
                "cave biome lookup at " + coordinates(pos),
                () -> engine.getCaveOrMantleBiome(x, relativeY, z));
        IrisRegion region = safe(
                "region lookup at " + x + ", " + z,
                () -> engine.getRegion(x, z));

        List<DustLine> lines = new ArrayList<>();
        lines.add(new DustLine(IrisLanguage.plain(
                RuntimeUiMessages.DUST_HEADER,
                MessageArgument.trusted("x", x),
                MessageArgument.trusted("y", y),
                MessageArgument.trusted("z", z)
        ), false));
        lines.add(new DustLine(IrisLanguage.plain(
                RuntimeUiMessages.DUST_BLOCK,
                MessageArgument.untrusted("block", ModdedBlockState.serialize(level.getBlockState(pos)))
        ), false));
        if (offset != null && surfaceY != null) {
            lines.add(new DustLine(positionLine(offset, surfaceY), true));
            lines.add(new DustLine(
                    placementLine(offset, surfaceRelative, relativeY, objectKey), true));
        }
        lines.add(new DustLine(IrisLanguage.plain(
                RuntimeUiMessages.DUST_OBJECT_AT_BLOCK,
                MessageArgument.untrusted(
                        "object",
                        objectKey == null
                                ? IrisLanguage.plain(RuntimeUiMessages.DUST_NONE)
                                : objectKey)
        ), false));
        if (objectKey == null) {
            String columnObject = findColumnObject(engine, x, relativeY, z, minHeight);
            lines.add(new DustLine(IrisLanguage.plain(
                    columnObject == null
                            ? RuntimeUiMessages.DUST_COLUMN_OBJECT_NONE
                            : RuntimeUiMessages.DUST_COLUMN_OBJECT,
                    MessageArgument.trusted(
                            columnObject == null ? "detail" : "object",
                            columnObject == null
                                    ? IrisLanguage.plain(RuntimeUiMessages.DUST_COLUMN_NONE)
                                    : columnObject)
            ), false));
        }
        if (surfaceBiome != null) {
            lines.add(new DustLine(IrisLanguage.plain(
                    RuntimeUiMessages.DUST_SURFACE_BIOME_DETAIL,
                    MessageArgument.untrusted("biome", surfaceBiome.getLoadKey()),
                    MessageArgument.untrusted("derivative", surfaceBiome.getDerivativeKey())
            ), false));
        }
        if (biomeHere != null
                && (surfaceBiome == null
                || !biomeHere.getLoadKey().equals(surfaceBiome.getLoadKey()))) {
            lines.add(new DustLine(IrisLanguage.plain(
                    RuntimeUiMessages.DUST_BIOME_AT_Y,
                    MessageArgument.untrusted("biome", biomeHere.getLoadKey())
            ), false));
        }
        if (caveBiome != null
                && (surfaceBiome == null
                || !caveBiome.getLoadKey().equals(surfaceBiome.getLoadKey()))) {
            lines.add(new DustLine(IrisLanguage.plain(
                    RuntimeUiMessages.DUST_CAVE_BIOME,
                    MessageArgument.untrusted("biome", caveBiome.getLoadKey())
            ), false));
        }
        NativeBiome nativeBiome = nativeBiome(level, pos);
        lines.add(new DustLine(IrisLanguage.plain(
                RuntimeUiMessages.DUST_SERVER_BIOME,
                MessageArgument.untrusted("biome", nativeBiome.key()),
                MessageArgument.trusted("id", nativeBiome.id())
        ), false));
        if (region != null) {
            lines.add(new DustLine(IrisLanguage.plain(
                    RuntimeUiMessages.DUST_REGION,
                    MessageArgument.untrusted("region", region.getLoadKey()),
                    MessageArgument.untrusted("name", region.getName())
            ), false));
        }
        Set<String> objects = safe(
                "chunk object lookup at " + (x >> 4) + ", " + (z >> 4),
                () -> engine.getObjectsAt(x >> 4, z >> 4));
        if (objects != null && !objects.isEmpty()) {
            lines.add(new DustLine(IrisLanguage.plain(
                    RuntimeUiMessages.DUST_OBJECTS_IN_CHUNK,
                    MessageArgument.untrusted("objects", objects.stream().sorted().toList())
            ), false));
        }
        sendReport(player, lines);
    }

    static String positionLine(int offset, int surfaceY) {
        if (offset > 0) {
            return IrisLanguage.plain(
                    RuntimeUiMessages.DUST_POSITION_ABOVE,
                    MessageArgument.trusted("offset", offset),
                    MessageArgument.trusted("surfaceY", surfaceY));
        }
        if (offset < 0) {
            return IrisLanguage.plain(
                    RuntimeUiMessages.DUST_POSITION_BELOW,
                    MessageArgument.trusted("offset", -offset),
                    MessageArgument.trusted("surfaceY", surfaceY));
        }
        return IrisLanguage.plain(
                RuntimeUiMessages.DUST_POSITION_AT,
                MessageArgument.trusted("surfaceY", surfaceY));
    }

    static String placementLine(int offset, int surfaceRelative,
                                int relativeY, String objectKey) {
        if (offset > 0) {
            return objectKey == null
                    ? IrisLanguage.plain(RuntimeUiMessages.DUST_PLACED_BY_DECORATION_ABOVE)
                    : IrisLanguage.plain(
                            RuntimeUiMessages.DUST_PLACED_BY_OBJECT_ABOVE,
                            MessageArgument.untrusted("object", objectKey));
        }
        if (objectKey != null) {
            return IrisLanguage.plain(
                    RuntimeUiMessages.DUST_PLACED_BY_BURIED_OBJECT,
                    MessageArgument.untrusted("object", objectKey));
        }
        return IrisLanguage.plain(
                RuntimeUiMessages.DUST_PLACED_BY_TERRAIN,
                MessageArgument.trusted("depth", Math.max(0, surfaceRelative - relativeY)));
    }

    private static String findColumnObject(Engine engine, int x, int relativeY,
                                           int z, int minHeight) {
        int maxRelativeY = engine.getMaxHeight() - minHeight - 1;
        try {
            for (int dy = 1; dy <= 64; dy++) {
                if (relativeY + dy <= maxRelativeY) {
                    String up = engine.getObjectPlacementKey(x, relativeY + dy, z);
                    if (up != null) {
                        return IrisLanguage.plain(
                                RuntimeUiMessages.DUST_COLUMN_ABOVE,
                                MessageArgument.untrusted("object", up),
                                MessageArgument.trusted("y", relativeY + dy + minHeight));
                    }
                }
                if (relativeY - dy >= 0) {
                    String down = engine.getObjectPlacementKey(x, relativeY - dy, z);
                    if (down != null) {
                        return IrisLanguage.plain(
                                RuntimeUiMessages.DUST_COLUMN_BELOW,
                                MessageArgument.untrusted("object", down),
                                MessageArgument.trusted("y", relativeY - dy + minHeight));
                    }
                }
            }
        } catch (Throwable error) {
            ModdedIrisLog.error("Iris dust column-object lookup failed at {}, {}, {}",
                    x, relativeY + minHeight, z, error);
        }
        return null;
    }

    private static NativeBiome nativeBiome(ServerLevel level, BlockPos pos) {
        Holder<Biome> holder = level.getBiome(pos);
        String key = holder.unwrapKey()
                .map((ResourceKey<Biome> resourceKey) -> resourceKey.identifier().toString())
                .orElse(IrisLanguage.plain(RuntimeUiMessages.STATUS_UNREGISTERED));
        Registry<Biome> registry = level.registryAccess().lookupOrThrow(Registries.BIOME);
        return new NativeBiome(key, registry.getId(holder.value()));
    }

    private static void sendReport(ServerPlayer player, List<DustLine> lines) {
        StringBuilder payload = new StringBuilder();
        for (int index = 0; index < lines.size(); index++) {
            DustLine line = lines.get(index);
            ChatFormatting color = index == 0
                    ? ChatFormatting.GOLD
                    : line.emphasis() ? ChatFormatting.YELLOW : ChatFormatting.WHITE;
            player.sendSystemMessage(Component.literal(line.text()).withStyle(color));
            if (index > 0) {
                payload.append('\n');
            }
            payload.append(line.text());
        }
        MutableComponent hover = Component.literal(
                IrisLanguage.plain(RuntimeUiMessages.DUST_COPY_HOVER));
        MutableComponent button = Component.literal(
                        IrisLanguage.plain(RuntimeUiMessages.DUST_COPY_BUTTON))
                .withStyle(ChatFormatting.GREEN)
                .withStyle(style -> style
                        .withClickEvent(new ClickEvent.CopyToClipboard(payload.toString()))
                        .withHoverEvent(new HoverEvent.ShowText(hover)));
        player.sendSystemMessage(button);
    }

    private static <T> T safe(String operation, Supplier<T> supplier) {
        try {
            return supplier.get();
        } catch (Throwable error) {
            ModdedIrisLog.error("Iris dust {} failed", operation, error);
            return null;
        }
    }

    private static String coordinates(BlockPos pos) {
        return pos.getX() + ", " + pos.getY() + ", " + pos.getZ();
    }

    private record DustLine(String text, boolean emphasis) {
    }

    private record NativeBiome(String key, int id) {
    }

    @FunctionalInterface
    interface ObjectPlacementLookup {
        String at(int x, int relativeY, int z);
    }

    static record RevealRun(
            UUID playerId,
            ServerPlayer player,
            ServerLevel level,
            Engine engine,
            BlockPos origin,
            String key,
            int minY,
            int maxYExclusive,
            AtomicBoolean cancelled
    ) {
    }
}
