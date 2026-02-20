package art.arcane.iris.engine.object;

import art.arcane.iris.engine.object.annotations.Desc;

@Desc("Defines which carved-space anchor to target for cave object placement.")
public enum IrisCaveAnchorMode {
    @Desc("Use the active cave profile default anchor mode.")
    PROFILE_DEFAULT,

    @Desc("Target cave floor anchors where carved space has solid support below.")
    FLOOR,

    @Desc("Target cave ceiling anchors where carved space has solid support above.")
    CEILING,

    @Desc("Target carved positions with no immediate solid support above or below.")
    CENTER,

    @Desc("Target any carved-space anchor.")
    ANY
}
