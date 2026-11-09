package com.volmit.tooling.world;

import com.volmit.geometry.BlockPosition;
import com.volmit.resource.ResourceId;
import com.volmit.tooling.Sender;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * A player as a tool sees them. Positions are block positions of the world the player stands in, with that world's own Y.
 *
 * <p>Threads. The readers ({@link #world}, {@link #position}, {@link #yaw}, {@link #pitch}, {@link #sneaking},
 * {@link #held}) are valid only on the thread that owns the player, which is where every {@link ToolWorldListener} call
 * about them and every {@link #run} task runs. The methods that change the player ({@link #teleport} and the ones
 * below it) are safe from any thread: the host moves the change onto the owning thread. A player stays one object for
 * as long as they are online, though {@link #id()} is the safer key for a map.
 */
public interface ToolPlayer {
    UUID id();

    /** The player's name as the platform shows it. */
    String name();

    /** The player as a command sender; its messages are plain text, delivered on the thread the platform needs. For styled text use {@link #message}. */
    Sender sender();

    /** The tool world the player stands in; empty when they stand in another world, even another pack's tool world. */
    Optional<ToolWorld> world();

    BlockPosition position();

    /** The direction the player faces, in degrees, as the platform counts them. */
    float yaw();

    float pitch();

    boolean sneaking();

    /**
     * The block the player looks at within {@code maxDistance} blocks, fluids not counting; empty when they look at none.
     * Like the other readers, valid only on the thread that owns the player.
     */
    Optional<BlockPosition> targetBlock(int maxDistance);

    /** Runs {@code task} on the thread that owns the player: at once when the caller is that thread, else later. */
    void run(Runnable task);

    /**
     * Moves the player to the position in {@code world} and completes with whether they arrived. The host remembers
     * where a player came from when they enter a tool world from outside one, and {@link ToolWorld#close()} sends them
     * back there.
     */
    CompletableFuture<Boolean> teleport(ToolWorld world, double x, double y, double z, float yaw, float pitch);

    /**
     * Puts the player in creative mode when {@code on}, remembering the mode they had; turns it off by restoring that
     * mode. The host also restores it when it sends the player out of a tool world.
     */
    void creative(boolean on);

    /** Sends {@code text} to the player's chat, in its colours. */
    void message(ToolText text);

    /** Shows {@code text} above the hotbar. */
    void actionBar(ToolText text);

    /** Plays {@code sound}, such as {@code minecraft:block.note_block.pling}, to this player only, where they stand. */
    void sound(ResourceId sound, float volume, float pitch);

    /**
     * Shows a sidebar with {@code title} and {@code lines}, top to bottom, replacing the one this player has from a tool.
     * An empty title keeps the platform's own sidebar title. The sidebar goes away when the player leaves the tool world.
     */
    void sidebar(ToolText title, List<ToolText> lines);

    /** Removes the sidebar {@link #sidebar} showed. */
    void hideSidebar();

    /** Shows dust particles to this player only. Positions are in the world the player stands in. */
    void particles(List<Particle> dust);

    /**
     * Puts {@code item} in the player's inventory, in the first free slot, and returns whether it fitted. An item that did
     * not fit is not given and not dropped. Called off the player's own thread, the item is given later and the answer
     * is true.
     */
    boolean give(ToolItem item);

    /** The tool item in the player's main hand; empty when the held item is not one, which is when it carries no payload. */
    Optional<ToolItem> held();

    /** Opens {@code menu} for the player, closing the menu they had open. */
    void openMenu(ToolMenu menu);

    /** Closes the menu a tool opened for the player; does nothing when none is open. */
    void closeMenu();
}
