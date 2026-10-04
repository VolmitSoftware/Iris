package art.arcane.iris.engine.image;

import art.arcane.iris.engine.object.image.IrisImageColorMode;

public record IrisImageMapSourceMetadata(
        String format,
        IrisImageColorMode colorMode,
        int width,
        int height,
        int colorComponents,
        int channels,
        int bitDepth,
        boolean alpha,
        double minimumAlpha,
        double maximumAlpha,
        String decodedContentHash
) {
}
