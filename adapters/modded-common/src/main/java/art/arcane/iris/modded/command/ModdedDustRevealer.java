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

import art.arcane.volmlib.nativelib.terrain.NativeBlockPoint;
import art.arcane.volmlib.nativelib.minecraft26_2.modded.NativeEditPlayer;
import art.arcane.volmlib.nativelib.minecraft26_2.modded.NativeEditWorld;
import art.arcane.iris.modded.ModdedIrisLog;
import art.arcane.iris.localization.IrisLanguage;
import art.arcane.iris.localization.RuntimeUiMessages;
import art.arcane.iris.generation.runtime.Engine;
import art.arcane.iris.generation.runtime.GenerationSessionException;
import art.arcane.iris.generation.runtime.GenerationSessionLease;
import art.arcane.iris.generation.biome.IrisBiome;
import art.arcane.iris.generation.terrain.IrisRegion;
import art.arcane.iris.world.history.SavedBiomeUnavailableException;
import art.arcane.volmlib.nativelib.minecraft26_2.modded.ModdedBlockState;
import art.arcane.iris.modded.ModdedEngineBootstrap;
import art.arcane.iris.modded.ModdedScheduler;
import art.arcane.iris.generation.context.IrisContext;
import art.arcane.volmlib.util.localization.MessageArgument;
import art.arcane.volmlib.nativelib.minecraft26_2.modded.NativeCommandText.Format;
import art.arcane.volmlib.nativelib.minecraft26_2.modded.NativeCommandText;

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
    private static final NativeEditPlayer.Dust REVEAL_DUST = new NativeEditPlayer.Dust(0xFFD24A, 1.2F);
    private static final NativeEditPlayer.ParticleSpread REVEAL_SPREAD = new NativeEditPlayer.ParticleSpread(3, 0.25D, 0.25D, 0.25D, 0.0D);
    private static final ConcurrentHashMap<UUID, RevealRun> ACTIVE_RUNS = new ConcurrentHashMap<>();

    private ModdedDustRevealer() {
    }

    public static void reveal(NativeEditPlayer player, NativeEditWorld level, NativeBlockPoint pos) {
        Engine engine = IrisModdedCommands.engineFor(level.world());
        if (engine == null) {
            player.sendSystemMessage(NativeCommandText.literal(
                    IrisLanguage.plain(RuntimeUiMessages.DUST_IRIS_WORLD_REQUIRED)));
            return;
        }
        int relativeY = pos.y() - engine.getMinHeight();
        String key;
        try {
            describe(player, level, engine, pos);
            key = safe(
                    "object lookup at " + coordinates(pos),
                    () -> engine.getObjectPlacementKey(pos.x(), relativeY, pos.z()));
        } catch (SavedBiomeUnavailableException unavailable) {
            if (!unavailable.isLoading()) {
                ModdedIrisLog.error("Iris dust saved biome lookup failed at {}", coordinates(pos), unavailable);
            }
            player.sendSystemMessage(NativeCommandText.literal(IrisLanguage.plain(unavailable.isLoading()
                    ? RuntimeUiMessages.DUST_BIOME_LOADING
                    : RuntimeUiMessages.DUST_REVEAL_FAILED)));
            return;
        }
        if (key == null) {
            return;
        }
        ModdedScheduler scheduler = ModdedEngineBootstrap.schedulerOrNull();
        if (scheduler == null) {
            player.sendSystemMessage(NativeCommandText.literal(
                    IrisLanguage.plain(RuntimeUiMessages.DUST_REVEAL_FAILED)));
            return;
        }
        level.sound(pos, "minecraft:item.lodestone_compass.lock", new NativeEditWorld.SoundOptions(1.0F, 0.1F));
        player.sendSystemMessage(NativeCommandText.literal(IrisLanguage.plain(
                RuntimeUiMessages.DUST_FOUND_OBJECT,
                MessageArgument.untrusted("object", key)
        )));

        RevealRun run = new RevealRun(
                player.id(),
                player,
                level,
                engine,
                pos,
                key,
                level.minY(),
                level.maxY() + 1,
                new AtomicBoolean());
        RevealRun previous = ACTIVE_RUNS.put(player.id(), run);
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
            List<NativeBlockPoint> hits = collect(run);
            if (!run.cancelled().get()) {
                scheduler.global(() -> revealBatch(scheduler, run, hits, 0));
            }
        } catch (GenerationSessionException error) {
            revealFailure(scheduler, run, error);
        } catch (Throwable error) {
            revealFailure(scheduler, run, error);
        }
    }

    static List<NativeBlockPoint> collect(RevealRun run) {
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

    static List<NativeBlockPoint> collect(NativeBlockPoint origin, String key, int engineMinY,
                                  int minY, int maxYExclusive, AtomicBoolean cancelled,
                                  ObjectPlacementLookup lookup) {
        List<NativeBlockPoint> hits = new ArrayList<>();
        Set<NativeBlockPoint> visited = new HashSet<>();
        Deque<NativeBlockPoint> frontier = new ArrayDeque<>();
        frontier.add(origin);
        visited.add(origin);
        while (!frontier.isEmpty() && hits.size() < MAX_HITS && !cancelled.get()) {
            NativeBlockPoint current = frontier.poll();
            hits.add(current);
            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = -1; dy <= 1; dy++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        if (dx == 0 && dy == 0 && dz == 0) {
                            continue;
                        }
                        NativeBlockPoint next = current.offset(dx, dy, dz);
                        if (next.y() < minY
                                || next.y() >= maxYExclusive
                                || !visited.add(next)) {
                            continue;
                        }
                        String nextKey = lookup.at(
                                next.x(), next.y() - engineMinY, next.z());
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
                                    List<NativeBlockPoint> hits, int from) {
        if (!active(run)) {
            // Drop the registry entry on abort too, or the run record pins the player, level
            // and engine until server stop. No-op if a newer run already replaced it.
            ACTIVE_RUNS.remove(run.playerId(), run);
            return;
        }
        int to = Math.min(hits.size(), from + PARTICLE_BATCH_SIZE);
        for (int index = from; index < to; index++) {
            NativeBlockPoint hit = hits.get(index);
            run.player().dust(REVEAL_DUST, hit.x() + 0.5D, hit.y() + 0.5D, hit.z() + 0.5D, REVEAL_SPREAD);
        }
        if (to > from) {
            NativeBlockPoint soundAt = hits.get(from);
            run.level().sound(soundAt, "minecraft:block.amethyst_block.chime",
                    new NativeEditWorld.SoundOptions(0.5F, ThreadLocalRandom.current().nextFloat(0.2F, 2.0F)));
        }
        if (to < hits.size()) {
            scheduler.laterGlobal(() -> revealBatch(scheduler, run, hits, to), 1);
            return;
        }
        ACTIVE_RUNS.remove(run.playerId(), run);
        run.player().sendSystemMessage(NativeCommandText.literal(IrisLanguage.plain(
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
                && run.player().activeIn(run.level())
                && !run.engine().isClosing()
                && !run.engine().isClosed();
    }

    private static void revealFailure(ModdedScheduler scheduler, RevealRun run, Throwable error) {
        ModdedIrisLog.error("Iris dust reveal failed for {} at {}", run.key(), coordinates(run.origin()), error);
        scheduler.global(() -> {
            if (ACTIVE_RUNS.remove(run.playerId(), run)) {
                run.player().sendSystemMessage(NativeCommandText.literal(
                        IrisLanguage.plain(RuntimeUiMessages.DUST_REVEAL_FAILED)));
            }
        });
    }

    private static void describe(NativeEditPlayer player, NativeEditWorld level, Engine engine, NativeBlockPoint pos) {
        int x = pos.x();
        int y = pos.y();
        int z = pos.z();
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
                MessageArgument.untrusted("block", level.block(pos.x(), pos.y(), pos.z()).key())
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

    private static NativeBiome nativeBiome(NativeEditWorld level, NativeBlockPoint pos) {
        NativeEditWorld.BiomeIdentity biome = level.biome(pos);
        return new NativeBiome(biome.key() == null ? IrisLanguage.plain(RuntimeUiMessages.STATUS_UNREGISTERED) : biome.key(), biome.id());
    }

    private static void sendReport(NativeEditPlayer player, List<DustLine> lines) {
        StringBuilder payload = new StringBuilder();
        for (int index = 0; index < lines.size(); index++) {
            DustLine line = lines.get(index);
            Format color = index == 0
                    ? Format.GOLD
                    : line.emphasis() ? Format.YELLOW : Format.WHITE;
            player.sendSystemMessage(NativeCommandText.literal(line.text()).withStyle(color));
            if (index > 0) {
                payload.append('\n');
            }
            payload.append(line.text());
        }
        NativeCommandText hover = NativeCommandText.literal(
                IrisLanguage.plain(RuntimeUiMessages.DUST_COPY_HOVER));
        NativeCommandText button = NativeCommandText.literal(
                        IrisLanguage.plain(RuntimeUiMessages.DUST_COPY_BUTTON))
                .withStyle(Format.GREEN)
                .withStyle(style -> style
                        .copyToClipboard(payload.toString())
                        .hover(hover));
        player.sendSystemMessage(button);
    }

    private static <T> T safe(String operation, Supplier<T> supplier) {
        try {
            return supplier.get();
        } catch (SavedBiomeUnavailableException unavailable) {
            throw unavailable;
        } catch (Throwable error) {
            ModdedIrisLog.error("Iris dust {} failed", operation, error);
            return null;
        }
    }

    private static String coordinates(NativeBlockPoint pos) {
        return pos.x() + ", " + pos.y() + ", " + pos.z();
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
            NativeEditPlayer player,
            NativeEditWorld level,
            Engine engine,
            NativeBlockPoint origin,
            String key,
            int minY,
            int maxYExclusive,
            AtomicBoolean cancelled
    ) {
    }
}
