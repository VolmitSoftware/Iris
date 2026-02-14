package art.arcane.iris.util.inventorygui;

public enum ElementEvent {
    LEFT,
    RIGHT,
    SHIFT_LEFT,
    SHIFT_RIGHT,
    DRAG_INTO,
    OTHER_DRAG_INTO;

    public art.arcane.volmlib.util.inventorygui.ElementEvent toShared() {
        return art.arcane.volmlib.util.inventorygui.ElementEvent.valueOf(name());
    }

    public static ElementEvent fromShared(art.arcane.volmlib.util.inventorygui.ElementEvent event) {
        return valueOf(event.name());
    }
}
