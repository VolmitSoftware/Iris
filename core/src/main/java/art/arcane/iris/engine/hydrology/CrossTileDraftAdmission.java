package art.arcane.iris.engine.hydrology;

interface CrossTileDraftAdmission {
    void prepare();

    CrossTilePublicationAdmission admit(HydrologyCaveCourseFilter.Result result);
}
