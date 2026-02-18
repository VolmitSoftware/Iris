package art.arcane.iris.util.common.inventorygui;

import art.arcane.volmlib.util.inventorygui.WindowDecorator;

import art.arcane.volmlib.util.inventorygui.Element;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public interface Window {
    WindowDecorator getDecorator();

    Window setDecorator(WindowDecorator decorator);

    art.arcane.volmlib.util.inventorygui.WindowResolution getResolution();

    Window setResolution(art.arcane.volmlib.util.inventorygui.WindowResolution resolution);

    default Window setResolution(WindowResolution resolution) {
        return setResolution(resolution.toShared());
    }

    default WindowResolution getLocalResolution() {
        return WindowResolution.fromShared(getResolution());
    }

    Window clearElements();

    Window close();

    Window open();

    Window callClosed();

    Window updateInventory();

    ItemStack computeItemStack(int viewportSlot);

    int getLayoutRow(int viewportSlottedPosition);

    int getLayoutPosition(int viewportSlottedPosition);

    int getRealLayoutPosition(int viewportSlottedPosition);

    int getRealPosition(int position, int row);

    int getRow(int realPosition);

    int getPosition(int realPosition);

    boolean isVisible();

    Window setVisible(boolean visible);

    int getViewportPosition();

    Window setViewportPosition(int position);

    int getViewportSlots();

    int getMaxViewportPosition();

    Window scroll(int direction);

    int getViewportHeight();

    Window setViewportHeight(int height);

    String getTitle();

    Window setTitle(String title);

    boolean hasElement(int position, int row);

    Window setElement(int position, int row, Element e);

    Element getElement(int position, int row);

    Player getViewer();

    Window reopen();

    Window onClosed(art.arcane.volmlib.util.scheduling.Callback<art.arcane.volmlib.util.inventorygui.Window> window);
}
