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

package art.arcane.iris.studio.map;

import com.formdev.flatlaf.FlatClientProperties;
import com.formdev.flatlaf.icons.FlatSearchIcon;

import javax.swing.BorderFactory;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JRadioButtonMenuItem;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.JToggleButton;
import javax.swing.JToolBar;
import javax.swing.ScrollPaneConstants;
import java.awt.BorderLayout;
import java.awt.Insets;

/**
 * Builds the IntelliJ style controls the studio windows share: a flat main toolbar, borderless toolbar buttons and search boxes.
 */
final class StudioToolbar {
    private static final int TOOLBAR_HEIGHT_PADDING = 5;

    private StudioToolbar() {
    }

    static JToolBar create() {
        JToolBar toolbar = new JToolBar();
        toolbar.setFloatable(false);
        toolbar.setRollover(true);
        toolbar.setBackground(StudioTheme.PANEL_BACKGROUND);
        toolbar.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, StudioTheme.SEPARATOR),
                StudioTheme.emptyBorder(TOOLBAR_HEIGHT_PADDING, 8, TOOLBAR_HEIGHT_PADDING, 8)
        ));
        return toolbar;
    }

    static JLabel label(String text) {
        JLabel label = new JLabel(text);
        label.setForeground(StudioTheme.SECONDARY_TEXT);
        label.setBorder(StudioTheme.emptyBorder(0, 6, 0, 6));
        return label;
    }

    static JButton button(String text, String tooltip, Runnable action) {
        JButton button = toolbarButton(text, tooltip);
        button.addActionListener(event -> action.run());
        return button;
    }

    /**
     * Opens a menu to pick light, dark or the system's theme.
     */
    static JButton themeButton() {
        JButton button = toolbarButton("Theme", "Light, dark, or whatever the system uses");
        button.addActionListener(event -> themeMenu().show(button, 0, button.getHeight()));
        return button;
    }

    private static JPopupMenu themeMenu() {
        JPopupMenu menu = new JPopupMenu();
        ButtonGroup choices = new ButtonGroup();
        StudioTheme.Mode current = StudioTheme.mode();
        for (StudioTheme.Mode mode : StudioTheme.Mode.values()) {
            JRadioButtonMenuItem item = new JRadioButtonMenuItem(mode.label(), mode == current);
            item.addActionListener(event -> StudioTheme.choose(mode));
            choices.add(item);
            menu.add(item);
        }
        return menu;
    }

    private static JButton toolbarButton(String text, String tooltip) {
        JButton button = new JButton(text);
        button.setToolTipText(tooltip);
        button.setFocusable(false);
        button.setMargin(new Insets(4, 8, 4, 8));
        button.putClientProperty(FlatClientProperties.BUTTON_TYPE, FlatClientProperties.BUTTON_TYPE_TOOLBAR_BUTTON);
        return button;
    }

    static JToggleButton toggle(String text, String tooltip) {
        JToggleButton toggle = new JToggleButton(text);
        toggle.setToolTipText(tooltip);
        toggle.setFocusable(false);
        toggle.setMargin(new Insets(4, 8, 4, 8));
        toggle.putClientProperty(FlatClientProperties.BUTTON_TYPE, FlatClientProperties.BUTTON_TYPE_TOOLBAR_BUTTON);
        return toggle;
    }

    // An underlined tab like IntelliJ's tool window tabs.
    static JToggleButton tab(String text) {
        JToggleButton tab = new JToggleButton(text);
        tab.setFocusable(false);
        tab.putClientProperty(FlatClientProperties.BUTTON_TYPE, FlatClientProperties.BUTTON_TYPE_TAB);
        return tab;
    }

    static void makeSearchField(JTextField field, String placeholder) {
        field.putClientProperty(FlatClientProperties.PLACEHOLDER_TEXT, placeholder);
        field.putClientProperty(FlatClientProperties.TEXT_FIELD_LEADING_ICON, new FlatSearchIcon());
        field.putClientProperty(FlatClientProperties.TEXT_FIELD_SHOW_CLEAR_BUTTON, true);
        field.setToolTipText(placeholder);
    }

    static JPanel padded(JComponent content, int top, int left, int bottom, int right) {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setOpaque(false);
        panel.setBorder(StudioTheme.emptyBorder(top, left, bottom, right));
        panel.add(content, BorderLayout.CENTER);
        return panel;
    }

    static JScrollPane scroll(JComponent content) {
        JScrollPane scrollPane = new JScrollPane(content);
        scrollPane.setBorder(BorderFactory.createEmptyBorder());
        scrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        scrollPane.getVerticalScrollBar().setUnitIncrement(18);
        return scrollPane;
    }
}
