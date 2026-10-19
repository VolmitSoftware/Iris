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

import com.formdev.flatlaf.FlatDarkLaf;
import com.formdev.flatlaf.FlatLaf;
import com.formdev.flatlaf.util.UIScale;
import com.volmit.iris.Iris;

import javax.swing.BorderFactory;
import javax.swing.UIManager;
import javax.swing.border.Border;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * The colours of IntelliJ's dark "New UI", shared by FlatLaf's controls and everything the studio windows draw themselves.
 */
final class StudioTheme {
    static final Color EDITOR_BACKGROUND = new Color(0x1E1F22);
    static final Color PANEL_BACKGROUND = new Color(0x2B2D30);
    static final Color HOVER_BACKGROUND = new Color(0x393B40);
    static final Color INACTIVE_SELECTION = new Color(0x43454A);
    static final Color SELECTION = new Color(0x2E436E);
    static final Color CONTROL_BORDER = new Color(0x4E5157);
    static final Color SEPARATOR = new Color(0x1E1F22);
    static final Color TEXT = new Color(0xDFE1E5);
    static final Color SECONDARY_TEXT = new Color(0x9DA0A8);
    static final Color MUTED_TEXT = new Color(0x6F737A);
    static final Color ACCENT = new Color(0x3574F0);
    static final Color LINK = new Color(0x548AF7);
    static final Color ERROR = new Color(0xF75464);
    static final Color WARNING = new Color(0xF2C55C);
    static final Color SUCCESS = new Color(0x5FB865);
    static final Color OVERLAY_BACKGROUND = new Color(0x2B, 0x2D, 0x30, 236);
    static final Color OVERLAY_BORDER = new Color(0x43454A);
    static final String SECONDARY_TEXT_HTML = "#9DA0A8";
    static final int OVERLAY_ARC = 10;

    private static final AtomicBoolean INSTALLED = new AtomicBoolean();
    private static volatile float pixelScale = 1F;
    private static final Map<String, String> INTELLIJ_NEW_UI_DEFAULTS = Map.ofEntries(
            Map.entry("@background", "#2B2D30"),
            Map.entry("@foreground", "#DFE1E5"),
            Map.entry("@accentColor", "#3574F0"),
            Map.entry("@selectionBackground", "#2E436E"),
            Map.entry("@selectionInactiveBackground", "#43454A"),
            Map.entry("@disabledForeground", "#6F737A"),
            Map.entry("Component.borderColor", "#4E5157"),
            Map.entry("Component.focusedBorderColor", "#3574F0"),
            Map.entry("Component.focusWidth", "1"),
            Map.entry("Component.innerFocusWidth", "0"),
            Map.entry("Component.arc", "8"),
            Map.entry("Button.arc", "8"),
            Map.entry("TextComponent.arc", "8"),
            Map.entry("Button.toolbar.hoverBackground", "#393B40"),
            Map.entry("Button.toolbar.pressedBackground", "#43454A"),
            Map.entry("Button.toolbar.selectedBackground", "#43454A"),
            Map.entry("ToggleButton.toolbar.selectedBackground", "#43454A"),
            Map.entry("List.selectionArc", "6"),
            Map.entry("List.selectionInsets", "0,6,0,6"),
            Map.entry("Tree.selectionArc", "6"),
            Map.entry("Tree.selectionInsets", "0,6,0,6"),
            Map.entry("Tree.border", "0,8,0,0"),
            Map.entry("ScrollBar.width", "10"),
            Map.entry("ScrollBar.thumbArc", "999"),
            Map.entry("ScrollBar.thumbInsets", "2,2,2,2"),
            Map.entry("ScrollBar.showButtons", "false"),
            Map.entry("ScrollBar.track", "#2B2D30"),
            Map.entry("ScrollPane.border", "0,0,0,0"),
            Map.entry("SplitPane.dividerSize", "1"),
            Map.entry("SplitPaneDivider.style", "plain"),
            Map.entry("SplitPane.background", "#1E1F22"),
            Map.entry("Separator.foreground", "#1E1F22"),
            Map.entry("ToolTip.background", "#393B40"),
            Map.entry("ProgressBar.arc", "999"),
            Map.entry("ProgressBar.horizontalSize", "120,4")
    );

    private StudioTheme() {
    }

    static void install() {
        if (!INSTALLED.compareAndSet(false, true)) {
            return;
        }
        try {
            FlatLaf.setGlobalExtraDefaults(INTELLIJ_NEW_UI_DEFAULTS);
            FlatDarkLaf.setup();
            pixelScale = UIScale.getUserScaleFactor();
        } catch (Throwable error) {
            Iris.reportError(error);
            Iris.warn("Could not apply the Iris studio theme, the studio windows will use Java's default look.");
        }
    }

    /**
     * FlatLaf grows fonts and its own controls to the screen's DPI; every size the studio sets itself has to grow with them.
     */
    static int scale(int pixels) {
        return Math.round(pixels * pixelScale);
    }

    static double scale(double pixels) {
        return pixels * pixelScale;
    }

    static Dimension scaledSize(int width, int height) {
        return new Dimension(scale(width), scale(height));
    }

    static Border emptyBorder(int top, int left, int bottom, int right) {
        return BorderFactory.createEmptyBorder(scale(top), scale(left), scale(bottom), scale(right));
    }

    static Font font() {
        return themeFont("defaultFont", Font.PLAIN, 13);
    }

    static Font smallFont() {
        return themeFont("small.font", Font.PLAIN, 11);
    }

    static Font boldFont() {
        return font().deriveFont(Font.BOLD);
    }

    static Font titleFont() {
        return font().deriveFont(Font.BOLD, font().getSize2D() + 1F);
    }

    static Font sectionFont() {
        return smallFont().deriveFont(Font.BOLD);
    }

    static Font monospacedFont() {
        return themeFont("monospaced.font", Font.PLAIN, 12);
    }

    private static Font themeFont(String key, int fallbackStyle, int fallbackSize) {
        Font font = UIManager.getFont(key);
        return font != null ? font : new Font(Font.SANS_SERIF, fallbackStyle, fallbackSize);
    }
}
