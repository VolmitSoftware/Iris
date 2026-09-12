package art.arcane.iris.generation.hydrology.cave;

record CaveViewObservations(
        CavePosition[] positions,
        byte[] operations,
        byte[] values,
        int size
) {
    static CaveViewObservations empty() {
        return new CaveViewObservations(new CavePosition[0], new byte[0], new byte[0], 0);
    }

    boolean matches(CaveVoxelView view) {
        for (int index = 0; index < size; index++) {
            CavePosition position = positions[index];
            int actual = switch (operations[index]) {
                case RecordingCaveVoxelView.IN_WORLD -> view.isInWorld(position) ? 1 : 0;
                case RecordingCaveVoxelView.VOXEL -> view.voxelAt(position).ordinal();
                case RecordingCaveVoxelView.OPEN_TO_SURFACE -> view.isOpenToSurface(position) ? 1 : 0;
                case RecordingCaveVoxelView.ABOVE_TERRAIN_SURFACE ->
                        view.isAboveTerrainSurface(position) ? 1 : 0;
                default -> throw new IllegalStateException("Unknown cave view operation.");
            };
            if (actual != Byte.toUnsignedInt(values[index])) {
                return false;
            }
        }
        return true;
    }
}
