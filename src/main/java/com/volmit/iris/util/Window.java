package com.volmit.iris.util;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public interface Window {
    Window setDecorator(WindowDecorator decorator);

    WindowDecorator getDecorator();

    WindowResolution getResolution();

    Window setResolution(WindowResolution resolution);

    Window clearElements();

    Window close();

    Window open();

    Window callClosed();

    Window updateInventory();

    Window setVisible(boolean visible);

    ItemStack computeItemStack(int viewportSlot);

    int getLayoutRow(int viewportSlottedPosition);

    int getLayoutPosition(int viewportSlottedPosition);

    int getRealLayoutPosition(int viewportSlottedPosition);

    int getRealPosition(int position, int row);

    int getRow(int realPosition);

    int getPosition(int realPosition);

    boolean isVisible();

    int getViewportPosition();

    int getViewportSlots();

    Window setViewportPosition(int position);

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

    Window onClosed(Callback<Window> window);
}
