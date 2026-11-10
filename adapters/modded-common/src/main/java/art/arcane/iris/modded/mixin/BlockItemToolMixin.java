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
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** A player placing a block in a tool world: the tool is asked about the block before it is placed, once. */
@Mixin(BlockItem.class)
public abstract class BlockItemToolMixin {
    @Shadow
    public abstract BlockPlaceContext updatePlacementContext(BlockPlaceContext context);

    @Shadow
    protected abstract BlockState getPlacementState(BlockPlaceContext context);

    @Inject(method = "place", at = @At("HEAD"), cancellable = true)
    private void iris$askAboutPlacement(BlockPlaceContext context, CallbackInfoReturnable<InteractionResult> result) {
        if (!ToolWorldGuard.isToolWorld(context.getLevel())) {
            return;
        }
        BlockPlaceContext updated = updatePlacementContext(context);
        if (updated == null) {
            ToolWorldGuard.enter(context.getLevel(), Cause.PLACE, context.getPlayer(), true);
            return;
        }
        if (!ToolWorldHooks.placeAllowed(context.getLevel(), context.getPlayer(), updated.getClickedPos(), getPlacementState(updated))) {
            result.setReturnValue(InteractionResult.FAIL);
            return;
        }
        ToolWorldGuard.enter(context.getLevel(), Cause.PLACE, context.getPlayer(), true);
    }

    @Inject(method = "place", at = @At("RETURN"))
    private void iris$endPlacement(BlockPlaceContext context, CallbackInfoReturnable<InteractionResult> result) {
        ToolWorldGuard.exit(context.getLevel());
    }
}
