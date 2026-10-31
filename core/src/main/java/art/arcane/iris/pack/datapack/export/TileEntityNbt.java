package art.arcane.iris.pack.datapack.export;

import art.arcane.volmlib.util.nbt.io.SNBTDeserializer;
import art.arcane.volmlib.util.nbt.tag.ByteTag;
import art.arcane.volmlib.util.nbt.tag.CompoundTag;
import art.arcane.volmlib.util.nbt.tag.DoubleTag;
import art.arcane.volmlib.util.nbt.tag.EndTag;
import art.arcane.volmlib.util.nbt.tag.FloatTag;
import art.arcane.volmlib.util.nbt.tag.IntArrayTag;
import art.arcane.volmlib.util.nbt.tag.IntTag;
import art.arcane.volmlib.util.nbt.tag.ListTag;
import art.arcane.volmlib.util.nbt.tag.LongTag;
import art.arcane.volmlib.util.nbt.tag.ShortTag;
import art.arcane.volmlib.util.nbt.tag.StringTag;
import art.arcane.volmlib.util.nbt.tag.Tag;

import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;

public final class TileEntityNbt {
    private static final String SNBT_PROPERTY = "nbt";
    private static final String ID = "id";
    private static final String PROFILE = "profile";
    private static final String UUID = "UUID";
    private static final int MAX_DEPTH = 64;
    private static final Set<String> POSITION_KEYS = Set.of("x", "y", "z");
    private static final Map<String, String> BLOCK_ENTITIES = Map.ofEntries(
            Map.entry("minecraft:chest", "minecraft:chest"),
            Map.entry("minecraft:trapped_chest", "minecraft:trapped_chest"),
            Map.entry("minecraft:ender_chest", "minecraft:ender_chest"),
            Map.entry("minecraft:barrel", "minecraft:barrel"),
            Map.entry("minecraft:furnace", "minecraft:furnace"),
            Map.entry("minecraft:smoker", "minecraft:smoker"),
            Map.entry("minecraft:blast_furnace", "minecraft:blast_furnace"),
            Map.entry("minecraft:dispenser", "minecraft:dispenser"),
            Map.entry("minecraft:dropper", "minecraft:dropper"),
            Map.entry("minecraft:hopper", "minecraft:hopper"),
            Map.entry("minecraft:brewing_stand", "minecraft:brewing_stand"),
            Map.entry("minecraft:beacon", "minecraft:beacon"),
            Map.entry("minecraft:lectern", "minecraft:lectern"),
            Map.entry("minecraft:jukebox", "minecraft:jukebox"),
            Map.entry("minecraft:enchanting_table", "minecraft:enchanting_table"),
            Map.entry("minecraft:conduit", "minecraft:conduit"),
            Map.entry("minecraft:bell", "minecraft:bell"),
            Map.entry("minecraft:crafter", "minecraft:crafter"),
            Map.entry("minecraft:daylight_detector", "minecraft:daylight_detector"),
            Map.entry("minecraft:comparator", "minecraft:comparator"),
            Map.entry("minecraft:end_gateway", "minecraft:end_gateway"),
            Map.entry("minecraft:end_portal", "minecraft:end_portal"),
            Map.entry("minecraft:sculk_sensor", "minecraft:sculk_sensor"),
            Map.entry("minecraft:calibrated_sculk_sensor", "minecraft:calibrated_sculk_sensor"),
            Map.entry("minecraft:sculk_catalyst", "minecraft:sculk_catalyst"),
            Map.entry("minecraft:sculk_shrieker", "minecraft:sculk_shrieker"),
            Map.entry("minecraft:chiseled_bookshelf", "minecraft:chiseled_bookshelf"),
            Map.entry("minecraft:decorated_pot", "minecraft:decorated_pot"),
            Map.entry("minecraft:creaking_heart", "minecraft:creaking_heart"),
            Map.entry("minecraft:trial_spawner", "minecraft:trial_spawner"),
            Map.entry("minecraft:vault", "minecraft:vault"),
            Map.entry("minecraft:beehive", "minecraft:beehive"),
            Map.entry("minecraft:bee_nest", "minecraft:beehive"),
            Map.entry("minecraft:spawner", "minecraft:mob_spawner"),
            Map.entry("minecraft:campfire", "minecraft:campfire"),
            Map.entry("minecraft:soul_campfire", "minecraft:campfire"),
            Map.entry("minecraft:command_block", "minecraft:command_block"),
            Map.entry("minecraft:chain_command_block", "minecraft:command_block"),
            Map.entry("minecraft:repeating_command_block", "minecraft:command_block"),
            Map.entry("minecraft:suspicious_sand", "minecraft:brushable_block"),
            Map.entry("minecraft:suspicious_gravel", "minecraft:brushable_block"));
    private static final List<Map.Entry<String, String>> SUFFIX_BLOCK_ENTITIES = List.of(
            Map.entry("_hanging_sign", "minecraft:hanging_sign"),
            Map.entry("_sign", "minecraft:sign"),
            Map.entry("_banner", "minecraft:banner"),
            Map.entry("_head", "minecraft:skull"),
            Map.entry("_skull", "minecraft:skull"),
            Map.entry("_bed", "minecraft:bed"),
            Map.entry("shulker_box", "minecraft:shulker_box"),
            Map.entry("_shelf", "minecraft:shelf"),
            Map.entry("copper_golem_statue", "minecraft:copper_golem_statue"));
    private static final Set<String> BYTE_FIELDS = Set.of(
            "Slot", "Count", "has_glowing_text", "is_waxed", "auto", "conditionMet", "powered", "TrackOutput",
            "UpdateLastExecution", "OnGround", "Invulnerable", "Silent", "NoGravity", "Glowing",
            "PersistenceRequired", "CanPickUpLoot", "LeftHanded", "NoAI", "ExactTeleport", "show_particles",
            "show_icon", "ambient");
    private static final Set<String> SHORT_FIELDS = Set.of(
            "Delay", "MinSpawnDelay", "MaxSpawnDelay", "SpawnCount", "MaxNearbyEntities", "RequiredPlayerRange",
            "SpawnRange", "BurnTime", "CookTime", "CookTimeTotal", "lit_time_remaining", "lit_total_time",
            "cooking_time_spent", "cooking_total_time", "Fire", "Air", "HurtTime", "DeathTime");
    private static final Set<String> LONG_FIELDS = Set.of("LootTableSeed", "Age", "LastExecution", "ticks_since_song_started");
    private static final Set<String> FLOAT_FIELDS = Set.of("Health", "AbsorptionAmount", "Rotation", "HandDropChances", "ArmorDropChances");

    private TileEntityNbt() {
    }

    public static Optional<String> blockEntityId(String blockKey) {
        String key = normalizeBlockKey(blockKey);
        if (key.isEmpty()) {
            return Optional.empty();
        }
        String exact = BLOCK_ENTITIES.get(key);
        if (exact != null) {
            return Optional.of(exact);
        }
        if (!key.startsWith("minecraft:")) {
            return Optional.empty();
        }
        for (Map.Entry<String, String> suffix : SUFFIX_BLOCK_ENTITIES) {
            if (key.endsWith(suffix.getKey())) {
                return Optional.of(suffix.getValue());
            }
        }
        return Optional.empty();
    }

    public static Optional<CompoundTag> toNbt(String blockKey, Map<String, Object> properties) {
        Optional<String> blockEntity = blockEntityId(blockKey);
        if (blockEntity.isEmpty() || properties == null) {
            return Optional.empty();
        }
        CompoundTag nbt = parseSnbt(properties.get(SNBT_PROPERTY));
        if (nbt == null) {
            nbt = compound(properties, "", 0);
        }
        for (String key : POSITION_KEYS) {
            nbt.remove(key);
        }
        nbt.remove(SNBT_PROPERTY);
        if (nbt.size() == 0) {
            return Optional.empty();
        }
        if (!nbt.containsKey(ID)) {
            nbt.putString(ID, blockEntity.get());
        }
        return Optional.of(nbt);
    }

    private static String normalizeBlockKey(String blockKey) {
        if (blockKey == null) {
            return "";
        }
        String trimmed = blockKey.trim().toLowerCase(Locale.ROOT);
        int properties = trimmed.indexOf('[');
        String base = properties < 0 ? trimmed : trimmed.substring(0, properties);
        if (base.isEmpty()) {
            return "";
        }
        return base.indexOf(':') < 0 ? "minecraft:" + base : base;
    }

    private static CompoundTag parseSnbt(Object snbt) {
        if (!(snbt instanceof String text) || text.isBlank()) {
            return null;
        }
        try {
            Tag<?> parsed = new SNBTDeserializer().fromString(text);
            return parsed instanceof CompoundTag compound ? compound : null;
        } catch (IOException | RuntimeException exception) {
            return null;
        }
    }

    private static CompoundTag compound(Map<?, ?> values, String parent, int depth) {
        CompoundTag compound = new CompoundTag();
        if (depth > MAX_DEPTH) {
            return compound;
        }
        Map<String, Object> sorted = new TreeMap<>();
        for (Map.Entry<?, ?> entry : values.entrySet()) {
            if (entry.getKey() != null && entry.getValue() != null) {
                sorted.put(String.valueOf(entry.getKey()), entry.getValue());
            }
        }
        if (depth == 0) {
            sorted.remove(SNBT_PROPERTY);
        }
        for (Map.Entry<String, Object> entry : sorted.entrySet()) {
            Tag<?> tag = tag(entry.getKey(), parent, entry.getValue(), depth + 1);
            if (tag != null) {
                compound.put(entry.getKey(), tag);
            }
        }
        return compound;
    }

    private static Tag<?> tag(String key, String parent, Object value, int depth) {
        if (value instanceof Map<?, ?> map) {
            return compound(map, key, depth);
        }
        if (value instanceof List<?> list) {
            return list(key, parent, list, depth);
        }
        if (value instanceof Boolean bool) {
            return new ByteTag(bool);
        }
        if (value instanceof Number number) {
            return number(numberKind(key, number), number);
        }
        return new StringTag(String.valueOf(value));
    }

    private static Tag<?> list(String key, String parent, List<?> values, int depth) {
        if (intArray(key, parent) && allNumbers(values)) {
            int[] ints = new int[values.size()];
            for (int index = 0; index < ints.length; index++) {
                ints[index] = ((Number) values.get(index)).intValue();
            }
            return new IntArrayTag(ints);
        }
        NumberKind listKind = allNumbers(values) ? listNumberKind(key, values) : null;
        ListTag<?> list = ListTag.createUnchecked(EndTag.class);
        Class<?> elementType = null;
        for (Object value : values) {
            if (value == null) {
                continue;
            }
            Tag<?> element = listKind == null ? tag(key, parent, value, depth + 1) : number(listKind, (Number) value);
            if (element == null) {
                continue;
            }
            if (elementType == null) {
                elementType = element.getClass();
            }
            if (elementType != element.getClass()) {
                return stringList(values);
            }
            list.addUnchecked(element);
        }
        return list;
    }

    private static ListTag<?> stringList(List<?> values) {
        ListTag<?> list = ListTag.createUnchecked(EndTag.class);
        for (Object value : values) {
            if (value != null) {
                list.addUnchecked(new StringTag(String.valueOf(value)));
            }
        }
        return list;
    }

    private static boolean intArray(String key, String parent) {
        return UUID.equals(key) || (ID.equals(key) && PROFILE.equals(parent));
    }

    private static boolean allNumbers(List<?> values) {
        if (values.isEmpty()) {
            return false;
        }
        for (Object value : values) {
            if (!(value instanceof Number)) {
                return false;
            }
        }
        return true;
    }

    private static NumberKind listNumberKind(String key, List<?> values) {
        NumberKind declared = declaredKind(key);
        if (declared != null) {
            return declared;
        }
        NumberKind widest = NumberKind.INT;
        for (Object value : values) {
            NumberKind kind = inferredKind((Number) value);
            if (kind.ordinal() > widest.ordinal()) {
                widest = kind;
            }
        }
        return widest;
    }

    private static NumberKind numberKind(String key, Number number) {
        NumberKind declared = declaredKind(key);
        return declared == null ? inferredKind(number) : declared;
    }

    private static NumberKind declaredKind(String key) {
        if (BYTE_FIELDS.contains(key)) {
            return NumberKind.BYTE;
        }
        if (SHORT_FIELDS.contains(key)) {
            return NumberKind.SHORT;
        }
        if (LONG_FIELDS.contains(key)) {
            return NumberKind.LONG;
        }
        if (FLOAT_FIELDS.contains(key)) {
            return NumberKind.FLOAT;
        }
        return null;
    }

    private static NumberKind inferredKind(Number number) {
        if (number instanceof Double || number instanceof Float) {
            return NumberKind.DOUBLE;
        }
        long value = number.longValue();
        return value >= Integer.MIN_VALUE && value <= Integer.MAX_VALUE ? NumberKind.INT : NumberKind.LONG;
    }

    private static Tag<?> number(NumberKind kind, Number number) {
        return switch (kind) {
            case BYTE -> new ByteTag(number.byteValue());
            case SHORT -> new ShortTag(number.shortValue());
            case INT -> new IntTag(number.intValue());
            case LONG -> new LongTag(number.longValue());
            case FLOAT -> new FloatTag(number.floatValue());
            case DOUBLE -> new DoubleTag(number.doubleValue());
        };
    }

    private enum NumberKind {
        BYTE,
        SHORT,
        INT,
        LONG,
        FLOAT,
        DOUBLE
    }
}
