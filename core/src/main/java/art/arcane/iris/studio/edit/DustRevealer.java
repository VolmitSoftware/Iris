/*
 * Iris is a World Generator for Minecraft Bukkit Servers
 * Copyright (c) 2022 Arcane Arts (Volmit Software)
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

package art.arcane.iris.studio.edit;

import art.arcane.iris.platform.bukkit.BukkitPlatform;
import art.arcane.iris.spi.IrisLogging;
import art.arcane.iris.localization.IrisLanguage;
import art.arcane.iris.localization.RuntimeUiMessages;
import art.arcane.iris.platform.bukkit.nms.INMS;
import art.arcane.iris.world.IrisToolbelt;
import art.arcane.iris.generation.runtime.Engine;
import art.arcane.iris.generation.biome.IrisBiome;
import art.arcane.iris.generation.terrain.IrisRegion;
import art.arcane.iris.platform.generation.PlatformChunkGenerator;
import art.arcane.iris.localization.C;
import art.arcane.volmlib.util.collection.KList;
import art.arcane.volmlib.util.math.BlockPosition;
import art.arcane.volmlib.util.math.M;
import art.arcane.volmlib.util.math.RNG;
import art.arcane.volmlib.util.localization.MessageArgument;
import art.arcane.iris.platform.bukkit.plugin.VolmitSender;
import art.arcane.iris.world.task.J;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Biome;
import org.bukkit.block.Block;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Supplier;

public class DustRevealer {
    // Matches the modded twin (ModdedDustRevealer) so both platforms truncate identically.
    private static final int MAX_HITS = 2_048;
    private static final int PARTICLE_BATCH_SIZE = 64;

    /**
     * Bounded, single-threaded flood fill over the object's placement key, followed by batched
     * particle playback on the owning threads. The previous shape recursively spawned one
     * scheduler task + one burst task per matched block, all mutating one unsynchronized
     * ArrayList and sleep-spinning MultiBurst workers — unbounded fan-out on large objects.
     */
    private static void reveal(Engine engine, World world, BlockPosition origin, String key) {
        KList<BlockPosition> hits = new KList<>();
        Set<BlockPosition> visited = new HashSet<>();
        ArrayDeque<BlockPosition> frontier = new ArrayDeque<>();
        visited.add(origin);
        frontier.add(origin);
        hits.add(origin);
        int minY = world.getMinHeight();
        int maxY = world.getMaxHeight();

        try {
            search:
            while (!frontier.isEmpty()) {
                BlockPosition at = frontier.poll();
                for (int dx = -1; dx <= 1; dx++) {
                    for (int dy = -1; dy <= 1; dy++) {
                        for (int dz = -1; dz <= 1; dz++) {
                            if (dx == 0 && dy == 0 && dz == 0) {
                                continue;
                            }
                            BlockPosition next = new BlockPosition(at.getX() + dx, at.getY() + dy, at.getZ() + dz);
                            if (next.getY() < minY || next.getY() >= maxY || !visited.add(next)) {
                                continue;
                            }
                            if (key.equals(engine.getObjectPlacementKey(next.getX(), next.getY() - minY, next.getZ()))) {
                                hits.add(next);
                                frontier.add(next);
                                if (hits.size() >= MAX_HITS) {
                                    break search;
                                }
                            }
                        }
                    }
                }
            }
        } catch (Throwable e) {
            IrisLogging.reportError(e);
        }

        revealBatch(world, hits, 0);
    }

    private static void revealBatch(World world, KList<BlockPosition> hits, int from) {
        if (from >= hits.size()) {
            return;
        }
        int to = Math.min(from + PARTICLE_BATCH_SIZE, hits.size());
        Runnable batch = () -> {
            for (int i = from; i < to; i++) {
                BlockPosition p = hits.get(i);
                BlockSignal.of(world, p.getX(), p.getY(), p.getZ(), 10);
            }
            BlockPosition first = hits.get(from);
            if (M.r(0.25)) {
                world.playSound(first.toBlock(world).getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1f, RNG.r.f(0.2f, 2f));
            }
            revealBatch(world, hits, to);
        };
        Location at = hits.get(from).toBlock(world).getLocation();
        if (!J.runAt(at, batch, 1) && !J.isFolia()) {
            J.s(batch, 1);
        }
    }

    public static void spawn(Block block, VolmitSender sender) {
        World world = block.getWorld();
        PlatformChunkGenerator generator = IrisToolbelt.access(world);
        if (generator == null) {
            return;
        }
        Engine access = generator.getEngine();

        if (access != null) {
            describe(access, world, block, sender);

            String a = access.getObjectPlacementKey(block.getX(), block.getY() - block.getWorld().getMinHeight(), block.getZ());
            if (a != null) {
                world.playSound(block.getLocation(), Sound.ITEM_LODESTONE_COMPASS_LOCK, 1f, 0.1f);

                sender.sendMessage(IrisLanguage.text(
                        RuntimeUiMessages.DUST_FOUND_OBJECT,
                        MessageArgument.untrusted("object", a)
                ));
                J.a(() -> reveal(access, world, new BlockPosition(block.getX(), block.getY(), block.getZ()), a));
            }
        }
    }

    private static void describe(Engine engine, World world, Block block, VolmitSender sender) {
        int x = block.getX();
        int y = block.getY();
        int z = block.getZ();
        int minHeight = world.getMinHeight();
        int relativeY = y - minHeight;
        int surfaceRelative = engine.getHeight(x, z, true);
        int surfaceY = surfaceRelative + minHeight;
        int offset = y - surfaceY;

        String objectKey = safe(() -> engine.getObjectPlacementKey(x, relativeY, z));
        IrisBiome surfaceBiome = safe(() -> engine.getSurfaceBiome(x, z));
        IrisBiome biomeHere = safe(() -> engine.getBiome(x, relativeY, z));
        IrisBiome caveBiome = safe(() -> engine.getCaveOrMantleBiome(x, relativeY, z));
        IrisRegion region = safe(() -> engine.getRegion(x, z));

        KList<DustLine> lines = new KList<>();
        lines.add(new DustLine(IrisLanguage.text(
                RuntimeUiMessages.DUST_HEADER,
                MessageArgument.trusted("x", x),
                MessageArgument.trusted("y", y),
                MessageArgument.trusted("z", z)
        ), false));
        lines.add(new DustLine(IrisLanguage.text(
                RuntimeUiMessages.DUST_BLOCK,
                MessageArgument.untrusted("block", block.getType().name())
        ), false));
        if (offset > 0) {
            lines.add(new DustLine(IrisLanguage.text(
                    RuntimeUiMessages.DUST_POSITION_ABOVE,
                    MessageArgument.trusted("offset", offset),
                    MessageArgument.trusted("surfaceY", surfaceY)
            ), true));
        } else if (offset < 0) {
            lines.add(new DustLine(IrisLanguage.text(
                    RuntimeUiMessages.DUST_POSITION_BELOW,
                    MessageArgument.trusted("offset", -offset),
                    MessageArgument.trusted("surfaceY", surfaceY)
            ), true));
        } else {
            lines.add(new DustLine(IrisLanguage.text(
                    RuntimeUiMessages.DUST_POSITION_AT,
                    MessageArgument.trusted("surfaceY", surfaceY)
            ), true));
        }

        String placedBy;
        if (offset > 0) {
            placedBy = objectKey != null
                    ? IrisLanguage.text(
                            RuntimeUiMessages.DUST_PLACED_BY_OBJECT_ABOVE,
                            MessageArgument.untrusted("object", objectKey)
                    )
                    : IrisLanguage.text(RuntimeUiMessages.DUST_PLACED_BY_DECORATION_ABOVE);
        } else if (objectKey != null) {
            placedBy = IrisLanguage.text(
                    RuntimeUiMessages.DUST_PLACED_BY_BURIED_OBJECT,
                    MessageArgument.untrusted("object", objectKey)
            );
        } else {
            placedBy = IrisLanguage.text(
                    RuntimeUiMessages.DUST_PLACED_BY_TERRAIN,
                    MessageArgument.trusted("depth", Math.max(0, surfaceRelative - relativeY))
            );
        }
        lines.add(new DustLine(placedBy, true));
        lines.add(new DustLine(IrisLanguage.text(
                RuntimeUiMessages.DUST_OBJECT_AT_BLOCK,
                MessageArgument.untrusted(
                        "object",
                        objectKey == null ? IrisLanguage.text(RuntimeUiMessages.DUST_NONE) : objectKey
                )
        ), false));
        if (objectKey == null) {
            String columnObject = findColumnObject(engine, x, relativeY, z, minHeight);
            lines.add(new DustLine(IrisLanguage.text(
                    columnObject == null
                            ? RuntimeUiMessages.DUST_COLUMN_OBJECT_NONE
                            : RuntimeUiMessages.DUST_COLUMN_OBJECT,
                    MessageArgument.trusted(
                            columnObject == null ? "detail" : "object",
                            columnObject == null ? IrisLanguage.text(RuntimeUiMessages.DUST_COLUMN_NONE) : columnObject
                    )
            ), false));
        }

        if (surfaceBiome != null) {
            lines.add(new DustLine(IrisLanguage.text(
                    RuntimeUiMessages.DUST_SURFACE_BIOME_DETAIL,
                    MessageArgument.untrusted("biome", surfaceBiome.getLoadKey()),
                    MessageArgument.untrusted("derivative", biomeKey(surfaceBiome.getDerivative()))
            ), false));
        }
        if (biomeHere != null && (surfaceBiome == null || !biomeHere.getLoadKey().equals(surfaceBiome.getLoadKey()))) {
            lines.add(new DustLine(IrisLanguage.text(
                    RuntimeUiMessages.DUST_BIOME_AT_Y,
                    MessageArgument.untrusted("biome", biomeHere.getLoadKey())
            ), false));
        }
        if (caveBiome != null && (surfaceBiome == null || !caveBiome.getLoadKey().equals(surfaceBiome.getLoadKey()))) {
            lines.add(new DustLine(IrisLanguage.text(
                    RuntimeUiMessages.DUST_CAVE_BIOME,
                    MessageArgument.untrusted("biome", caveBiome.getLoadKey())
            ), false));
        }

        try {
            lines.add(new DustLine(IrisLanguage.text(
                    RuntimeUiMessages.DUST_SERVER_BIOME,
                    MessageArgument.untrusted("biome", INMS.get().getTrueBiomeBaseKey(block.getLocation())),
                    MessageArgument.trusted("id", INMS.get().getTrueBiomeBaseId(INMS.get().getTrueBiomeBase(block.getLocation())))
            ), false));
        } catch (Throwable e) {
            IrisLogging.reportError(e);
        }

        if (region != null) {
            lines.add(new DustLine(IrisLanguage.text(
                    RuntimeUiMessages.DUST_REGION,
                    MessageArgument.untrusted("region", region.getLoadKey()),
                    MessageArgument.untrusted("name", region.getName())
            ), false));
        }

        Set<String> objects = safe(() -> engine.getObjectsAt(x >> 4, z >> 4));
        if (objects != null && !objects.isEmpty()) {
            lines.add(new DustLine(IrisLanguage.text(
                    RuntimeUiMessages.DUST_OBJECTS_IN_CHUNK,
                    MessageArgument.untrusted("objects", objects)
            ), false));
        }

        StringBuilder payload = new StringBuilder();
        sender.sendMessage(C.IRIS + lines.get(0).text());
        payload.append(lines.get(0).text());
        for (int i = 1; i < lines.size(); i++) {
            DustLine line = lines.get(i);
            sender.sendMessage((line.emphasis() ? C.YELLOW : C.WHITE) + line.text());
            payload.append('\n').append(line.text());
        }
        sendCopyButton(sender, payload.toString());
    }

    private static String findColumnObject(Engine engine, int x, int relativeY, int z, int minHeight) {
        for (int dy = 1; dy <= 64; dy++) {
            if (relativeY + dy >= 0) {
                try {
                    String up = engine.getObjectPlacementKey(x, relativeY + dy, z);
                    if (up != null) {
                        return IrisLanguage.text(
                                RuntimeUiMessages.DUST_COLUMN_ABOVE,
                                MessageArgument.untrusted("object", up),
                                MessageArgument.trusted("y", relativeY + dy + minHeight)
                        );
                    }
                } catch (Throwable ignored) {
                }
            }
            if (relativeY - dy >= 0) {
                try {
                    String down = engine.getObjectPlacementKey(x, relativeY - dy, z);
                    if (down != null) {
                        return IrisLanguage.text(
                                RuntimeUiMessages.DUST_COLUMN_BELOW,
                                MessageArgument.untrusted("object", down),
                                MessageArgument.trusted("y", relativeY - dy + minHeight)
                        );
                    }
                } catch (Throwable ignored) {
                }
            }
        }
        return null;
    }

    private static String biomeKey(Biome biome) {
        if (biome == null) {
            return IrisLanguage.text(RuntimeUiMessages.DUST_NONE);
        }
        try {
            return biome.getKey().getKey();
        } catch (Throwable ignored) {
            return biome.toString();
        }
    }

    private static <T> T safe(Supplier<T> supplier) {
        try {
            return supplier.get();
        } catch (Throwable e) {
            IrisLogging.reportError(e);
            return null;
        }
    }

    private static void sendCopyButton(VolmitSender sender, String payload) {
        if (!sender.isPlayer()) {
            return;
        }
        try {
            Component button = Component.text(IrisLanguage.text(RuntimeUiMessages.DUST_COPY_BUTTON))
                    .color(NamedTextColor.GREEN)
                    .clickEvent(ClickEvent.copyToClipboard(payload))
                    .hoverEvent(HoverEvent.showText(Component.text(IrisLanguage.text(RuntimeUiMessages.DUST_COPY_HOVER))));
            sender.sendComponent(button);
        } catch (Throwable e) {
            IrisLogging.reportError(e);
        }
    }

    private record DustLine(String text, boolean emphasis) {
    }
}
