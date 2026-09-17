package art.arcane.iris.engine.object.structure.jigsaw;

import art.arcane.iris.engine.object.annotations.Desc;

@Desc("Controls waterlogging behavior for a configured native jigsaw.")
public enum IrisJigsawLiquidSettings {
    @Desc("Keeps the registered structure's own liquid setting; vanilla defaults to applying waterlogging when the definition omits it.")
    SOURCE,

    @Desc("Places pieces dry: existing water is displaced and waterloggable blocks stay unwaterlogged, leaving air-filled interiors underwater.")
    IGNORE_WATERLOGGING,

    @Desc("Re-applies pre-existing water into placed waterloggable blocks and floods from neighboring sources, so underwater pieces come out waterlogged.")
    APPLY_WATERLOGGING
}
