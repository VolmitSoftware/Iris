package art.arcane.iris.structure.jigsaw;

import art.arcane.volmlib.util.documentation.Description;

@Description("Controls waterlogging behavior for a configured native jigsaw.")
public enum IrisJigsawLiquidSettings {
    @Description("Keeps the registered structure's own liquid setting; vanilla defaults to applying waterlogging when the definition omits it.")
    SOURCE,

    @Description("Places pieces dry: existing water is displaced and waterloggable blocks stay unwaterlogged, leaving air-filled interiors underwater.")
    IGNORE_WATERLOGGING,

    @Description("Re-applies pre-existing water into placed waterloggable blocks and floods from neighboring sources, so underwater pieces come out waterlogged.")
    APPLY_WATERLOGGING
}
