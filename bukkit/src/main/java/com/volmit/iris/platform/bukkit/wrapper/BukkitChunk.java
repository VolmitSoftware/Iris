package com.volmit.iris.platform.bukkit.wrapper;

import com.volmit.iris.platform.PlatformChunk;
import org.bukkit.Chunk;

public class BukkitChunk implements PlatformChunk {
    private final Chunk delegate;

    private BukkitChunk(Chunk delegate) {
        this.delegate = delegate;
    }

    @Override
    public int getX() {
        return delegate.getX();
    }

    @Override
    public int getZ() {
        return delegate.getZ();
    }

    @Override
    public void unload(boolean save, boolean force) {
        if(force) {
            delegate.getPluginChunkTickets().forEach(delegate::removePluginChunkTicket);
        }

        delegate.unload(save);
    }

    public static BukkitChunk of(Chunk chunk) {
        return new BukkitChunk(chunk);
    }
}
