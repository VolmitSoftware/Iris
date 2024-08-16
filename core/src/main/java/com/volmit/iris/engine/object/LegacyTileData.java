package com.volmit.iris.engine.object;

import com.volmit.iris.Iris;
import com.volmit.iris.util.collection.KList;
import com.volmit.iris.util.scheduling.J;
import org.bukkit.DyeColor;
import org.bukkit.block.*;
import org.bukkit.block.banner.Pattern;
import org.bukkit.block.banner.PatternType;
import org.bukkit.entity.EntityType;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.Map;

public class LegacyTileData extends TileData {
    private static final Map<Short, Handler> legacy = Map.of(
            (short) 0, new SignHandler(),
            (short) 1, new SpawnerHandler(),
            (short) 2, new BannerHandler());
    private final short id;
    private final KList<Object> properties;

    public LegacyTileData(DataInputStream in) throws IOException {
        id = in.readShort();
        var handler = legacy.get(id);
        if (handler == null)
            throw new IOException("Unknown tile type: " + id);
        properties = handler.read(in);
    }

    @Override
    public void toBukkit(Block block) {
        var handler = legacy.get(id);
        J.s(() -> handler.toBukkit(properties, block));
    }

    @Override
    public void toBinary(DataOutputStream out) throws IOException {
        out.writeShort(id);
        legacy.get(id).toBinary(properties, out);
    }

    private interface Handler {
        KList<Object> read(DataInputStream in) throws IOException;
        void toBinary(KList<Object> list, DataOutputStream out) throws IOException;
        void toBukkit(KList<Object> list, Block block);
    }

    private static class SignHandler implements Handler {
        @Override
        public KList<Object> read(DataInputStream in) throws IOException {
            return new KList<>()
                    .qadd(in.readUTF())
                    .qadd(in.readUTF())
                    .qadd(in.readUTF())
                    .qadd(in.readUTF())
                    .qadd(DyeColor.values()[in.readByte()]);
        }

        @Override
        public void toBinary(KList<Object> list, DataOutputStream out) throws IOException {
            out.writeUTF((String) list.get(0));
            out.writeUTF((String) list.get(1));
            out.writeUTF((String) list.get(2));
            out.writeUTF((String) list.get(3));
            out.writeByte(((DyeColor) list.get(4)).ordinal());
        }

        @Override
        public void toBukkit(KList<Object> list, Block block) {
            Sign sign = (Sign) block.getState();
            sign.setLine(0, (String) list.get(0));
            sign.setLine(1, (String) list.get(1));
            sign.setLine(2, (String) list.get(2));
            sign.setLine(3, (String) list.get(3));
            sign.setColor((DyeColor) list.get(4));
            sign.update();
        }
    }

    private static class SpawnerHandler implements Handler {
        @Override
        public KList<Object> read(DataInputStream in) throws IOException {
            return new KList<>().qadd(EntityType.values()[in.readShort()]);
        }

        @Override
        public void toBinary(KList<Object> list, DataOutputStream out) throws IOException {
            out.writeShort(((EntityType) list.get(0)).ordinal());
        }

        @Override
        public void toBukkit(KList<Object> list, Block block) {
            CreatureSpawner spawner = (CreatureSpawner) block.getState();
            spawner.setSpawnedType((EntityType) list.get(0));
            spawner.update();
        }
    }

    private static class BannerHandler implements Handler {
        @Override
        public KList<Object> read(DataInputStream in) throws IOException {
            KList<Object> list = new KList<>();
            list.add(DyeColor.values()[in.readByte()]);
            int listSize = in.readByte();
            var patterns = new KList<>();

            for (int i = 0; i < listSize; i++) {
                DyeColor color = DyeColor.values()[in.readByte()];
                PatternType type = PatternType.values()[in.readByte()];
                patterns.add(new Pattern(color, type));
            }

            list.add(patterns);
            return list;
        }

        @Override
        public void toBinary(KList<Object> list, DataOutputStream out) throws IOException {
            out.writeByte(((DyeColor) list.get(0)).ordinal());
            out.writeByte(((List<Pattern>) list.get(1)).size());
            for (Pattern i : (List<Pattern>) list.get(1)) {
                out.writeByte(i.getColor().ordinal());
                out.writeByte(i.getPattern().ordinal());
            }
        }

        @Override
        public void toBukkit(KList<Object> list, Block block) {
            Banner banner = (Banner) block.getState();
            banner.setBaseColor((DyeColor) list.get(0));
            banner.setPatterns((List<Pattern>) list.get(1));
            banner.update();
        }
    }
}
