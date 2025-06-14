package com.volmit.iris.engine.object;

import com.volmit.iris.Iris;
import com.volmit.iris.core.nms.container.Pair;
import com.volmit.iris.engine.data.cache.AtomicCache;
import com.volmit.iris.util.collection.KList;
import com.volmit.iris.util.collection.KMap;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.NonNull;
import lombok.ToString;
import org.apache.commons.io.function.IOFunction;
import org.bukkit.*;
import org.bukkit.block.*;
import org.bukkit.block.banner.Pattern;
import org.bukkit.block.banner.PatternType;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.EntityType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

@ToString
@EqualsAndHashCode(callSuper = false)
public class LegacyTileData extends TileData {
    private static final Map<Integer, Pair<Builder, IOFunction<DataInputStream, Handler>>> legacy = Map.of(
            0, new Pair<>(SignHandler::fromBukkit, SignHandler::new),
            1, new Pair<>(SpawnerHandler::fromBukkit, SpawnerHandler::new),
            2, new Pair<>(BannerHandler::fromBukkit, BannerHandler::new));
    private static final AtomicCache<Tag<Material>> SIGNS = new AtomicCache<>();
    private final int id;
    private final Handler handler;

    public LegacyTileData(DataInputStream in) throws IOException {
        id = in.readShort();
        var factory = legacy.get(id);
        if (factory == null)
            throw new IOException("Unknown tile type: " + id);
        handler = factory.getB().apply(in);
    }

    private LegacyTileData(int id, Handler handler) {
        this.id = id;
        this.handler = handler;
    }

    @Nullable
    public static LegacyTileData fromBukkit(@NonNull BlockState tileState) {
        var type = tileState.getType();
        for (var id : legacy.keySet()) {
            var factory = legacy.get(id);
            var handler = factory.getA().apply(tileState, type);
            if (handler != null)
                return new LegacyTileData(id, handler);
        }
        return null;
    }

    @Override
    public @NonNull KMap<String, Object> getProperties() {
        return new KMap<>();
    }

    @Override
    public @NonNull Material getMaterial() {
        return handler.getMaterial();
    }

    @Override
    public boolean isApplicable(BlockData data) {
        return handler.isApplicable(data);
    }

    @Override
    public void toBukkit(Block block) {
        Iris.scheduler.region().run(block.getLocation(), () -> handler.toBukkit(block));
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
        Material getMaterial();
        boolean isApplicable(BlockData data);
        void toBinary(DataOutputStream out) throws IOException;
        void toBukkit(Block block);
    }

    @FunctionalInterface
    private interface Builder {
        @Nullable Handler apply(@NonNull BlockState blockState, @NonNull Material type);
    }

    @ToString
    @EqualsAndHashCode
    @AllArgsConstructor
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

        @SuppressWarnings("deprecation")
        private static SignHandler fromBukkit(BlockState blockState, Material type) {
            if (!signsTag().isTagged(type) || !(blockState instanceof Sign sign))
                return null;
            return new SignHandler(sign.getLine(0), sign.getLine(1), sign.getLine(2), sign.getLine(3), sign.getColor());
        }

        @Override
        public Material getMaterial() {
            return Material.OAK_SIGN;
        }

        @Override
        public boolean isApplicable(BlockData data) {
            return signsTag().isTagged(data.getMaterial());
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
    @AllArgsConstructor
    private static class SpawnerHandler implements Handler {
        private final EntityType type;

        private SpawnerHandler(DataInputStream in) throws IOException {
            type = EntityType.values()[in.readShort()];
        }

        private static SpawnerHandler fromBukkit(BlockState blockState, Material material) {
            if (material != Material.SPAWNER || !(blockState instanceof CreatureSpawner spawner))
                return null;
            return new SpawnerHandler(spawner.getSpawnedType());
        }

        @Override
        public Material getMaterial() {
            return Material.SPAWNER;
        }

        @Override
        public boolean isApplicable(BlockData data) {
            return data.getMaterial() == Material.SPAWNER;
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
    @AllArgsConstructor
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

        private static BannerHandler fromBukkit(BlockState blockState, Material type) {
            if (!Tag.BANNERS.isTagged(type) || !(blockState instanceof Banner banner))
                return null;
            return new BannerHandler(new KList<>(banner.getPatterns()), banner.getBaseColor());
        }

        @Override
        public Material getMaterial() {
            return Material.WHITE_BANNER;
        }

        @Override
        public boolean isApplicable(BlockData data) {
            return Tag.BANNERS.isTagged(data.getMaterial());
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

    private static Tag<Material> signsTag() {
        return SIGNS.aquire(() -> {
            var signs = Bukkit.getTag("blocks", NamespacedKey.minecraft("all_signs"), Material.class);
            if (signs != null)
                return signs;
            return new Tag<>() {
                @Override
                public boolean isTagged(@NotNull Material item) {
                    return item.getKey().getKey().endsWith("_sign");
                }

                @NotNull
                @Override
                public Set<Material> getValues() {
                    return StreamSupport.stream(Registry.MATERIAL.spliterator(), false)
                            .filter(this::isTagged)
                            .collect(Collectors.toUnmodifiableSet());
                }

                @NotNull
                @Override
                public NamespacedKey getKey() {
                    return NamespacedKey.minecraft("all_signs");
                }
            };
        });
    }
}
