/*
 * Iris is a World Generator for Minecraft Bukkit Servers
 * Copyright (c) 2022 Arcane Arts (Volmit Software)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.volmit.iris.util.inventorygui;

import com.volmit.iris.Iris;
import com.volmit.iris.util.collection.KMap;
import com.volmit.iris.util.collection.KSet;
import com.volmit.iris.util.scheduling.Callback;
import com.volmit.iris.util.scheduling.J;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

public class UIWindow implements Window, Listener {
    private final Player viewer;
    private final KMap<Integer, Element> elements;
    private WindowDecorator decorator;
    private Callback<Window> eClose;
    private WindowResolution resolution;
    private String title;
    private boolean visible;
    private int viewportPosition;
    private int viewportSize;
    private int highestRow;
    private Inventory inventory;
    private int clickcheck;
    private boolean doubleclicked;

    public UIWindow(Player viewer) {
        clickcheck = 0;
        doubleclicked = false;
        this.viewer = viewer;
        this.elements = new KMap<>();
        setTitle("");
        setDecorator(new UIVoidDecorator());
        setResolution(WindowResolution.W9_H6);
        setViewportHeight(clip(3, 1, getResolution().getMaxHeight()).intValue());
        setViewportPosition(0);
    }

    @EventHandler
    public void on(InventoryClickEvent e) {
        if (!e.getWhoClicked().equals(viewer)) {
            return;
        }

        if (!isVisible()) {
            return;
        }

        // 1.14 bukkit api change, removed getTitle() and getName() from Inventory.class
        if (!viewer.getOpenInventory().getTitle().equals(title)) {
            return;
        }

        if (e.getClickedInventory() == null) {
            return;
        }

        if (!e.getView().getType().equals(getResolution().getType())) {
            return;
        }

        if (e.getClickedInventory().getType().equals(getResolution().getType())) {
            Element element = getElement(getLayoutPosition(e.getSlot()), getLayoutRow(e.getSlot()));

            switch (e.getAction()) {
                case CLONE_STACK:
                case UNKNOWN:
                case SWAP_WITH_CURSOR:
                case PLACE_SOME:
                case PLACE_ONE:
                case PLACE_ALL:
                case PICKUP_SOME:
                case PICKUP_ONE:
                case PICKUP_HALF:
                case PICKUP_ALL:
                case NOTHING:
                case MOVE_TO_OTHER_INVENTORY:
                case HOTBAR_SWAP:
                case HOTBAR_MOVE_AND_READD:
                case DROP_ONE_SLOT:
                case DROP_ONE_CURSOR:
                case DROP_ALL_SLOT:
                case DROP_ALL_CURSOR:
                case COLLECT_TO_CURSOR:
                    break;
            }

            switch (e.getClick()) {
                case DOUBLE_CLICK:
                    doubleclicked = true;
                    break;
                case LEFT:

                    clickcheck++;

                    if (clickcheck == 1) {
                        J.s(() ->
                        {
                            if (clickcheck == 1) {
                                clickcheck = 0;

                                if (element != null) {
                                    element.call(ElementEvent.LEFT, element);
                                }
                            }
                        });
                    } else if (clickcheck == 2) {
                        J.s(() ->
                        {
                            if (doubleclicked) {
                                doubleclicked = false;
                            } else {
                                scroll(1);
                            }

                            clickcheck = 0;
                        });
                    }

                    break;
                case RIGHT:
                    if (element != null) {
                        element.call(ElementEvent.RIGHT, element);
                    } else {
                        scroll(-1);
                    }
                    break;
                case SHIFT_LEFT:
                    if (element != null) {
                        element.call(ElementEvent.SHIFT_LEFT, element);
                    }
                    break;
                case SHIFT_RIGHT:
                    if (element != null) {
                        element.call(ElementEvent.SHIFT_RIGHT, element);
                    }
                    break;
                case SWAP_OFFHAND:
                case UNKNOWN:
                case WINDOW_BORDER_RIGHT:
                case WINDOW_BORDER_LEFT:
                case NUMBER_KEY:
                case MIDDLE:
                case DROP:
                case CREATIVE:
                case CONTROL_DROP:
                default:
                    break;
            }
        }

        e.setCancelled(true);

    }

    @EventHandler
    public void on(InventoryCloseEvent e) {
        if (!e.getPlayer().equals(viewer)) {
            return;
        }

        if (!e.getPlayer().getOpenInventory().getTitle().equals(title)) {
            return;
        }

        if (isVisible()) {
            close();
            callClosed();
        }
    }

    @Override
    public WindowDecorator getDecorator() {
        return decorator;
    }

    @Override
    public UIWindow setDecorator(WindowDecorator decorator) {
        this.decorator = decorator;
        return this;
    }

    @Override
    public UIWindow close() {
        setVisible(false);
        return this;
    }

    @Override
    public UIWindow open() {
        setVisible(true);
        return this;
    }

    @Override
    public boolean isVisible() {
        return visible;
    }

    @Override
    public UIWindow setVisible(boolean visible) {
        if (isVisible() == visible) {
            return this;
        }

        if (visible) {
            Bukkit.getPluginManager().registerEvents(this, Iris.instance);

            if (getResolution().getType().equals(InventoryType.CHEST)) {
                inventory = Bukkit.createInventory(null, getViewportHeight() * 9, getTitle());
            } else {
                inventory = Bukkit.createInventory(null, getResolution().getType(), getTitle());
            }

            viewer.openInventory(inventory);
            this.visible = visible;
            updateInventory();
        } else {
            this.visible = visible;
            HandlerList.unregisterAll(this);
            viewer.closeInventory();
        }

        this.visible = visible;
        return this;
    }

    @Override
    public int getViewportPosition() {
        return viewportPosition;
    }

    @Override
    public UIWindow setViewportPosition(int viewportPosition) {
        this.viewportPosition = viewportPosition;
        scroll(0);
        updateInventory();

        return this;
    }

    @Override
    public int getMaxViewportPosition() {
        return Math.max(0, highestRow - getViewportHeight());
    }

    @Override
    public UIWindow scroll(int direction) {
        viewportPosition = (int) clip(viewportPosition + direction, 0, getMaxViewportPosition()).doubleValue();
        updateInventory();

        return this;
    }

    @Override
    public int getViewportHeight() {
        return viewportSize;
    }

    @Override
    public UIWindow setViewportHeight(int height) {
        viewportSize = (int) clip(height, 1, getResolution().getMaxHeight()).doubleValue();

        if (isVisible()) {
            reopen();
        }

        return this;
    }

    @Override
    public String getTitle() {
        return title;
    }

    @Override
    public UIWindow setTitle(String title) {
        this.title = title;

        if (isVisible()) {
            reopen();
        }

        return this;
    }

    @Override
    public UIWindow setElement(int position, int row, Element e) {
        if (row > highestRow) {
            highestRow = row;
        }

        elements.put(getRealPosition((int) clip(position, -getResolution().getMaxWidthOffset(), getResolution().getMaxWidthOffset()).doubleValue(), row), e);
        updateInventory();
        return this;
    }

    @Override
    public Element getElement(int position, int row) {
        return elements.get(getRealPosition((int) clip(position, -getResolution().getMaxWidthOffset(), getResolution().getMaxWidthOffset()).doubleValue(), row));
    }

    @Override
    public Player getViewer() {
        return viewer;
    }

    @Override
    public UIWindow onClosed(Callback<Window> window) {
        eClose = window;
        return this;
    }

    @Override
    public int getViewportSlots() {
        return getViewportHeight() * getResolution().getWidth();
    }

    @Override
    public int getLayoutRow(int viewportSlottedPosition) {
        return getRow(getRealLayoutPosition(viewportSlottedPosition));
    }

    @Override
    public int getLayoutPosition(int viewportSlottedPosition) {
        return getPosition(viewportSlottedPosition);
    }

    @Override
    public int getRealLayoutPosition(int viewportSlottedPosition) {
        return getRealPosition(getPosition(viewportSlottedPosition), getRow(viewportSlottedPosition) + getViewportPosition());
    }

    @Override
    public int getRealPosition(int position, int row) {
        return (int) (((row * getResolution().getWidth()) + getResolution().getMaxWidthOffset()) + clip(position, -getResolution().getMaxWidthOffset(), getResolution().getMaxWidthOffset()));
    }

    @Override
    public int getRow(int realPosition) {
        return realPosition / getResolution().getWidth();
    }

    @Override
    public int getPosition(int realPosition) {
        return (realPosition % getResolution().getWidth()) - getResolution().getMaxWidthOffset();
    }

    @Override
    public Window callClosed() {
        if (eClose != null) {
            eClose.run(this);
        }

        return this;
    }

    @Override
    public boolean hasElement(int position, int row) {
        return getElement(position, row) != null;
    }

    @Override
    public WindowResolution getResolution() {
        return resolution;
    }

    public Double clip(double value, double min, double max) {
        return Math.min(max, Math.max(min, value));
    }

    @Override
    public Window setResolution(WindowResolution resolution) {
        close();
        this.resolution = resolution;
        setViewportHeight((int) clip(getViewportHeight(), 1, getResolution().getMaxHeight()).doubleValue());
        return this;
    }

    @Override
    public Window clearElements() {
        highestRow = 0;
        elements.clear();
        updateInventory();
        return this;
    }

    @Override
    public Window updateInventory() {
        if (isVisible()) {
            ItemStack[] is = inventory.getContents();
            @SuppressWarnings("MismatchedQueryAndUpdateOfCollection") KSet<ItemStack> isf = new KSet<>();

            for (int i = 0; i < is.length; i++) {
                ItemStack isc = is[i];
                ItemStack isx = computeItemStack(i);
                int layoutRow = getLayoutRow(i);
                int layoutPosition = getLayoutPosition(i);

                if (isx != null && !hasElement(layoutPosition, layoutRow)) {
                    ItemStack gg = isx.clone();
                    gg.setAmount(gg.getAmount() + 1);
                    isf.add(gg);
                }

                if (((isc == null) != (isx == null)) || isx != null && isc != null && !isc.equals(isx)) {
                    inventory.setItem(i, isx);
                }
            }
        }

        return this;
    }

    @Override
    public ItemStack computeItemStack(int viewportSlot) {
        int layoutRow = getLayoutRow(viewportSlot);
        int layoutPosition = getLayoutPosition(viewportSlot);
        Element e = hasElement(layoutPosition, layoutRow) ? getElement(layoutPosition, layoutRow) : getDecorator().onDecorateBackground(this, layoutPosition, layoutRow);

        if (e != null) {
            return e.computeItemStack();
        }

        return null;
    }

    @Override
    public Window reopen() {
        return this.close().open();
    }
}
