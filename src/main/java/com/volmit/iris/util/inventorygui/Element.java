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

import com.volmit.iris.util.collection.KList;
import com.volmit.iris.util.data.MaterialBlock;
import com.volmit.iris.util.scheduling.Callback;
import org.bukkit.inventory.ItemStack;

@SuppressWarnings("ALL")
public interface Element {
    MaterialBlock getMaterial();

    Element setMaterial(MaterialBlock b);

    boolean isEnchanted();

    Element setEnchanted(boolean enchanted);

    String getId();

    String getName();

    Element setName(String name);

    double getProgress();

    Element setProgress(double progress);

    short getEffectiveDurability();

    int getCount();

    Element setCount(int c);

    ItemStack computeItemStack();

    Element setBackground(boolean bg);

    boolean isBackgrond();

    Element addLore(String loreLine);

    KList<String> getLore();

    Element call(ElementEvent event, Element context);

    Element onLeftClick(Callback<Element> clicked);

    Element onRightClick(Callback<Element> clicked);

    Element onShiftLeftClick(Callback<Element> clicked);

    Element onShiftRightClick(Callback<Element> clicked);

    Element onDraggedInto(Callback<Element> into);

    Element onOtherDraggedInto(Callback<Element> other);
}
