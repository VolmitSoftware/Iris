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
import com.volmit.tooling.world.BlockChange.Cause;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.material.Fluid;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The ticks of a tool world. Each kind of tick names the cause of the block changes inside it, so the tool is asked about
 * them as that cause; and the tool world keeps its weather off.
 */
@Mixin(ServerLevel.class)
public abstract class ServerLevelToolMixin {
    @Inject(method = "tickFluid", at = @At("HEAD"))
    private void iris$beginFluidTick(BlockPos position, Fluid fluid, CallbackInfo info) {
        ToolWorldGuard.enter((ServerLevel) (Object) this, Cause.FLUID, null, false);
    }

    @Inject(method = "tickFluid", at = @At("RETURN"))
    private void iris$endFluidTick(BlockPos position, Fluid fluid, CallbackInfo info) {
        ToolWorldGuard.exit((ServerLevel) (Object) this);
    }

    @Inject(method = "tickBlock", at = @At("HEAD"))
    private void iris$beginBlockTick(BlockPos position, Block block, CallbackInfo info) {
        ToolWorldGuard.enter((ServerLevel) (Object) this, null, null, false);
    }

    @Inject(method = "tickBlock", at = @At("RETURN"))
    private void iris$endBlockTick(BlockPos position, Block block, CallbackInfo info) {
        ToolWorldGuard.exit((ServerLevel) (Object) this);
    }

    @Inject(method = "tickChunk", at = @At("HEAD"))
    private void iris$beginRandomTick(LevelChunk chunk, int randomTickSpeed, CallbackInfo info) {
        ToolWorldGuard.enter((ServerLevel) (Object) this, null, null, false);
    }

    @Inject(method = "tickChunk", at = @At("RETURN"))
    private void iris$endRandomTick(LevelChunk chunk, int randomTickSpeed, CallbackInfo info) {
        ToolWorldGuard.exit((ServerLevel) (Object) this);
    }

    @Inject(method = "tickNonPassenger", at = @At("HEAD"))
    private void iris$beginEntityTick(Entity entity, CallbackInfo info) {
        ToolWorldGuard.enter((ServerLevel) (Object) this, Cause.ENTITY, entity, false);
    }

    @Inject(method = "tickNonPassenger", at = @At("RETURN"))
    private void iris$endEntityTick(Entity entity, CallbackInfo info) {
        ToolWorldGuard.exit((ServerLevel) (Object) this);
    }

    @Inject(method = "advanceWeatherCycle", at = @At("HEAD"), cancellable = true)
    private void iris$keepToolWorldWeatherOff(CallbackInfo info) {
        if (ToolWorldGuard.isToolWorld((ServerLevel) (Object) this)) {
            info.cancel();
        }
    }
}
