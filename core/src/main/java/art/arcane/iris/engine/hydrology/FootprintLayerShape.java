package art.arcane.iris.engine.hydrology;

record FootprintLayerShape(
        int channelRadius,
        double shoreWidth,
        double gradingWidth,
        int bed,
        int fluidHead,
        int ceiling,
        boolean ellipsoid,
        boolean archedChannel,
        boolean roundedSurfaceBed,
        boolean organicBoundary,
        boolean fallingThroat
) {
    double totalRadius() {
        return channelRadius + shoreWidth + gradingWidth;
    }
}
