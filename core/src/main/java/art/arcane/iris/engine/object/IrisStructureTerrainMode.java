package art.arcane.iris.engine.object;

import art.arcane.iris.engine.object.annotations.Desc;

@Desc("Controls how terrain is integrated with a structure.")
public enum IrisStructureTerrainMode {
    @Desc("Replays the registered native structure's own authored terrain adaptation (beard surface fitting, BURY/ENCAPSULATE fill, legacy template-air clearing). Does nothing for editable Iris structures, which have no source adaptation to replay.")
    SOURCE,

    @Desc("Disables all terrain integration. Pieces are placed into completely unmodified terrain and may end up embedded in solid rock or floating over dips.")
    PRESERVE,

    @Desc("Clears one straight axis-aligned box around the assembled pieces, expanded by horizontalPadding, ceilingPadding, and floorPadding. Ignores the carve shape and erosion settings.")
    BORE,

    @Desc("Clears the padded envelope using the configured carve shape (BOX, ROUNDED, or noise-eroded ERODED) so the cavity hugs the assembled pieces instead of a straight box.")
    FORCE_CARVE,

    @Desc("Raises surface terrain from processed solid rigid-template foundations at or below each authored ground plane with a 12-block falloff, even when the registered structure has no terrain adaptation. Existing higher terrain and authored air remain untouched.")
    VACUUM,

    @Desc("Cuts and fills exposed native structure foundations within flattenRange, blending into surrounding terrain across horizontalPadding blocks. Buried and submerged pieces retain their terrain.")
    FLATTEN,

    @Desc("Fills the padded piece volume with solid blocks before any piece is placed so shells, walls, and floors land in solid ground instead of pre-carved air. Only air and liquid cells are filled; existing terrain and structures are never overwritten. Native pieces then carve their own interiors.")
    ENCASE
}
