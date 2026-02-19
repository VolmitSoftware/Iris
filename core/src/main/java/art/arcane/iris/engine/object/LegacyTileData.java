package art.arcane.iris.engine.object;

import art.arcane.iris.core.nms.container.Pair;
import art.arcane.iris.engine.data.cache.AtomicCache;
import art.arcane.volmlib.util.collection.KList;
import art.arcane.volmlib.util.collection.KMap;
import art.arcane.iris.util.common.reflect.KeyedType;
import art.arcane.iris.util.common.scheduling.J;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.NonNull;
import lombok.ToString;
import org.apache.commons.io.function.IOFunction;
import org.bukkit.*;
import org.bukkit.block.*;
import org.bukkit.block.banner.Pattern;
import org.bukkit.block.banner.PatternType;
import org.bukkit.block.sign.Side;
import org.bukkit.block.sign.SignSide;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.EntityType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Locale;
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
        if (!J.runAt(block.getLocation(), () -> handler.toBukkit(block))) {
            J.s(() -> handler.toBukkit(block));
        }
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

        private static SignHandler fromBukkit(BlockState blockState, Material type) {
            if (!signsTag().isTagged(type) || !(blockState instanceof Sign sign))
                return null;
            SignSide front = sign.getSide(Side.FRONT);
            return new SignHandler(front.getLine(0), front.getLine(1), front.getLine(2), front.getLine(3), front.getColor());
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
            SignSide front = sign.getSide(Side.FRONT);
            SignSide back = sign.getSide(Side.BACK);
            front.setLine(0, line1);
            front.setLine(1, line2);
            front.setLine(2, line3);
            front.setLine(3, line4);
            front.setColor(dyeColor);
            back.setLine(0, line1);
            back.setLine(1, line2);
            back.setLine(2, line3);
            back.setLine(3, line4);
            back.setColor(dyeColor);
            sign.update();
        }
    }
    @ToString
    @EqualsAndHashCode
    @AllArgsConstructor
    private static class SpawnerHandler implements Handler {
        private final EntityType type;

        private SpawnerHandler(DataInputStream in) throws IOException {
            EntityType resolved = null;
            if (in.markSupported()) {
                in.mark(Integer.MAX_VALUE);
            }

            try {
                String keyString = in.readUTF();
                NamespacedKey key = NamespacedKey.fromString(keyString);
                resolved = key == null ? null : Registry.ENTITY_TYPE.get(key);
                if (resolved == null && in.markSupported()) {
                    in.reset();
                }
            } catch (Throwable ignored) {
                if (in.markSupported()) {
                    in.reset();
                }
            }

            if (resolved == null) {
                short legacyOrdinal = in.readShort();
                EntityType[] legacyValues = EntityType.values();
                if (legacyOrdinal >= 0 && legacyOrdinal < legacyValues.length) {
                    resolved = legacyValues[legacyOrdinal];
                }
            }

            type = resolved == null ? EntityType.PIG : resolved;
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
            NamespacedKey key = KeyedType.getKey(type);
            out.writeUTF(key == null ? type.name() : key.toString());
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
            DyeColor[] dyeColors = DyeColor.values();
            int baseColorIndex = in.readUnsignedByte();
            baseColor = baseColorIndex >= 0 && baseColorIndex < dyeColors.length ? dyeColors[baseColorIndex] : DyeColor.WHITE;
            patterns = new KList<>();
            int listSize = in.readUnsignedByte();

            if (in.markSupported()) {
                in.mark(Integer.MAX_VALUE);
            }

            boolean parsedKeyed = false;
            try {
                KList<Pattern> keyedPatterns = new KList<>();
                for (int i = 0; i < listSize; i++) {
                    int colorIndex = in.readUnsignedByte();
                    DyeColor color = colorIndex >= 0 && colorIndex < dyeColors.length ? dyeColors[colorIndex] : DyeColor.WHITE;
                    NamespacedKey patternKey = NamespacedKey.fromString(in.readUTF());
                    PatternType pattern = patternKey == null ? null : Registry.BANNER_PATTERN.get(patternKey);
                    if (pattern == null) {
                        throw new IOException("Unknown banner pattern key");
                    }
                    keyedPatterns.add(new Pattern(color, pattern));
                }
                patterns.addAll(keyedPatterns);
                parsedKeyed = true;
            } catch (Throwable ignored) {
                if (in.markSupported()) {
                    in.reset();
                }
            }

            if (parsedKeyed) {
                return;
            }

            PatternType[] legacyPatternTypes = PatternType.values();
            PatternType fallbackPattern = Registry.BANNER_PATTERN.get(NamespacedKey.minecraft("base"));
            if (fallbackPattern == null && legacyPatternTypes.length > 0) {
                fallbackPattern = legacyPatternTypes[0];
            }

            for (int i = 0; i < listSize; i++) {
                int colorIndex = in.readUnsignedByte();
                DyeColor color = colorIndex >= 0 && colorIndex < dyeColors.length ? dyeColors[colorIndex] : DyeColor.WHITE;
                int legacyPatternIndex = in.readUnsignedByte();
                PatternType pattern = legacyPatternIndex >= 0 && legacyPatternIndex < legacyPatternTypes.length ? legacyPatternTypes[legacyPatternIndex] : fallbackPattern;
                if (pattern != null) {
                    patterns.add(new Pattern(color, pattern));
                }
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
                NamespacedKey key = KeyedType.getKey(i.getPattern());
                if (key == null) {
                    key = NamespacedKey.minecraft("base");
                }
                out.writeUTF(key.toString());
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
                    NamespacedKey key = KeyedType.getKey(item);
                    if (key != null) {
                        return key.getKey().endsWith("_sign");
                    }
                    return item.name().toLowerCase(Locale.ROOT).endsWith("_sign");
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
