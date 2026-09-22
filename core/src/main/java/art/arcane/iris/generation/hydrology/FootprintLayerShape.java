package art.arcane.iris.generation.hydrology;

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
        boolean fallingThroat,
        boolean inlet
) {
    double totalRadius() {
        return channelRadius + shoreWidth + gradingWidth;
    }
}
