package art.arcane.iris.engine.framework;

/**
 * Changes a module makes to a jigsaw structure's registered definition before the platform assembles a start of it,
 * as the platform code applies them: a blank text or a null value keeps the definition's own. The heightmap and liquid
 * settings are the names of the API's {@code JigsawOverrides} constants, or {@code SOURCE} to keep the definition's (the
 * API leaves such an option out instead).
 */
public record NativeJigsawOverrides(
        String startPool,
        String startJigsawName,
        Integer maxDepth,
        Integer maxDistanceHorizontal,
        Integer maxDistanceVertical,
        Boolean useExpansionHack,
        String projectStartToHeightmap,
        Integer dimensionPaddingBottom,
        Integer dimensionPaddingTop,
        String liquidSettings
) {
    public static final String SOURCE = "SOURCE";
}
