package com.volmit.vanilla;

import com.volmit.config.Desc;
import com.volmit.config.Ref;
import com.volmit.config.Reference;
import com.volmit.config.MaxNumber;
import com.volmit.config.MinNumber;
import com.volmit.resource.ResourceId;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;

/** Changes to a platform jigsaw structure's definition before the platform assembles one start of it, as packs write
 * them: {@code {"startPool": "minecraft:village/plains/town_centers", "maxDepth": 4}}. Every option left out keeps
 * what the registered definition says, and its getter returns an empty value then; there is no constant that stands for
 * "the definition's own". A value the host decodes as every settings class (see DESIGN.md, "Decoding contract").
 */
@Desc("Changes to a platform jigsaw structure's definition before a start of it is assembled. An option left out keeps the definition's value.")
public final class JigsawOverrides {
    /** What packs write as the start connector to remove the definition's constraint. */
    private static final String NO_START_JIGSAW = "NONE";

    /** Whether the start piece follows one of the platform's heightmaps. */
    @Desc("Which heightmap the start piece is placed on.")
    public enum Heightmap {
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
    @Desc("Whether placed pieces take on the water they replace.")
    public enum Liquids {
        @Desc("Places pieces dry.")
        IGNORE_WATERLOGGING,
        @Desc("Waterlogs placed pieces from the water they replace.")
        APPLY_WATERLOGGING
    }

    @Desc("The template pool the start piece comes from. Left out, the definition's pool.")
    @Ref(Reference.TEMPLATE_POOL)
    private String startPool = null;

    @Desc("The start piece's connector. Left out, the definition's; NONE removes the definition's constraint.")
    private String startJigsawName = null;

    @MinNumber(0)
    @MaxNumber(20)
    @Desc("How many pieces deep the structure grows. Left out, the definition's depth.")
    private Integer maxDepth = null;

    @MinNumber(1)
    @MaxNumber(128)
    @Desc("How many blocks sideways pieces may reach from the start. Left out, the definition's distance.")
    private Integer maxDistanceHorizontal = null;

    @MinNumber(1)
    @MaxNumber(4064)
    @Desc("How many blocks up or down pieces may reach from the start. Left out, the definition's distance.")
    private Integer maxDistanceVertical = null;

    @Desc("Whether the platform's expansion hack applies. Left out, the definition's setting.")
    private Boolean useExpansionHack = null;

    @Desc("The heightmap the start piece is placed on. Left out, the definition's heightmap.")
    private Heightmap projectStartToHeightmap = null;

    @MinNumber(0)
    @MaxNumber(Integer.MAX_VALUE)
    @Desc("How many blocks pieces stay above the dimension's bottom. Left out, the definition's padding.")
    private Integer dimensionPaddingBottom = null;

    @MinNumber(0)
    @MaxNumber(Integer.MAX_VALUE)
    @Desc("How many blocks pieces stay below the dimension's top. Left out, the definition's padding.")
    private Integer dimensionPaddingTop = null;

    @Desc("Whether placed pieces take on the water they replace. Left out, the definition's setting.")
    private Liquids liquidSettings = null;

    private JigsawOverrides() {
    }

    private JigsawOverrides(JigsawOverrides source) {
        startPool = source.startPool;
        startJigsawName = source.startJigsawName;
        maxDepth = source.maxDepth;
        maxDistanceHorizontal = source.maxDistanceHorizontal;
        maxDistanceVertical = source.maxDistanceVertical;
        useExpansionHack = source.useExpansionHack;
        projectStartToHeightmap = source.projectStartToHeightmap;
        dimensionPaddingBottom = source.dimensionPaddingBottom;
        dimensionPaddingTop = source.dimensionPaddingTop;
        liquidSettings = source.liquidSettings;
    }

    /** Overrides that change nothing: the registered definition as it is. */
    public static JigsawOverrides none() {
        return new JigsawOverrides();
    }

    /** These overrides, starting from the template pool {@code pool}, such as {@code minecraft:village/plains/town_centers}. */
    public JigsawOverrides withStartPool(ResourceId pool) {
        JigsawOverrides overrides = new JigsawOverrides(this);
        overrides.startPool = pool.toString();
        return overrides;
    }

    /** These overrides, starting from the connector {@code name}. */
    public JigsawOverrides withStartJigsawName(ResourceId name) {
        JigsawOverrides overrides = new JigsawOverrides(this);
        overrides.startJigsawName = name.toString();
        return overrides;
    }

    /** These overrides, without the definition's start connector constraint. */
    public JigsawOverrides withoutStartJigsaw() {
        JigsawOverrides overrides = new JigsawOverrides(this);
        overrides.startJigsawName = NO_START_JIGSAW;
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

    /** The template pool to start from, such as {@code minecraft:village/plains/town_centers}; empty keeps the definition's. */
    public Optional<ResourceId> startPool() {
        return platformKey(startPool);
    }

    /** The start connector; empty keeps the definition's, unless {@link #removesStartJigsaw()}. */
    public Optional<ResourceId> startJigsawName() {
        return removesStartJigsaw() ? Optional.empty() : platformKey(startJigsawName);
    }

    /** Whether the start connector constraint is removed, which packs write as {@code NONE}. */
    public boolean removesStartJigsaw() {
        return startJigsawName != null && NO_START_JIGSAW.equalsIgnoreCase(written(startJigsawName));
    }

    private static Optional<ResourceId> platformKey(String key) {
        return key == null || key.isBlank() ? Optional.empty() : Optional.of(ResourceId.ofPlatformKey(key));
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

    /** The heightmap projection; empty keeps the definition's. */
    public Optional<Heightmap> projectStartToHeightmap() {
        return Optional.ofNullable(projectStartToHeightmap);
    }

    public OptionalInt dimensionPaddingBottom() {
        return optional(dimensionPaddingBottom);
    }

    public OptionalInt dimensionPaddingTop() {
        return optional(dimensionPaddingTop);
    }

    /** The waterlogging; empty keeps the definition's. */
    public Optional<Liquids> liquidSettings() {
        return Optional.ofNullable(liquidSettings);
    }

    private static OptionalInt optional(Integer value) {
        return value == null ? OptionalInt.empty() : OptionalInt.of(value);
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof JigsawOverrides that && written(startPool).equals(written(that.startPool))
                && written(startJigsawName).equals(written(that.startJigsawName)) && Objects.equals(maxDepth, that.maxDepth)
                && Objects.equals(maxDistanceHorizontal, that.maxDistanceHorizontal)
                && Objects.equals(maxDistanceVertical, that.maxDistanceVertical)
                && Objects.equals(useExpansionHack, that.useExpansionHack)
                && projectStartToHeightmap == that.projectStartToHeightmap
                && Objects.equals(dimensionPaddingBottom, that.dimensionPaddingBottom)
                && Objects.equals(dimensionPaddingTop, that.dimensionPaddingTop)
                && liquidSettings == that.liquidSettings;
    }

    @Override
    public int hashCode() {
        return Objects.hash(written(startPool), written(startJigsawName), maxDepth, maxDistanceHorizontal, maxDistanceVertical,
                useExpansionHack, projectStartToHeightmap, dimensionPaddingBottom, dimensionPaddingTop, liquidSettings);
    }

    @Override
    public String toString() {
        return "JigsawOverrides[pool '" + written(startPool) + "', depth " + maxDepth + ", " + projectStartToHeightmap + "]";
    }

    /** A key as the pack wrote it, without surrounding blanks; blank for a key left out, so that left out and blank are equal. */
    private static String written(String key) {
        return key == null ? "" : key.trim();
    }
}
