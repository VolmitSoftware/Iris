package art.arcane.iris.studio.view;

record VisionViewport(double centerX, double centerZ, double blocksPerPixel) {
    VisionViewport {
        if (!Double.isFinite(centerX) || !Double.isFinite(centerZ)) {
            throw new IllegalArgumentException("Vision viewport center must be finite");
        }
        if (!Double.isFinite(blocksPerPixel) || blocksPerPixel <= 0D) {
            throw new IllegalArgumentException("Vision viewport scale must be finite and positive");
        }
    }

    double worldX(double screenX, int width) {
        return centerX + (screenX - width * 0.5D) * blocksPerPixel;
    }

    double worldZ(double screenZ, int height) {
        return centerZ + (screenZ - height * 0.5D) * blocksPerPixel;
    }

    VisionViewport zoomAt(
            double screenX,
            double screenZ,
            int width,
            int height,
            double factor,
            double minimumScale,
            double maximumScale
    ) {
        if (!Double.isFinite(factor) || factor <= 0D) {
            throw new IllegalArgumentException("Vision zoom factor must be finite and positive");
        }
        double anchorX = worldX(screenX, width);
        double anchorZ = worldZ(screenZ, height);
        double nextScale = Math.max(minimumScale, Math.min(maximumScale, blocksPerPixel * factor));
        return new VisionViewport(
                anchorX - (screenX - width * 0.5D) * nextScale,
                anchorZ - (screenZ - height * 0.5D) * nextScale,
                nextScale
        );
    }
}
