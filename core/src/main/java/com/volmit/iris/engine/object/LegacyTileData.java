package com.volmit.iris.engine.object;

import com.volmit.iris.util.collection.KList;
import com.volmit.iris.util.scheduling.J;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import org.apache.commons.io.function.IOFunction;
import org.bukkit.DyeColor;
import org.bukkit.block.*;
import org.bukkit.block.banner.Pattern;
import org.bukkit.block.banner.PatternType;
import org.bukkit.entity.EntityType;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Map;

@ToString
@EqualsAndHashCode(callSuper = false)
public class LegacyTileData extends TileData {
    private static final Map<Integer, IOFunction<DataInputStream, Handler>> legacy = Map.of(
            0, SignHandler::new,
            1, SpawnerHandler::new,
            2, BannerHandler::new);
    private final int id;
    private final Handler handler;

    public LegacyTileData(DataInputStream in) throws IOException {
        id = in.readShort();
        var factory = legacy.get(id);
        if (factory == null)
            throw new IOException("Unknown tile type: " + id);
        handler = factory.apply(in);
    }

    @Override
    public void toBukkit(Block block) {
        J.s(() -> handler.toBukkit(block));
    }

    @Override
    public void toBinary(DataOutputStream out) throws IOException {
        out.writeShort(id);
        handler.toBinary(out);
    }

    @Override
    public TileData clone() {
        return this;
    }

    private interface Handler {
        void toBinary(DataOutputStream out) throws IOException;
        void toBukkit(Block block);
    }

    @ToString
    @EqualsAndHashCode
    private static class SignHandler implements Handler {
        private final String line1;
        private final String line2;
        private final String line3;
        private final String line4;
        private final DyeColor dyeColor;

        private SignHandler(DataInputStream in) throws IOException {
            line1 = in.readUTF();
            line2 = in.readUTF();
            line3 = in.readUTF();
            line4 = in.readUTF();
            dyeColor = DyeColor.values()[in.readByte()];
        }

        @Override
        public void toBinary(DataOutputStream out) throws IOException {
            out.writeUTF(line1);
            out.writeUTF(line2);
            out.writeUTF(line3);
            out.writeUTF(line4);
            out.writeByte(dyeColor.ordinal());
        }

        @Override
        public void toBukkit(Block block) {
            Sign sign = (Sign) block.getState();
            sign.setLine(0, line1);
            sign.setLine(1, line2);
            sign.setLine(2, line3);
            sign.setLine(3, line4);
            sign.setColor(dyeColor);
            sign.update();
        }
    }
    @ToString
    @EqualsAndHashCode
    private static class SpawnerHandler implements Handler {
        private final EntityType type;

        private SpawnerHandler(DataInputStream in) throws IOException {
            type = EntityType.values()[in.readShort()];
        }

        @Override
        public void toBinary(DataOutputStream out) throws IOException {
            out.writeShort(type.ordinal());
        }

        @Override
        public void toBukkit(Block block) {
            CreatureSpawner spawner = (CreatureSpawner) block.getState();
            spawner.setSpawnedType(type);
            spawner.update();
        }
    }
    @ToString
    @EqualsAndHashCode
    private static class BannerHandler implements Handler {
        private final KList<Pattern> patterns;
        private final DyeColor baseColor;

        private BannerHandler(DataInputStream in) throws IOException {
            baseColor = DyeColor.values()[in.readByte()];
            patterns = new KList<>();
            int listSize = in.readByte();
            for (int i = 0; i < listSize; i++) {
                DyeColor color = DyeColor.values()[in.readByte()];
                PatternType pattern = PatternType.values()[in.readByte()];
                patterns.add(new Pattern(color, pattern));
            }
        }

        @Override
        public void toBinary(DataOutputStream out) throws IOException {
            out.writeByte(baseColor.ordinal());
            out.writeByte(patterns.size());
            for (Pattern i : patterns) {
                out.writeByte(i.getColor().ordinal());
                out.writeByte(i.getPattern().ordinal());
            }
        }

        @Override
        public void toBukkit(Block block) {
            Banner banner = (Banner) block.getState();
            banner.setBaseColor(baseColor);
            banner.setPatterns(patterns);
            banner.update();
        }
    }
}
