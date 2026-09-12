package art.arcane.iris.generation.hydrology;

interface CrossTileDraftAdmission {
    void prepare();

    CrossTilePublicationAdmission admit(HydrologyCaveCourseFilter.Result result);
}
