package com.volmit.block;

import com.volmit.config.Desc;
import com.volmit.config.MaxNumber;
import com.volmit.config.MinNumber;
import com.volmit.config.Ref;
import com.volmit.config.Reference;
import com.volmit.config.Required;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** A block as packs write it in settings, such as {@code {"block": "iron_ore"}} or
 * {@code {"block": "minecraft:oak_log", "data": {"axis": "y"}, "backup": {"block": "stone"}}}. Settings hold it
 * where a pack picks a block it may not be able to name exactly: a key without a namespace, a custom block of
 * the pack's {@code blocks} folder, or a block that only some versions or mods have.
 *
 * <p>The host resolves it with {@link BlockRegistry#resolve(BlockSpec)} and its block entity with
 * {@link BlockRegistry#blockEntity(BlockSpec)}. A spec is a value: the host creates it with the no-argument
 * constructor and reads the JSON into it, and nothing changes it afterwards. Property values are text however the
 * pack writes them, so {@code "waterlogged": true} reads as {@code "true"}.
 */
@Desc("A block, with optional properties, block entity data and a backup for platforms without it")
public final class BlockSpec {
    private static final String AIR = "air";

    @Required
    @Ref(Reference.BLOCK)
    @Desc("The block to use. A key without a namespace is a Minecraft block or a block of the pack's blocks folder.")
    private String block = AIR;

    @Desc("Optional properties for this block, such as 'waterlogged': true")
    private Map<String, String> data = Map.of();

    @MinNumber(1)
    @MaxNumber(1000)
    @Desc("The weight of this block in a list it is picked from. A weight of two is as if the block were in the list twice.")
    private int weight = 1;

    @Desc("If the block cannot be created on this platform, this block is used instead.")
    private BlockSpec backup;

    @Desc("Optional block entity data for this block, such as a spawner's SpawnData")
    private Map<String, Object> tileData = Map.of();

    private BlockSpec() {
    }

    private BlockSpec(String block, Map<String, String> data, int weight, BlockSpec backup, Map<String, Object> tileData) {
        this.block = Objects.requireNonNull(block, "block");
        this.data = Map.copyOf(data);
        this.weight = weight;
        this.backup = backup;
        this.tileData = Collections.unmodifiableMap(new LinkedHashMap<>(tileData));
    }

    /** The block {@code block}, such as {@code minecraft:stone} or {@code stone}, without properties, weight 1. */
    public static BlockSpec of(String block) {
        return new BlockSpec(block, Map.of(), 1, null, Map.of());
    }

    /** This spec with the property {@code name} set to {@code value}. */
    public BlockSpec withProperty(String name, String value) {
        var changed = new LinkedHashMap<>(data());
        changed.put(name, value);
        return new BlockSpec(block, changed, weight, backup, tileData());
    }

    public BlockSpec withWeight(int weight) {
        return new BlockSpec(block, data(), weight, backup, tileData());
    }

    public BlockSpec withBackup(BlockSpec backup) {
        return new BlockSpec(block, data(), weight, backup, tileData());
    }

    public BlockSpec withTileData(Map<String, Object> tileData) {
        return new BlockSpec(block, data(), weight, backup, tileData);
    }

    /** The block key as the pack wrote it. */
    public String block() {
        return block == null ? AIR : block;
    }

    /** The properties as the pack wrote them, as text. */
    public Map<String, String> data() {
        return data == null ? Map.of() : Collections.unmodifiableMap(data);
    }

    public int weight() {
        return weight;
    }

    public Optional<BlockSpec> backup() {
        return Optional.ofNullable(backup);
    }

    /** The block entity data as the pack wrote it: JSON objects as maps, arrays as lists, numbers as {@code Double}. */
    public Map<String, Object> tileData() {
        return tileData == null ? Map.of() : Collections.unmodifiableMap(tileData);
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof BlockSpec spec && block().equals(spec.block()) && data().equals(spec.data())
                && weight == spec.weight && Objects.equals(backup, spec.backup) && tileData().equals(spec.tileData());
    }

    @Override
    public int hashCode() {
        return Objects.hash(block(), data(), weight, backup, tileData());
    }

    @Override
    public String toString() {
        return "BlockSpec[" + block() + (data().isEmpty() ? "" : " " + data()) + "]";
    }
}
