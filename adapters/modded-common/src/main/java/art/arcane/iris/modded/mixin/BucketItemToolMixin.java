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
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Buckets in a tool world. Filling a bucket changes the block it is aimed at, so each change in its use is put to the tool as
 * a bucket change. Emptying one is put to the tool before the fluid is placed, once.
 */
@Mixin(BucketItem.class)
public abstract class BucketItemToolMixin {
    @Inject(method = "use", at = @At("HEAD"))
    private void iris$beginBucketUse(Level level, Player player, InteractionHand hand, CallbackInfoReturnable<InteractionResult> result) {
        ToolWorldGuard.enter(level, Cause.BUCKET, player, false);
    }

    @Inject(method = "use", at = @At("RETURN"))
    private void iris$endBucketUse(Level level, Player player, InteractionHand hand, CallbackInfoReturnable<InteractionResult> result) {
        ToolWorldGuard.exit(level);
    }

    @Inject(method = "emptyContents", at = @At("HEAD"), cancellable = true)
    private void iris$askAboutEmptying(LivingEntity user, Level level, BlockPos position, BlockHitResult hit,
                                       CallbackInfoReturnable<Boolean> result) {
        if (!ToolWorldGuard.isToolWorld(level)) {
            return;
        }
        if (!ToolWorldHooks.bucketEmptyAllowed(level, user, position)) {
            result.setReturnValue(false);
            return;
        }
        ToolWorldGuard.enter(level, Cause.BUCKET, user, true);
    }

    @Inject(method = "emptyContents", at = @At("RETURN"))
    private void iris$endEmptying(LivingEntity user, Level level, BlockPos position, BlockHitResult hit,
                                  CallbackInfoReturnable<Boolean> result) {
        ToolWorldGuard.exit(level);
    }
}
