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

package com.volmit.iris.core.gui;

import javax.swing.AbstractButton;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JToggleButton;

import static com.volmit.iris.core.gui.NoiseExplorerGUI.ACCENT;
import static com.volmit.iris.core.gui.NoiseExplorerGUI.BORDER;
import static com.volmit.iris.core.gui.NoiseExplorerGUI.FIELD_BACKGROUND;
import static com.volmit.iris.core.gui.NoiseExplorerGUI.ITEM_FONT;
import static com.volmit.iris.core.gui.NoiseExplorerGUI.PRIMARY_TEXT;
import static com.volmit.iris.core.gui.NoiseExplorerGUI.SECONDARY_TEXT;

final class ExplorerStyle {
    private static final java.awt.Color SELECTED_BACKGROUND = ACCENT.darker().darker();

    private ExplorerStyle() {
    }

    static JToggleButton toggleButton(String text) {
        JToggleButton button = new JToggleButton(text);
        applyFrame(button);
        button.addItemListener(event -> paintToggleState(button));
        paintToggleState(button);
        return button;
    }

    static JButton button(String text) {
        JButton button = new JButton(text);
        applyFrame(button);
        button.setBackground(FIELD_BACKGROUND);
        button.setForeground(PRIMARY_TEXT);
        return button;
    }

    private static void applyFrame(AbstractButton button) {
        button.setFocusPainted(false);
        button.setFont(ITEM_FONT);
        button.setContentAreaFilled(false);
        button.setOpaque(true);
        button.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(BORDER),
                BorderFactory.createEmptyBorder(5, 9, 5, 9)
        ));
    }

    private static void paintToggleState(JToggleButton button) {
        button.setBackground(button.isSelected() ? SELECTED_BACKGROUND : FIELD_BACKGROUND);
        button.setForeground(button.isSelected() ? PRIMARY_TEXT : SECONDARY_TEXT);
    }
}
