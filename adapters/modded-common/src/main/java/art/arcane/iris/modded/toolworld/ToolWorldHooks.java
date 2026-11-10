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

package art.arcane.iris.modded.toolworld;

import art.arcane.iris.modded.structure.PrivateFields;
import com.volmit.geometry.BlockPosition;
import com.volmit.tooling.world.BlockChange.Cause;
import com.volmit.tooling.world.ToolPlayer;
import com.volmit.tooling.world.ToolUse;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.CompoundContainer;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.piston.PistonStructureResolver;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** What the mixins ask of a tool world for each kind of thing a player or the world does; all of it answers "allowed" outside tool worlds. */
public final class ToolWorldHooks {
    private static final String COMMAND_PREFIX = "/";
    private static final double LEFT_CLICK_REACH_BLOCKS = 5.0D;

    private ToolWorldHooks() {
    }

    // What players do with their hands.

    public static boolean rightClickBlockAllowed(ServerPlayer player, InteractionHand hand, ItemStack stack, BlockHitResult hit) {
        if (!ToolWorldGuard.isToolWorld(player.level())) {
            return true;
        }
        return ToolWorldGuard.usingAllowed(player, new ToolUse(handOf(hand), ToolUse.Action.RIGHT_CLICK_BLOCK,
                ToolItems.read(stack), Optional.of(ToolBlocks.positionOf(hit.getBlockPos())), ToolBlocks.faceOf(hit.getDirection())));
    }

    public static boolean rightClickAirAllowed(ServerPlayer player, InteractionHand hand, ItemStack stack) {
        if (!ToolWorldGuard.isToolWorld(player.level())) {
            return true;
        }
        return ToolWorldGuard.usingAllowed(player, new ToolUse(handOf(hand), ToolUse.Action.RIGHT_CLICK_AIR,
                ToolItems.read(stack), Optional.empty(), Optional.empty()));
    }

    /** Asks the tool about a left click on a block; a refusal puts the block back on the player's screen. */
    public static boolean leftClickBlockAllowed(ServerPlayer player, BlockPos at, Direction face, int sequence) {
        if (!ToolWorldGuard.isToolWorld(player.level())) {
            return true;
        }
        ToolUse use = new ToolUse(ToolUse.Hand.MAIN, ToolUse.Action.LEFT_CLICK_BLOCK,
                ToolItems.read(player.getMainHandItem()), Optional.of(ToolBlocks.positionOf(at)), ToolBlocks.faceOf(face));
        if (ToolWorldGuard.usingAllowed(player, use)) {
            return true;
        }
        player.connection.send(new ClientboundBlockUpdatePacket(at, player.level().getBlockState(at)));
        player.connection.ackBlockChangesUpTo(sequence);
        return false;
    }

    /** Tells the tool about a swing at nothing; a swing at a block is the left click on the block. */
    public static void leftClickAir(ServerPlayer player, InteractionHand hand) {
        if (!ToolWorldGuard.isToolWorld(player.level())) {
            return;
        }
        HitResult target = player.pick(LEFT_CLICK_REACH_BLOCKS, 1.0F, false);
        if (target.getType() == HitResult.Type.BLOCK) {
            return;
        }
        ToolWorldGuard.usingAllowed(player, new ToolUse(handOf(hand), ToolUse.Action.LEFT_CLICK_AIR,
                ToolItems.read(player.getItemInHand(hand)), Optional.empty(), Optional.empty()));
    }

    private static ToolUse.Hand handOf(InteractionHand hand) {
        return hand == InteractionHand.OFF_HAND ? ToolUse.Hand.OFF : ToolUse.Hand.MAIN;
    }

    // Block changes a player starts.

    public static boolean placeAllowed(Level level, Entity actor, BlockPos at, BlockState placed) {
        if (!ToolWorldGuard.isToolWorld(level)) {
            return true;
        }
        Optional<com.volmit.block.BlockState> state = placed == null ? Optional.empty() : Optional.of(ToolBlocks.apiStateOf(placed));
        return ToolWorldGuard.allowed(level, Cause.PLACE, ToolWorldGuard.toolPlayerOf(actor), List.of(ToolBlocks.positionOf(at)), state);
    }

    public static boolean breakAllowed(Level level, ServerPlayer actor, BlockPos at) {
        return ToolWorldGuard.allowed(level, Cause.BREAK, ToolWorldGuard.toolPlayerOf(actor), List.of(ToolBlocks.positionOf(at)), Optional.empty());
    }

    public static boolean bucketEmptyAllowed(Level level, LivingEntity actor, BlockPos at) {
        return ToolWorldGuard.allowed(level, Cause.BUCKET, ToolWorldGuard.toolPlayerOf(actor), List.of(ToolBlocks.positionOf(at)), Optional.empty());
    }

    // Pistons.

    /** Asks about each block a piston moves and the block each of them enters and leaves, and about the piston itself. */
    public static boolean pistonMoveAllowed(Level level, BlockPos piston, Direction direction, boolean extending) {
        if (!ToolWorldGuard.isToolWorld(level)) {
            return true;
        }
        PistonStructureResolver resolver = new PistonStructureResolver(level, piston, direction, extending);
        if (!resolver.resolve()) {
            return true;
        }
        List<BlockPos> moved = new ArrayList<>(resolver.getToPush());
        moved.addAll(resolver.getToDestroy());
        List<BlockPosition> checked = new ArrayList<>();
        List<BlockPosition> changed = new ArrayList<>();
        changed.add(ToolBlocks.positionOf(piston));
        changed.add(ToolBlocks.positionOf(piston.relative(direction)));
        for (BlockPos block : moved) {
            checked.add(ToolBlocks.positionOf(block));
            checked.add(ToolBlocks.positionOf(block.relative(direction)));
            checked.add(ToolBlocks.positionOf(block.relative(direction.getOpposite())));
            changed.add(ToolBlocks.positionOf(block));
            changed.add(ToolBlocks.positionOf(block.relative(direction)));
        }
        if (checked.isEmpty()) {
            return true;
        }
        return ToolWorldGuard.allowed(level, Cause.PISTON, Optional.empty(),
                checked.stream().distinct().toList(), changed.stream().distinct().toList(), Optional.empty());
    }

    // Containers and machines.

    public static boolean containerClickAllowed(ServerPlayer player, AbstractContainerMenu menu) {
        if (!ToolWorldGuard.isToolWorld(player.level())) {
            return true;
        }
        List<BlockPosition> blocks = containerBlocks(menu);
        if (blocks.isEmpty()) {
            return true;
        }
        return ToolWorldGuard.allowed(player.level(), Cause.CONTAINER, ToolWorldGuard.toolPlayerOf(player), blocks, Optional.empty());
    }

    /** Tells the tool that the inventory of a container block was closed, which may have changed what is in it. */
    public static void containerClosed(Entity closer, AbstractContainerMenu menu) {
        Optional<ModdedToolWorld> world = ToolWorldGuard.worldOf(closer.level());
        if (world.isEmpty() || !(closer instanceof ServerPlayer player)) {
            return;
        }
        Optional<ToolPlayer> toolPlayer = ToolWorldGuard.toolPlayerOf(player);
        for (BlockPosition block : containerBlocks(menu)) {
            world.get().tell(listener -> listener.containerChanged(toolPlayer, block));
        }
    }

    /** The blocks the container inventory of {@code menu} is made of: none for a player's or a menu, two for a double chest. */
    private static List<BlockPosition> containerBlocks(AbstractContainerMenu menu) {
        List<BlockPosition> blocks = new ArrayList<>(2);
        for (Slot slot : menu.slots) {
            addBlocksOf(blocks, slot.container);
        }
        return blocks;
    }

    private static void addBlocksOf(List<BlockPosition> blocks, Container container) {
        if (container instanceof CompoundContainer compound) {
            addBlocksOf(blocks, PrivateFields.read(compound, "container1", Container.class));
            addBlocksOf(blocks, PrivateFields.read(compound, "container2", Container.class));
            return;
        }
        if (container instanceof BlockEntity blockEntity) {
            BlockPosition at = ToolBlocks.positionOf(blockEntity.getBlockPos());
            if (!blocks.contains(at)) {
                blocks.add(at);
            }
        }
    }

    public static boolean machineAllowed(Level level, BlockPos at) {
        if (!ToolWorldGuard.isToolWorld(level)) {
            return true;
        }
        return ToolWorldGuard.allowed(level, Cause.MACHINE, Optional.empty(), List.of(ToolBlocks.positionOf(at)), Optional.empty());
    }

    // Commands.

    /** Asks the tool world a player stands in about their command; a command from anywhere else is every tool world's to hear. */
    public static boolean commandAllowed(CommandSourceStack source, String typed) {
        if (!ToolWorldRegistry.INSTANCE.hasWorlds()) {
            return true;
        }
        String line = typed.startsWith(COMMAND_PREFIX) ? typed.substring(COMMAND_PREFIX.length()) : typed;
        ServerPlayer player = source.getPlayer();
        if (player != null) {
            return playerCommandAllowed(player, line);
        }
        return ToolWorldRegistry.INSTANCE.all().stream().allMatch(world -> world.ask(listener -> listener.command(Optional.empty(), line), true));
    }

    private static boolean playerCommandAllowed(ServerPlayer player, String line) {
        if (!(player.level() instanceof ServerLevel level)) {
            return true;
        }
        Optional<ModdedToolWorld> world = ToolWorldRegistry.INSTANCE.forLevel(level);
        if (world.isEmpty()) {
            return true;
        }
        ToolPlayer toolPlayer = ModdedToolWorldService.toolPlayerOf(player);
        return world.get().ask(listener -> listener.command(Optional.of(toolPlayer), line), true);
    }
}
