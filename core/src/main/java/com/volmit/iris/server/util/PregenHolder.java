package com.volmit.iris.server.util;

import com.volmit.iris.core.pregenerator.PregenListener;
import com.volmit.iris.server.packet.work.PregenPacket;
import com.volmit.iris.util.collection.KList;
import com.volmit.iris.util.collection.KMap;
import com.volmit.iris.util.math.Position2;
import lombok.AccessLevel;
import lombok.Getter;

import java.util.UUID;
import java.util.function.Consumer;

@Getter
public class PregenHolder {
    private final UUID id;
    private final int x, z, r;
    @Getter(AccessLevel.NONE)
    private final KList<Position2> chunks = new KList<>();
    private final PregenListener listener;

    public PregenHolder(PregenPacket packet, int r, boolean fill, PregenListener listener) {
        this.id = packet.getId();
        this.x = packet.getX();
        this.z = packet.getZ();
        this.r = r;
        this.listener = listener;

        if (fill)
            iterate(chunks::add);
    }

    public void put(KMap<UUID, PregenHolder> holders) {
        holders.put(id, this);
    }

    public synchronized boolean remove(int x, int z) {
        chunks.remove(new Position2(x, z));
        boolean b = chunks.isEmpty();
        if (b && listener != null) listener.onRegionGenerated(x, z);
        return b;
    }

    public void iterate(Consumer<Position2> consumer) {
        int cX = x << 5;
        int cZ = z << 5;
        for (int x = -r; x <= r; x++) {
            for (int z = -r; z <= r; z++) {
                if (x == 0 && z == 0) {
                    for (int xx = 0; xx < 32; xx++) {
                        for (int zz = 0; zz < 32; zz++) {
                            consumer.accept(new Position2(x + cX + xx, z + cZ + zz));
                        }
                    }
                    continue;
                }

                consumer.accept(new Position2(x + cX + x < 0 ? 0 : 32, z + cZ + z < 0 ? 0 : 32));
            }
        }
    }
}
