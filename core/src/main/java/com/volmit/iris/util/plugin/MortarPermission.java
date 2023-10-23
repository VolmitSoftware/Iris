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

package com.volmit.iris.util.plugin;

import com.volmit.iris.Iris;
import com.volmit.iris.util.collection.KList;
import org.bukkit.command.CommandSender;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Modifier;

public abstract class MortarPermission {
    private MortarPermission parent;

    public MortarPermission() {
        for (Field i : getClass().getDeclaredFields()) {
            if (i.isAnnotationPresent(Permission.class)) {
                try {
                    MortarPermission px = (MortarPermission) i.getType().getConstructor().newInstance();
                    px.setParent(this);
                    i.set(Modifier.isStatic(i.getModifiers()) ? null : this, px);
                } catch (IllegalArgumentException | IllegalAccessException | InstantiationException |
                         InvocationTargetException | NoSuchMethodException | SecurityException e) {
                    e.printStackTrace();
                    Iris.reportError(e);
                }
            }
        }
    }

    public KList<MortarPermission> getChildren() {
        KList<MortarPermission> p = new KList<>();

        for (Field i : getClass().getDeclaredFields()) {
            if (i.isAnnotationPresent(Permission.class)) {
                try {
                    p.add((MortarPermission) i.get(Modifier.isStatic(i.getModifiers()) ? null : this));
                } catch (IllegalArgumentException | IllegalAccessException | SecurityException e) {
                    e.printStackTrace();
                    Iris.reportError(e);
                }
            }
        }

        return p;
    }

    public String getFullNode() {
        if (hasParent()) {
            return getParent().getFullNode() + "." + getNode();
        }

        return getNode();
    }

    protected abstract String getNode();

    public abstract String getDescription();

    public abstract boolean isDefault();

    @Override
    public String toString() {
        return getFullNode();
    }

    public boolean hasParent() {
        return getParent() != null;
    }

    public MortarPermission getParent() {
        return parent;
    }

    public void setParent(MortarPermission parent) {
        this.parent = parent;
    }

    public boolean has(CommandSender sender) {
        return sender.hasPermission(getFullNode());
    }
}
