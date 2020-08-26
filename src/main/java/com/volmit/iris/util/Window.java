package com.volmit.iris.util;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public interface Window
{
	public Window setDecorator(WindowDecorator decorator);

	public WindowDecorator getDecorator();

	public WindowResolution getResolution();

	public Window setResolution(WindowResolution resolution);

	public Window clearElements();

	public Window close();

	public Window open();

	public Window callClosed();

	public Window updateInventory();

	public Window setVisible(boolean visible);

	public ItemStack computeItemStack(int viewportSlot);

	public int getLayoutRow(int viewportSlottedPosition);

	public int getLayoutPosition(int viewportSlottedPosition);

	public int getRealLayoutPosition(int viewportSlottedPosition);

	public int getRealPosition(int position, int row);

	public int getRow(int realPosition);

	public int getPosition(int realPosition);

	public boolean isVisible();

	public int getViewportPosition();

	public int getViewportSlots();

	public Window setViewportPosition(int position);

	public int getMaxViewportPosition();

	public Window scroll(int direction);

	public int getViewportHeight();

	public Window setViewportHeight(int height);

	public String getTitle();

	public Window setTitle(String title);

	public boolean hasElement(int position, int row);

	public Window setElement(int position, int row, Element e);

	public Element getElement(int position, int row);

	public Player getViewer();

	public Window reopen();

	public Window onClosed(Callback<Window> window);
}
