package art.arcane.iris.core.service;

import com.volmit.geometry.BlockPosition;
import com.volmit.tooling.world.BlockChange;
import com.volmit.tooling.world.ToolPlayer;
import com.volmit.tooling.world.ToolUse;
import com.volmit.tooling.world.ToolWorldListener;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * What a Jigsaw Studio world hears from its host: the world's players, blocks and commands, handed to the studio service
 * that decides. The studio's id is the id of its request.
 */
final class JigsawStudioWorldListener implements ToolWorldListener {
    private final JigsawStudioService service;
    private final UUID studioId;

    JigsawStudioWorldListener(JigsawStudioService service, UUID studioId) {
        this.service = Objects.requireNonNull(service, "Jigsaw Studio service");
        this.studioId = Objects.requireNonNull(studioId, "Jigsaw Studio ID");
    }

    @Override
    public void entered(ToolPlayer player) {
        service.playerEntered(player);
    }

    @Override
    public void left(ToolPlayer player) {
        service.playerLeft(player);
    }

    @Override
    public void moved(ToolPlayer player, BlockPosition to) {
        service.playerMoved(player, to);
    }

    @Override
    public void sneakToggled(ToolPlayer player, boolean sneaking) {
        service.sneakToggled(player, sneaking);
    }

    @Override
    public boolean used(ToolPlayer player, ToolUse use) {
        return service.playerUsed(studioId, player, use);
    }

    @Override
    public boolean blockChanging(BlockChange change) {
        return service.blockChanging(studioId, change);
    }

    @Override
    public boolean command(Optional<ToolPlayer> sender, String line) {
        return service.command(studioId, sender, line);
    }

    @Override
    public void chunkLoaded(int chunkX, int chunkZ) {
        service.chunkLoaded(studioId, chunkX, chunkZ);
    }

    @Override
    public Optional<String> closeRefusal() {
        return service.closeRefusal(studioId);
    }

    @Override
    public CompletableFuture<Void> closing(Optional<ToolPlayer> by) {
        return service.closing(studioId, by);
    }

    @Override
    public void closed() {
        service.worldClosed(studioId);
    }
}
