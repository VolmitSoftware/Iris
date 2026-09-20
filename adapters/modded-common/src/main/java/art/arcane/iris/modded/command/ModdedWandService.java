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
import art.arcane.volmlib.nativelib.minecraft26_2.modded.NativeEditInteraction;
import art.arcane.volmlib.nativelib.minecraft26_2.modded.NativeTaggedItems;
import art.arcane.volmlib.nativelib.minecraft26_2.modded.NativeItemStack;
import art.arcane.volmlib.nativelib.minecraft26_2.modded.NativeModdedServer;
import art.arcane.iris.modded.ModdedIrisLog;
import art.arcane.iris.localization.IrisLanguage;
import art.arcane.iris.localization.RuntimeUiMessages;
import art.arcane.volmlib.util.localization.MessageArgument;
import art.arcane.volmlib.nativelib.minecraft26_2.modded.NativeCommandText.Format;
import art.arcane.volmlib.nativelib.minecraft26_2.modded.NativeCommandText;

import java.awt.Color;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

public final class ModdedWandService {
    private static final ConcurrentHashMap<UUID, Selection> SELECTIONS = new ConcurrentHashMap<>();
    private static final String WAND_TAG = "iris_wand";
    private static final String DUST_TAG = "iris_dust";
    private static final int DRAW_INTERVAL_TICKS = 5;
    private static final double DRAW_STEP = 0.5D;
    private static final double DRAW_KEEP_CHANCE = 0.4D;
    private static final int DRAW_POINT_CAP = 400;
    private static final double DRAW_DISTANCE_SQUARED = 96.0D * 96.0D;
    private static int tickCounter = 0;

    private ModdedWandService() {
    }

    public record Selection(String dimension, NativeBlockPoint first, NativeBlockPoint second) {
        public boolean complete() {
            return first != null && second != null;
        }

        public NativeBlockPoint min() {
            return new NativeBlockPoint(Math.min(first.x(), second.x()), Math.min(first.y(), second.y()), Math.min(first.z(), second.z()));
        }

        public NativeBlockPoint max() {
            return new NativeBlockPoint(Math.max(first.x(), second.x()), Math.max(first.y(), second.y()), Math.max(first.z(), second.z()));
        }
    }

    public static NativeItemStack createWand() {
        return NativeTaggedItems.create(new NativeTaggedItems.Options("minecraft:blaze_rod",
                NativeCommandText.literal(IrisLanguage.plain(RuntimeUiMessages.WAND_NAME)).withStyle(NativeCommandText.Format.BOLD, NativeCommandText.Format.GOLD),
                List.of(NativeCommandText.literal(IrisLanguage.plain(RuntimeUiMessages.WAND_LORE_FIRST)),
                        NativeCommandText.literal(IrisLanguage.plain(RuntimeUiMessages.WAND_LORE_SECOND))),
                WAND_TAG, true, true));
    }

    public static NativeItemStack createDust() {
        return NativeTaggedItems.create(new NativeTaggedItems.Options("minecraft:glowstone_dust",
                NativeCommandText.literal(IrisLanguage.plain(RuntimeUiMessages.DUST_NAME)).withStyle(NativeCommandText.Format.BOLD, NativeCommandText.Format.YELLOW),
                List.of(NativeCommandText.literal(IrisLanguage.plain(RuntimeUiMessages.DUST_LORE))),
                DUST_TAG, true, true));
    }

    public static boolean isHoldingWand(NativeEditPlayer player) {
        return player.holdingFlaggedItem(WAND_TAG);
    }

    public static boolean attackBlock(NativeEditInteraction interaction) {
        if (isHoldingWand(interaction.player())) {
            setCorner(interaction.player(), interaction.world(), interaction.position(), true);
            return true;
        }
        return false;
    }

    public static boolean useBlock(NativeEditInteraction interaction) {
        if (isHoldingWand(interaction.player())) {
            setCorner(interaction.player(), interaction.world(), interaction.position(), false);
            return true;
        }
        if (interaction.player().holdingFlaggedItem(DUST_TAG)) {
            interaction.world().sound(interaction.position(), "minecraft:block.amethyst_block.chime", new NativeEditWorld.SoundOptions(2.0F, 1.97F));
            ModdedDustRevealer.reveal(interaction.player(), interaction.world(), interaction.position());
            return true;
        }
        return false;
    }

    private static void setCorner(NativeEditPlayer player, NativeEditWorld level, NativeBlockPoint pos, boolean first) {
        String dimension = level.key();
        NativeBlockPoint corner = pos;
        SELECTIONS.compute(player.id(), (UUID uuid, Selection existing) -> {
            NativeBlockPoint other = existing != null && existing.dimension().equals(dimension) ? (first ? existing.second() : existing.first()) : null;
            return first ? new Selection(dimension, corner, other) : new Selection(dimension, other, corner);
        });
        level.sound(pos, "minecraft:block.end_portal_frame.fill", new NativeEditWorld.SoundOptions(1.0F, first ? 0.67F : 1.17F));
        player.sendOverlayMessage(NativeCommandText.literal(IrisLanguage.plain(
                RuntimeUiMessages.WAND_POSITION_SET,
                MessageArgument.trusted("position", first ? 1 : 2),
                MessageArgument.trusted("x", corner.x()),
                MessageArgument.trusted("y", corner.y()),
                MessageArgument.trusted("z", corner.z())
        )));
    }

    public static Selection selection(NativeEditPlayer player) {
        Selection selection = SELECTIONS.get(player.id());
        if (selection == null || !selection.complete() || !selection.dimension().equals(player.world().key())) {
            return null;
        }
        return selection;
    }

    public static void setSelection(NativeEditPlayer player, NativeBlockPoint first, NativeBlockPoint second) {
        SELECTIONS.put(player.id(), new Selection(player.world().key(), first, second));
    }

    public static void clearAll() {
        SELECTIONS.clear();
        ModdedDustRevealer.clear();
        ModdedWhatCommands.clear();
    }

    public static void serverTick(NativeModdedServer server) {
        tickCounter++;
        if (tickCounter % DRAW_INTERVAL_TICKS != 0) {
            return;
        }
        try {
            server.forEachPlayer(context -> drawSelection(context.editing()));
        } catch (Throwable e) {
            ModdedIrisLog.error("Iris wand selection draw failed", e);
        }
    }

    private static void drawSelection(NativeEditPlayer player) {
        if (!isHoldingWand(player)) {
            return;
        }
        Selection selection = selection(player);
        if (selection != null) {
            draw(player, selection);
        }
    }

    private static void draw(NativeEditPlayer player, Selection selection) {
        NativeBlockPoint min = selection.min();
        NativeBlockPoint max = selection.max();
        double lowX = min.x();
        double lowY = min.y();
        double lowZ = min.z();
        double highX = max.x() + 1;
        double highY = max.y() + 1;
        double highZ = max.z() + 1;
        double[][] edges = {
                {lowX, lowY, lowZ, highX, lowY, lowZ},
                {lowX, lowY, lowZ, lowX, highY, lowZ},
                {lowX, lowY, lowZ, lowX, lowY, highZ},
                {highX, lowY, lowZ, highX, highY, lowZ},
                {highX, lowY, lowZ, highX, lowY, highZ},
                {lowX, highY, lowZ, highX, highY, lowZ},
                {lowX, highY, lowZ, lowX, highY, highZ},
                {lowX, lowY, highZ, highX, lowY, highZ},
                {lowX, lowY, highZ, lowX, highY, highZ},
                {highX, highY, lowZ, highX, highY, highZ},
                {lowX, highY, highZ, highX, highY, highZ},
                {highX, lowY, highZ, highX, highY, highZ}
        };

        ThreadLocalRandom random = ThreadLocalRandom.current();
        double px = player.x();
        double py = player.y();
        double pz = player.z();
        int sent = 0;
        for (double[] edge : edges) {
            double dx = edge[3] - edge[0];
            double dy = edge[4] - edge[1];
            double dz = edge[5] - edge[2];
            double length = Math.sqrt(dx * dx + dy * dy + dz * dz);
            if (length <= 0) {
                continue;
            }
            double ux = dx / length;
            double uy = dy / length;
            double uz = dz / length;
            for (double d = 0; d <= length; d += DRAW_STEP) {
                if (random.nextDouble() > DRAW_KEEP_CHANCE) {
                    continue;
                }
                double x = edge[0] + ux * d;
                double y = edge[1] + uy * d;
                double z = edge[2] + uz * d;
                double distX = x - px;
                double distY = y - py;
                double distZ = z - pz;
                if (distX * distX + distY * distY + distZ * distZ > DRAW_DISTANCE_SQUARED) {
                    continue;
                }
                float hue = (float) (0.5F + (Math.sin((x + y + z + (player.ticks() / 2.0F)) / 20.0F) / 2.0D));
                Color color = Color.getHSBColor(hue, 1.0F, 1.0F);
                player.coloredDust(color.getRGB() & 0xFFFFFF, 0.9F, x, y, z);
                sent++;
                if (sent >= DRAW_POINT_CAP) {
                    return;
                }
            }
        }
    }
}
