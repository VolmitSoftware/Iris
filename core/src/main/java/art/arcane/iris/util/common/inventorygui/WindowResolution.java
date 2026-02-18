package art.arcane.iris.util.common.inventorygui;

import org.bukkit.event.inventory.InventoryType;

public enum WindowResolution {
    W9_H6(9, 6, InventoryType.CHEST),
    W5_H1(5, 1, InventoryType.HOPPER),
    W3_H3(3, 3, InventoryType.DROPPER);

    private final int width;
    private final int maxHeight;
    private final InventoryType type;

    WindowResolution(int w, int h, InventoryType type) {
        this.width = w;
        this.maxHeight = h;
        this.type = type;
    }

    public int getMaxWidthOffset() {
        return (getWidth() - 1) / 2;
    }

    public int getWidth() {
        return width;
    }

    public int getMaxHeight() {
        return maxHeight;
    }

    public InventoryType getType() {
        return type;
    }

    public art.arcane.volmlib.util.inventorygui.WindowResolution toShared() {
        return art.arcane.volmlib.util.inventorygui.WindowResolution.valueOf(name());
    }

    public static WindowResolution fromShared(art.arcane.volmlib.util.inventorygui.WindowResolution resolution) {
        return valueOf(resolution.name());
    }
}
