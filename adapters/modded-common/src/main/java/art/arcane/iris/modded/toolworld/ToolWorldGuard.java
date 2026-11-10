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

import com.volmit.block.BlockState;
import com.volmit.geometry.BlockPosition;
import com.volmit.tooling.world.BlockChange;
import com.volmit.tooling.world.BlockChange.Cause;
import com.volmit.tooling.world.ToolPlayer;
import com.volmit.tooling.world.ToolUse;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BonemealableBlock;

import java.util.List;
import java.util.Optional;

/**
 * Where the vanilla code paths of a tool world ask the tool. Every change to a block of a tool world passes
 * {@link #setBlockAllowed}: the host's own writes pass, a change inside a {@link Scope} that names its cause is put to the
 * tool as that cause, and a change nobody named a cause for is refused, so the world stays frozen where Bukkit would not
 * have been asked. The scopes are entered by the mixins at the places vanilla starts a kind of change.
 */
public final class ToolWorldGuard {
    private static final ThreadLocal<Scope> CURRENT = new ThreadLocal<>();
    private static final ThreadLocal<Integer> HOST_WRITES = ThreadLocal.withInitial(() -> 0);

    private ToolWorldGuard() {
    }

    /** What a thread is doing to a tool world, which tells its block changes apart. */
    private static final class Scope {
        private final Level level;
        private final Cause cause;
        private final Entity actor;
        private final boolean approved;
        private final Scope previous;

        private Scope(Level level, Cause cause, Entity actor, boolean approved, Scope previous) {
            this.level = level;
            this.cause = cause;
            this.actor = actor;
            this.approved = approved;
            this.previous = previous;
        }
    }

    /** Whether {@code level} is a tool world. Cheap enough to ask for every block change of every world. */
    public static boolean isToolWorld(Level level) {
        return ToolWorldRegistry.INSTANCE.hasWorlds() && level instanceof ServerLevel serverLevel
                && ToolWorldRegistry.INSTANCE.forLevel(serverLevel).isPresent();
    }

    /** Runs the host's own write to a tool world, which no tool is asked about. */
    static void hostWrite(Runnable write) {
        HOST_WRITES.set(HOST_WRITES.get() + 1);
        try {
            write.run();
        } finally {
            HOST_WRITES.set(HOST_WRITES.get() - 1);
        }
    }

    /**
     * Starts a kind of change in {@code level}. A {@code null} cause is told apart per block by what changes, as a random tick
     * does. {@code approved} lets everything inside through, for what the tool was asked about already.
     *
     * @return whether {@code level} is a tool world, which is whether {@link #exit} must follow
     */
    public static boolean enter(Level level, Cause cause, Entity actor, boolean approved) {
        if (!isToolWorld(level)) {
            return false;
        }
        CURRENT.set(new Scope(level, cause, actor, approved, CURRENT.get()));
        return true;
    }

    /** Ends the innermost scope of {@code level}; does nothing for a level that is no tool world. */
    public static void exit(Level level) {
        Scope scope = CURRENT.get();
        if (scope != null && scope.level == level) {
            CURRENT.set(scope.previous);
        }
    }

    /** Drops scopes a failed call left behind. */
    public static void resetScopes() {
        CURRENT.remove();
        HOST_WRITES.remove();
    }

    /** Whether the block at {@code position} of {@code level} may become {@code newState} now. */
    public static boolean setBlockAllowed(Level level, BlockPos position, net.minecraft.world.level.block.state.BlockState newState) {
        if (!isToolWorld(level) || HOST_WRITES.get() > 0) {
            return true;
        }
        Scope scope = CURRENT.get();
        if (scope == null || scope.level != level) {
            return false;
        }
        if (scope.approved) {
            return true;
        }
        net.minecraft.world.level.block.state.BlockState old = level.getBlockState(position);
        if (old == newState) {
            return true;
        }
        Cause cause = scope.cause != null ? scope.cause : classifyTickChange(old, newState);
        Optional<BlockState> placed = cause == Cause.PLACE ? Optional.of(ToolBlocks.apiStateOf(newState)) : Optional.empty();
        return allowed(level, cause, actorPlayer(scope.actor), List.of(ToolBlocks.positionOf(position)), placed);
    }

    private static Cause classifyTickChange(net.minecraft.world.level.block.state.BlockState old,
                                            net.minecraft.world.level.block.state.BlockState replacement) {
        if (old.is(BlockTags.FIRE) || replacement.is(BlockTags.FIRE)) {
            return Cause.FIRE;
        }
        if (old.getBlock() instanceof BonemealableBlock) {
            return Cause.GROWTH;
        }
        return Cause.PHYSICS;
    }

    /** Whether the tool lets a change of {@code cause} to {@code positions} happen; changes outside tool worlds are none of the host's business. */
    public static boolean allowed(Level level, Cause cause, Optional<ToolPlayer> player, List<BlockPosition> positions,
                                  Optional<BlockState> placed) {
        return allowed(level, cause, player, positions, positions, placed);
    }

    public static boolean allowed(Level level, Cause cause, Optional<ToolPlayer> player, List<BlockPosition> positions,
                                  List<BlockPosition> changed, Optional<BlockState> placed) {
        Optional<ModdedToolWorld> world = worldOf(level);
        if (world.isEmpty()) {
            return true;
        }
        BlockChange change = new BlockChange(cause, positions, changed, player, placed);
        return world.get().ask(listener -> listener.blockChanging(change), false);
    }

    /** Whether the tool lets {@code player} use their hand as {@code use} describes. */
    public static boolean usingAllowed(ServerPlayer player, ToolUse use) {
        Optional<ModdedToolWorld> world = worldOf(player.level());
        if (world.isEmpty()) {
            return true;
        }
        ToolPlayer toolPlayer = ModdedToolWorldService.players().of(player);
        return world.get().ask(listener -> listener.used(toolPlayer, use), false);
    }

    public static Optional<ToolPlayer> toolPlayerOf(Entity entity) {
        return actorPlayer(entity);
    }

    static Optional<ModdedToolWorld> worldOf(Level level) {
        if (!ToolWorldRegistry.INSTANCE.hasWorlds() || !(level instanceof ServerLevel serverLevel)) {
            return Optional.empty();
        }
        return ToolWorldRegistry.INSTANCE.forLevel(serverLevel);
    }

    private static Optional<ToolPlayer> actorPlayer(Entity actor) {
        if (actor instanceof ServerPlayer player) {
            return Optional.of(ModdedToolWorldService.players().of(player));
        }
        return Optional.empty();
    }
}
