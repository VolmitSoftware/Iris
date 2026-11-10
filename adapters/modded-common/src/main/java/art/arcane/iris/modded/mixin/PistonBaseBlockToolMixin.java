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
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.piston.PistonBaseBlock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** A piston in a tool world asks the tool about every block it moves before it moves any, so it never moves half a structure. */
@Mixin(PistonBaseBlock.class)
public abstract class PistonBaseBlockToolMixin {
    @Inject(method = "moveBlocks", at = @At("HEAD"), cancellable = true)
    private void iris$askAboutPiston(Level level, BlockPos position, Direction direction, boolean extending,
                                     CallbackInfoReturnable<Boolean> result) {
        if (!ToolWorldGuard.isToolWorld(level)) {
            return;
        }
        if (!ToolWorldHooks.pistonMoveAllowed(level, position, direction, extending)) {
            result.setReturnValue(false);
            return;
        }
        ToolWorldGuard.enter(level, Cause.PISTON, null, true);
    }

    @Inject(method = "moveBlocks", at = @At("RETURN"))
    private void iris$endPiston(Level level, BlockPos position, Direction direction, boolean extending,
                                CallbackInfoReturnable<Boolean> result) {
        ToolWorldGuard.exit(level);
    }
}
