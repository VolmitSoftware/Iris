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
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.SaplingBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** A sapling growing into a tree in a tool world changes blocks as growth. */
@Mixin(SaplingBlock.class)
public abstract class SaplingBlockToolMixin {
    @Inject(method = "advanceTree", at = @At("HEAD"))
    private void iris$beginTreeGrowth(ServerLevel level, BlockPos position, BlockState state, RandomSource random, CallbackInfo info) {
        ToolWorldGuard.enter(level, Cause.GROWTH, null, false);
    }

    @Inject(method = "advanceTree", at = @At("RETURN"))
    private void iris$endTreeGrowth(ServerLevel level, BlockPos position, BlockState state, RandomSource random, CallbackInfo info) {
        ToolWorldGuard.exit(level);
    }
}
