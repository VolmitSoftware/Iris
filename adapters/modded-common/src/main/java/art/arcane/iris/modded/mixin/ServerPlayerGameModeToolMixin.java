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

package art.arcane.iris.modded.mixin;

import art.arcane.iris.modded.toolworld.ToolWorldGuard;
import art.arcane.iris.modded.toolworld.ToolWorldHooks;
import com.volmit.tooling.world.BlockChange.Cause;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerPlayerGameMode;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * What a player does with their hands in a tool world: the tool is asked about a click before it happens, and about a block
 * that is broken before it breaks. What a click that the tool allows then changes is let through, since the tool allowed it.
 */
@Mixin(ServerPlayerGameMode.class)
public abstract class ServerPlayerGameModeToolMixin {
    @Shadow
    protected ServerLevel level;

    @Shadow
    @Final
    protected ServerPlayer player;

    @Inject(method = "destroyBlock", at = @At("HEAD"), cancellable = true)
    private void iris$askAboutBreaking(BlockPos position, CallbackInfoReturnable<Boolean> result) {
        if (!ToolWorldGuard.isToolWorld(level)) {
            return;
        }
        if (!ToolWorldHooks.breakAllowed(level, player, position)) {
            result.setReturnValue(false);
            return;
        }
        ToolWorldGuard.enter(level, Cause.BREAK, player, true);
    }

    @Inject(method = "destroyBlock", at = @At("RETURN"))
    private void iris$endBreaking(BlockPos position, CallbackInfoReturnable<Boolean> result) {
        ToolWorldGuard.exit(level);
    }

    @Inject(method = "useItemOn", at = @At("HEAD"), cancellable = true)
    private void iris$askAboutUsingOnBlock(ServerPlayer user, Level world, ItemStack stack, InteractionHand hand, BlockHitResult hit,
                                           CallbackInfoReturnable<InteractionResult> result) {
        if (!ToolWorldGuard.isToolWorld(world)) {
            return;
        }
        if (!ToolWorldHooks.rightClickBlockAllowed(user, hand, stack, hit)) {
            result.setReturnValue(InteractionResult.FAIL);
            return;
        }
        ToolWorldGuard.enter(world, null, user, true);
    }

    @Inject(method = "useItemOn", at = @At("RETURN"))
    private void iris$endUsingOnBlock(ServerPlayer user, Level world, ItemStack stack, InteractionHand hand, BlockHitResult hit,
                                      CallbackInfoReturnable<InteractionResult> result) {
        ToolWorldGuard.exit(world);
    }

    @Inject(method = "useItem", at = @At("HEAD"), cancellable = true)
    private void iris$askAboutUsing(ServerPlayer user, Level world, ItemStack stack, InteractionHand hand,
                                    CallbackInfoReturnable<InteractionResult> result) {
        if (!ToolWorldGuard.isToolWorld(world)) {
            return;
        }
        if (!ToolWorldHooks.rightClickAirAllowed(user, hand, stack)) {
            result.setReturnValue(InteractionResult.FAIL);
            return;
        }
        ToolWorldGuard.enter(world, null, user, true);
    }

    @Inject(method = "useItem", at = @At("RETURN"))
    private void iris$endUsing(ServerPlayer user, Level world, ItemStack stack, InteractionHand hand,
                               CallbackInfoReturnable<InteractionResult> result) {
        ToolWorldGuard.exit(world);
    }

    @Inject(method = "handleBlockBreakAction", at = @At("HEAD"), cancellable = true)
    private void iris$askAboutLeftClick(BlockPos position, ServerboundPlayerActionPacket.Action action, Direction face,
                                        int maxBuildHeight, int sequence, CallbackInfo info) {
        if (!ToolWorldGuard.isToolWorld(level)) {
            return;
        }
        boolean startsDigging = action == ServerboundPlayerActionPacket.Action.START_DESTROY_BLOCK;
        if (startsDigging && !ToolWorldHooks.leftClickBlockAllowed(player, position, face, sequence)) {
            info.cancel();
            return;
        }
        ToolWorldGuard.enter(level, null, player, true);
    }

    @Inject(method = "handleBlockBreakAction", at = @At("RETURN"))
    private void iris$endLeftClick(BlockPos position, ServerboundPlayerActionPacket.Action action, Direction face,
                                   int maxBuildHeight, int sequence, CallbackInfo info) {
        ToolWorldGuard.exit(level);
    }
}
