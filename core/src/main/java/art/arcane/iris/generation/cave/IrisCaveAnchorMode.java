package art.arcane.iris.generation.cave;

import art.arcane.volmlib.util.documentation.Description;

@Description("Defines which carved-space anchor to target for cave object placement.")
public enum IrisCaveAnchorMode {
    @Description("Use the active cave profile default anchor mode.")
    PROFILE_DEFAULT,

    @Description("Target cave floor anchors where carved space has solid support below.")
    FLOOR,

    @Description("Target cave ceiling anchors where carved space has solid support above.")
    CEILING,

    @Description("Target carved positions with no immediate solid support above or below.")
    CENTER,

    @Description("Target any carved-space anchor.")
    ANY
}
