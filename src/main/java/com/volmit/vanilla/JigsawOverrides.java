package com.volmit.vanilla;

import com.volmit.config.Desc;
import com.volmit.config.Ref;
import com.volmit.config.Reference;
import com.volmit.config.MaxNumber;
import com.volmit.config.MinNumber;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;

/** Changes to a platform jigsaw structure's definition before the platform assembles one start of it, as packs write
 * them: {@code {"startPool": "minecraft:village/plains/town_centers", "maxDepth": 4}}. Every option left out keeps
 * what the registered definition says. A value the host creates with the no-argument constructor and reads the JSON
 * into.
 */
@Desc("Optional overrides applied to a live vanilla, datapack, or modded jigsaw definition before Minecraft assembles it. Omitted values preserve the registered source definition.")
public final class JigsawOverrides {
    /** Removes the definition's start connector constraint when given as {@link #startJigsawName()}. */
    public static final String NO_START_JIGSAW = "NONE";

    /** Whether the start piece follows one of the platform's heightmaps. */
    @Desc("Controls whether a configured native jigsaw projects its start onto a Minecraft heightmap.")
    public enum Heightmap {
        @Desc("Keeps the registered structure's own heightmap projection.")
        SOURCE,
        @Desc("Removes heightmap projection: the start piece lands at the structure's literal start height.")
        NONE,
        @Desc("The terrain top counting water as solid.")
        WORLD_SURFACE_WG,
        @Desc("The terrain top including water.")
        WORLD_SURFACE,
        @Desc("Solid terrain beneath any water.")
        OCEAN_FLOOR_WG,
        @Desc("Solid ground beneath any water.")
        OCEAN_FLOOR,
        @Desc("The terrain top including water.")
        MOTION_BLOCKING,
        @Desc("The terrain top including water; there is no leaf layer to skip.")
        MOTION_BLOCKING_NO_LEAVES
    }

    /** Whether placed pieces take on the water they are placed into. */
    @Desc("Controls waterlogging behavior for a configured native jigsaw.")
    public enum Liquids {
        @Desc("Keeps the registered structure's own liquid setting.")
        SOURCE,
        @Desc("Places pieces dry.")
        IGNORE_WATERLOGGING,
        @Desc("Waterlogs placed pieces from the water they replace.")
        APPLY_WATERLOGGING
    }

    @Desc("Optional registered template-pool key used instead of the source jigsaw's start pool. Empty preserves the source pool.")
    @Ref(Reference.TEMPLATE_POOL)
    private String startPool = "";

    @Desc("Optional start connector name. Empty preserves the source value; the literal NONE removes the source constraint.")
    private String startJigsawName = "";

    @MinNumber(0)
    @MaxNumber(20)
    @Desc("Maximum jigsaw recursion depth. Null preserves the source value.")
    private Integer maxDepth = null;

    @MinNumber(1)
    @MaxNumber(128)
    @Desc("Exact horizontal block distance allowed from the start. Null preserves the source value.")
    private Integer maxDistanceHorizontal = null;

    @MinNumber(1)
    @MaxNumber(4064)
    @Desc("Exact vertical block distance allowed from the start. Null preserves the source value.")
    private Integer maxDistanceVertical = null;

    @Desc("Expansion-hack override. Null preserves the source value.")
    private Boolean useExpansionHack = null;

    @Desc("Heightmap projection override. SOURCE preserves the registered value and NONE removes projection.")
    private Heightmap projectStartToHeightmap = Heightmap.SOURCE;

    @MinNumber(0)
    @MaxNumber(Integer.MAX_VALUE)
    @Desc("Minimum distance retained above the dimension floor. Null preserves the source value.")
    private Integer dimensionPaddingBottom = null;

    @MinNumber(0)
    @MaxNumber(Integer.MAX_VALUE)
    @Desc("Minimum distance retained below the dimension ceiling. Null preserves the source value.")
    private Integer dimensionPaddingTop = null;

    @Desc("Waterlogging override. SOURCE preserves the registered value.")
    private Liquids liquidSettings = Liquids.SOURCE;

    private JigsawOverrides() {
    }

    private JigsawOverrides(JigsawOverrides source) {
        startPool = source.startPool;
        startJigsawName = source.startJigsawName;
        maxDepth = source.maxDepth;
        maxDistanceHorizontal = source.maxDistanceHorizontal;
        maxDistanceVertical = source.maxDistanceVertical;
        useExpansionHack = source.useExpansionHack;
        projectStartToHeightmap = source.projectStartToHeightmap();
        dimensionPaddingBottom = source.dimensionPaddingBottom;
        dimensionPaddingTop = source.dimensionPaddingTop;
        liquidSettings = source.liquidSettings();
    }

    /** Overrides that change nothing: the registered definition as it is. */
    public static JigsawOverrides none() {
        return new JigsawOverrides();
    }

    /** These overrides, starting from the template pool {@code pool}, such as {@code minecraft:village/plains/town_centers}. */
    public JigsawOverrides withStartPool(String pool) {
        JigsawOverrides overrides = new JigsawOverrides(this);
        overrides.startPool = Objects.requireNonNull(pool, "pool");
        return overrides;
    }

    /** These overrides, starting from the connector {@code name}; {@link #NO_START_JIGSAW} removes the constraint. */
    public JigsawOverrides withStartJigsawName(String name) {
        JigsawOverrides overrides = new JigsawOverrides(this);
        overrides.startJigsawName = Objects.requireNonNull(name, "name");
        return overrides;
    }

    public JigsawOverrides withMaxDepth(int depth) {
        JigsawOverrides overrides = new JigsawOverrides(this);
        overrides.maxDepth = depth;
        return overrides;
    }

    /** These overrides, keeping pieces within {@code horizontal} and {@code vertical} blocks of the start. */
    public JigsawOverrides withMaxDistance(int horizontal, int vertical) {
        JigsawOverrides overrides = new JigsawOverrides(this);
        overrides.maxDistanceHorizontal = horizontal;
        overrides.maxDistanceVertical = vertical;
        return overrides;
    }

    public JigsawOverrides withExpansionHack(boolean use) {
        JigsawOverrides overrides = new JigsawOverrides(this);
        overrides.useExpansionHack = use;
        return overrides;
    }

    public JigsawOverrides withHeightmap(Heightmap heightmap) {
        JigsawOverrides overrides = new JigsawOverrides(this);
        overrides.projectStartToHeightmap = Objects.requireNonNull(heightmap, "heightmap");
        return overrides;
    }

    /** These overrides, keeping the pieces {@code bottom} blocks above the dimension's floor and {@code top} below its ceiling. */
    public JigsawOverrides withDimensionPadding(int bottom, int top) {
        JigsawOverrides overrides = new JigsawOverrides(this);
        overrides.dimensionPaddingBottom = bottom;
        overrides.dimensionPaddingTop = top;
        return overrides;
    }

    public JigsawOverrides withLiquids(Liquids liquids) {
        JigsawOverrides overrides = new JigsawOverrides(this);
        overrides.liquidSettings = Objects.requireNonNull(liquids, "liquids");
        return overrides;
    }

    /** The template pool to start from; empty keeps the definition's. */
    public String startPool() {
        return startPool == null ? "" : startPool;
    }

    /** The start connector; empty keeps the definition's, {@link #NO_START_JIGSAW} removes it. */
    public String startJigsawName() {
        return startJigsawName == null ? "" : startJigsawName;
    }

    public OptionalInt maxDepth() {
        return optional(maxDepth);
    }

    public OptionalInt maxDistanceHorizontal() {
        return optional(maxDistanceHorizontal);
    }

    public OptionalInt maxDistanceVertical() {
        return optional(maxDistanceVertical);
    }

    public Optional<Boolean> useExpansionHack() {
        return Optional.ofNullable(useExpansionHack);
    }

    /** The heightmap projection; {@link Heightmap#SOURCE} where the pack wrote none or one the host does not know. */
    public Heightmap projectStartToHeightmap() {
        return projectStartToHeightmap == null ? Heightmap.SOURCE : projectStartToHeightmap;
    }

    public OptionalInt dimensionPaddingBottom() {
        return optional(dimensionPaddingBottom);
    }

    public OptionalInt dimensionPaddingTop() {
        return optional(dimensionPaddingTop);
    }

    /** The waterlogging; {@link Liquids#SOURCE} where the pack wrote none or one the host does not know. */
    public Liquids liquidSettings() {
        return liquidSettings == null ? Liquids.SOURCE : liquidSettings;
    }

    private static OptionalInt optional(Integer value) {
        return value == null ? OptionalInt.empty() : OptionalInt.of(value);
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof JigsawOverrides that && startPool().equals(that.startPool())
                && startJigsawName().equals(that.startJigsawName()) && Objects.equals(maxDepth, that.maxDepth)
                && Objects.equals(maxDistanceHorizontal, that.maxDistanceHorizontal)
                && Objects.equals(maxDistanceVertical, that.maxDistanceVertical)
                && Objects.equals(useExpansionHack, that.useExpansionHack)
                && projectStartToHeightmap() == that.projectStartToHeightmap()
                && Objects.equals(dimensionPaddingBottom, that.dimensionPaddingBottom)
                && Objects.equals(dimensionPaddingTop, that.dimensionPaddingTop)
                && liquidSettings() == that.liquidSettings();
    }

    @Override
    public int hashCode() {
        return Objects.hash(startPool(), startJigsawName(), maxDepth, maxDistanceHorizontal, maxDistanceVertical,
                useExpansionHack, projectStartToHeightmap(), dimensionPaddingBottom, dimensionPaddingTop, liquidSettings());
    }

    @Override
    public String toString() {
        return "JigsawOverrides[pool '" + startPool() + "', depth " + maxDepth + ", " + projectStartToHeightmap() + "]";
    }
}
