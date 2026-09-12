package art.arcane.iris.studio.view;

record NoiseViewport(double centerX, double centerZ, double blocksPerPixel) {
    static final double MIN_BLOCKS_PER_PIXEL = 0.0001D;
    static final double MAX_BLOCKS_PER_PIXEL = 1_000_000D;

    NoiseViewport {
        if (!Double.isFinite(centerX) || !Double.isFinite(centerZ)) {
            throw new IllegalArgumentException("Viewport center must be finite");
        }
        if (!Double.isFinite(blocksPerPixel) || blocksPerPixel <= 0D) {
            throw new IllegalArgumentException("Viewport scale must be finite and positive");
        }
    }

    double worldX(double screenX, int width) {
        return centerX + ((screenX - (width / 2D)) * blocksPerPixel);
    }

    double worldZ(double screenZ, int height) {
        return centerZ + ((screenZ - (height / 2D)) * blocksPerPixel);
    }

    NoiseViewport panPixels(double deltaX, double deltaZ) {
        return new NoiseViewport(
                centerX - (deltaX * blocksPerPixel),
                centerZ - (deltaZ * blocksPerPixel),
                blocksPerPixel
        );
    }

    NoiseViewport zoomAt(double screenX, double screenZ, int width, int height, double factor) {
        if (!Double.isFinite(factor) || factor <= 0D) {
            throw new IllegalArgumentException("Zoom factor must be finite and positive");
        }
        double anchorX = worldX(screenX, width);
        double anchorZ = worldZ(screenZ, height);
        double nextScale = Math.max(MIN_BLOCKS_PER_PIXEL,
                Math.min(MAX_BLOCKS_PER_PIXEL, blocksPerPixel * factor));
        double nextCenterX = anchorX - ((screenX - (width / 2D)) * nextScale);
        double nextCenterZ = anchorZ - ((screenZ - (height / 2D)) * nextScale);
        return new NoiseViewport(nextCenterX, nextCenterZ, nextScale);
    }
}
