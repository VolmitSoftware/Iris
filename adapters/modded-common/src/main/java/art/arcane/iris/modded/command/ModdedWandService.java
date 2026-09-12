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
import art.arcane.volmlib.util.localization.MessageArgument;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Unit;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;

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

    public record Selection(ResourceKey<Level> dimension, BlockPos first, BlockPos second) {
        public boolean complete() {
            return first != null && second != null;
        }

        public BlockPos min() {
            return new BlockPos(Math.min(first.getX(), second.getX()), Math.min(first.getY(), second.getY()), Math.min(first.getZ(), second.getZ()));
        }

        public BlockPos max() {
            return new BlockPos(Math.max(first.getX(), second.getX()), Math.max(first.getY(), second.getY()), Math.max(first.getZ(), second.getZ()));
        }
    }

    public static ItemStack createWand() {
        ItemStack stack = new ItemStack(Items.BLAZE_ROD);
        stack.set(DataComponents.CUSTOM_NAME, Component.literal(IrisLanguage.plain(RuntimeUiMessages.WAND_NAME)).withStyle(ChatFormatting.BOLD, ChatFormatting.GOLD));
        stack.set(DataComponents.LORE, new ItemLore(List.of(
                Component.literal(IrisLanguage.plain(RuntimeUiMessages.WAND_LORE_FIRST)),
                Component.literal(IrisLanguage.plain(RuntimeUiMessages.WAND_LORE_SECOND)))));
        stack.set(DataComponents.UNBREAKABLE, Unit.INSTANCE);
        stack.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, Boolean.TRUE);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(flagTag(WAND_TAG)));
        stack.set(DataComponents.TOOLTIP_DISPLAY,
                TooltipDisplay.DEFAULT.withHidden(DataComponents.UNBREAKABLE, true));
        return stack;
    }

    public static ItemStack createDust() {
        ItemStack stack = new ItemStack(Items.GLOWSTONE_DUST);
        stack.set(DataComponents.CUSTOM_NAME, Component.literal(IrisLanguage.plain(RuntimeUiMessages.DUST_NAME)).withStyle(ChatFormatting.BOLD, ChatFormatting.YELLOW));
        stack.set(DataComponents.LORE, new ItemLore(List.of(
                Component.literal(IrisLanguage.plain(RuntimeUiMessages.DUST_LORE)))));
        stack.set(DataComponents.UNBREAKABLE, Unit.INSTANCE);
        stack.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, Boolean.TRUE);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(flagTag(DUST_TAG)));
        stack.set(DataComponents.TOOLTIP_DISPLAY,
                TooltipDisplay.DEFAULT.withHidden(DataComponents.UNBREAKABLE, true));
        return stack;
    }

    private static CompoundTag flagTag(String key) {
        CompoundTag tag = new CompoundTag();
        tag.putBoolean(key, true);
        return tag;
    }

    public static boolean isWand(ItemStack stack) {
        return hasFlag(stack, WAND_TAG);
    }

    public static boolean isDust(ItemStack stack) {
        return hasFlag(stack, DUST_TAG);
    }

    private static boolean hasFlag(ItemStack stack, String key) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        return data != null && data.copyTag().getBooleanOr(key, false);
    }

    public static boolean isHoldingWand(ServerPlayer player) {
        return isWand(player.getMainHandItem());
    }

    public static boolean attackBlock(Player player, Level level, InteractionHand hand, BlockPos pos) {
        if (hand != InteractionHand.MAIN_HAND || level.isClientSide() || !(player instanceof ServerPlayer serverPlayer) || !(level instanceof ServerLevel serverLevel)) {
            return false;
        }
        if (isWand(serverPlayer.getMainHandItem())) {
            setCorner(serverPlayer, serverLevel, pos, true);
            return true;
        }
        return false;
    }

    public static boolean useBlock(Player player, Level level, InteractionHand hand, BlockPos pos) {
        if (hand != InteractionHand.MAIN_HAND || level.isClientSide() || !(player instanceof ServerPlayer serverPlayer) || !(level instanceof ServerLevel serverLevel)) {
            return false;
        }
        ItemStack held = serverPlayer.getMainHandItem();
        if (isWand(held)) {
            setCorner(serverPlayer, serverLevel, pos, false);
            return true;
        }
        if (isDust(held)) {
            serverLevel.playSound(null, pos, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 2.0F, 1.97F);
            ModdedDustRevealer.reveal(serverPlayer, serverLevel, pos);
            return true;
        }
        return false;
    }

    private static void setCorner(ServerPlayer player, ServerLevel level, BlockPos pos, boolean first) {
        ResourceKey<Level> dimension = level.dimension();
        BlockPos corner = pos.immutable();
        SELECTIONS.compute(player.getUUID(), (UUID uuid, Selection existing) -> {
            BlockPos other = existing != null && existing.dimension().equals(dimension) ? (first ? existing.second() : existing.first()) : null;
            return first ? new Selection(dimension, corner, other) : new Selection(dimension, other, corner);
        });
        level.playSound(null, pos, SoundEvents.END_PORTAL_FRAME_FILL, SoundSource.PLAYERS, 1.0F, first ? 0.67F : 1.17F);
        player.sendOverlayMessage(Component.literal(IrisLanguage.plain(
                RuntimeUiMessages.WAND_POSITION_SET,
                MessageArgument.trusted("position", first ? 1 : 2),
                MessageArgument.trusted("x", corner.getX()),
                MessageArgument.trusted("y", corner.getY()),
                MessageArgument.trusted("z", corner.getZ())
        )));
    }

    public static Selection selection(ServerPlayer player) {
        Selection selection = SELECTIONS.get(player.getUUID());
        if (selection == null || !selection.complete() || !selection.dimension().equals(player.level().dimension())) {
            return null;
        }
        return selection;
    }

    public static void setSelection(ServerPlayer player, BlockPos first, BlockPos second) {
        SELECTIONS.put(player.getUUID(), new Selection(player.level().dimension(), first.immutable(), second.immutable()));
    }

    public static void clearAll() {
        SELECTIONS.clear();
        ModdedDustRevealer.clear();
        ModdedWhatCommands.clear();
    }

    public static void serverTick(MinecraftServer server) {
        tickCounter++;
        if (tickCounter % DRAW_INTERVAL_TICKS != 0) {
            return;
        }
        try {
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                if (!isHoldingWand(player)) {
                    continue;
                }
                Selection selection = selection(player);
                if (selection == null) {
                    continue;
                }
                draw(player.level(), player, selection);
            }
        } catch (Throwable e) {
            ModdedIrisLog.error("Iris wand selection draw failed", e);
        }
    }

    private static void draw(ServerLevel level, ServerPlayer player, Selection selection) {
        BlockPos min = selection.min();
        BlockPos max = selection.max();
        double lowX = min.getX();
        double lowY = min.getY();
        double lowZ = min.getZ();
        double highX = max.getX() + 1;
        double highY = max.getY() + 1;
        double highZ = max.getZ() + 1;
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
        double px = player.getX();
        double py = player.getY();
        double pz = player.getZ();
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
                float hue = (float) (0.5F + (Math.sin((x + y + z + (player.tickCount / 2.0F)) / 20.0F) / 2.0D));
                Color color = Color.getHSBColor(hue, 1.0F, 1.0F);
                DustParticleOptions options = new DustParticleOptions(color.getRGB() & 0xFFFFFF, 0.9F);
                level.sendParticles(player, options, true, true, x, y, z, 1, 0.0D, 0.0D, 0.0D, 0.0D);
                sent++;
                if (sent >= DRAW_POINT_CAP) {
                    return;
                }
            }
        }
    }
}
