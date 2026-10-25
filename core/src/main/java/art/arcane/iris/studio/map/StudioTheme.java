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

import com.formdev.flatlaf.FlatDarkLaf;
import com.formdev.flatlaf.FlatLaf;
import com.formdev.flatlaf.FlatLightLaf;
import com.formdev.flatlaf.util.UIScale;
import art.arcane.iris.spi.IrisLogging;
import art.arcane.iris.configuration.IrisSettings;

import javax.swing.BorderFactory;
import javax.swing.UIManager;
import javax.swing.border.Border;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.EventQueue;
import java.awt.Font;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * The colours of IntelliJ's "New UI", light and dark, shared by FlatLaf's controls and everything the studio windows
 * draw themselves. The theme follows the system unless the user picked light or dark.
 */
final class StudioTheme {
    static final Color EDITOR_BACKGROUND = new ThemeColor(0xFFFFFF, 0x1E1F22);
    static final Color PANEL_BACKGROUND = new ThemeColor(0xF7F8FA, 0x2B2D30);
    static final Color HOVER_BACKGROUND = new ThemeColor(0xDFE1E5, 0x393B40);
    static final Color INACTIVE_SELECTION = new ThemeColor(0xDFE1E5, 0x43454A);
    static final Color SELECTION = new ThemeColor(0xD4E2FF, 0x2E436E);
    static final Color CONTROL_BORDER = new ThemeColor(0xC9CCD6, 0x4E5157);
    static final Color SEPARATOR = new ThemeColor(0xEBECF0, 0x1E1F22);
    static final Color TEXT = new ThemeColor(0x000000, 0xDFE1E5);
    static final ThemeColor SECONDARY_TEXT = new ThemeColor(0x6C707E, 0x9DA0A8);
    static final Color MUTED_TEXT = new ThemeColor(0x818594, 0x6F737A);
    static final Color ACCENT = new Color(0x3574F0);
    static final Color LINK = new ThemeColor(0x315FBD, 0x548AF7);
    static final Color ERROR = new ThemeColor(0xDB3B4B, 0xF75464);
    static final Color WARNING = new ThemeColor(0xC27D04, 0xF2C55C);
    static final Color SUCCESS = new ThemeColor(0x208A3C, 0x5FB865);
    static final Color OVERLAY_BACKGROUND = new ThemeColor(new Color(0xFF, 0xFF, 0xFF, 240), new Color(0x2B, 0x2D, 0x30, 236));
    static final Color OVERLAY_BORDER = new ThemeColor(0xDFE1E5, 0x43454A);
    static final int OVERLAY_ARC = 10;

    private static final boolean DARK_WITHOUT_SYSTEM_PREFERENCE = true;
    private static final AtomicBoolean INSTALLED = new AtomicBoolean();
    private static final ExecutorService SYSTEM_CHECK = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "Iris Theme Check");
        thread.setDaemon(true);
        return thread;
    });
    private static volatile boolean dark = DARK_WITHOUT_SYSTEM_PREFERENCE;
    private static volatile float pixelScale = 1F;
    private static final Map<String, String> SHARED_DEFAULTS = Map.ofEntries(
            Map.entry("Component.focusWidth", "1"),
            Map.entry("Component.innerFocusWidth", "0"),
            Map.entry("Component.arc", "8"),
            Map.entry("Button.arc", "8"),
            Map.entry("TextComponent.arc", "8"),
            Map.entry("List.selectionArc", "6"),
            Map.entry("List.selectionInsets", "0,6,0,6"),
            Map.entry("Tree.selectionArc", "6"),
            Map.entry("Tree.selectionInsets", "0,6,0,6"),
            Map.entry("Tree.border", "0,8,0,0"),
            Map.entry("ScrollBar.width", "10"),
            Map.entry("ScrollBar.thumbArc", "999"),
            Map.entry("ScrollBar.thumbInsets", "2,2,2,2"),
            Map.entry("ScrollBar.showButtons", "false"),
            Map.entry("ScrollPane.border", "0,0,0,0"),
            Map.entry("SplitPane.dividerSize", "1"),
            Map.entry("SplitPaneDivider.style", "plain"),
            Map.entry("ProgressBar.arc", "999"),
            Map.entry("ProgressBar.horizontalSize", "120,4")
    );
    private static final Map<String, String> DARK_DEFAULTS = withShared(Map.ofEntries(
            Map.entry("@background", "#2B2D30"),
            Map.entry("@foreground", "#DFE1E5"),
            Map.entry("@accentColor", "#3574F0"),
            Map.entry("@selectionBackground", "#2E436E"),
            Map.entry("@selectionInactiveBackground", "#43454A"),
            Map.entry("@disabledForeground", "#6F737A"),
            Map.entry("Component.borderColor", "#4E5157"),
            Map.entry("Component.focusedBorderColor", "#3574F0"),
            Map.entry("Button.toolbar.hoverBackground", "#393B40"),
            Map.entry("Button.toolbar.pressedBackground", "#43454A"),
            Map.entry("Button.toolbar.selectedBackground", "#43454A"),
            Map.entry("ToggleButton.toolbar.selectedBackground", "#43454A"),
            Map.entry("ScrollBar.track", "#2B2D30"),
            Map.entry("SplitPane.background", "#1E1F22"),
            Map.entry("Separator.foreground", "#1E1F22"),
            Map.entry("ToolTip.background", "#393B40")
    ));
    private static final Map<String, String> LIGHT_DEFAULTS = withShared(Map.ofEntries(
            Map.entry("@background", "#F7F8FA"),
            Map.entry("@foreground", "#000000"),
            Map.entry("@accentColor", "#3574F0"),
            Map.entry("@selectionBackground", "#D4E2FF"),
            Map.entry("@selectionForeground", "#000000"),
            Map.entry("@selectionInactiveBackground", "#DFE1E5"),
            Map.entry("@selectionInactiveForeground", "#000000"),
            Map.entry("@disabledForeground", "#A8ADBD"),
            Map.entry("Component.borderColor", "#C9CCD6"),
            Map.entry("Component.focusedBorderColor", "#3574F0"),
            Map.entry("Button.toolbar.hoverBackground", "#DFE1E5"),
            Map.entry("Button.toolbar.pressedBackground", "#C9CCD6"),
            Map.entry("Button.toolbar.selectedBackground", "#DFE1E5"),
            Map.entry("ToggleButton.toolbar.selectedBackground", "#DFE1E5"),
            Map.entry("ScrollBar.track", "#F7F8FA"),
            Map.entry("SplitPane.background", "#EBECF0"),
            Map.entry("Separator.foreground", "#EBECF0")
    ));

    private StudioTheme() {
    }

    static void install() {
        if (!INSTALLED.compareAndSet(false, true)) {
            return;
        }
        try {
            applyLookAndFeel(darkFor(mode()));
            pixelScale = UIScale.getUserScaleFactor();
        } catch (Throwable error) {
            IrisLogging.reportError(error);
            IrisLogging.warn("Could not apply the Iris studio theme, the studio windows will use Java's default look.");
        }
    }

    static boolean isDark() {
        return dark;
    }

    static Mode mode() {
        return Mode.fromSetting(IrisSettings.get().getGui().getTheme());
    }

    /**
     * Remembers the user's choice and switches every studio window to it.
     */
    static void choose(Mode next) {
        IrisSettings settings = IrisSettings.get();
        settings.getGui().setTheme(next.settingName());
        settings.forceSave();
        if (next == Mode.AUTO) {
            followSystem();
            return;
        }
        switchTo(next == Mode.DARK);
    }

    // Studio windows call this when they come to the front, so a change made in the system's settings shows up.
    static void followSystem() {
        if (!INSTALLED.get() || mode() != Mode.AUTO) {
            return;
        }
        SYSTEM_CHECK.execute(() -> {
            boolean systemDark = SystemAppearance.prefersDark().orElse(DARK_WITHOUT_SYSTEM_PREFERENCE);
            EventQueue.invokeLater(() -> switchTo(systemDark));
        });
    }

    private static boolean darkFor(Mode chosen) {
        return switch (chosen) {
            case LIGHT -> false;
            case DARK -> true;
            case AUTO -> SystemAppearance.prefersDark().orElse(DARK_WITHOUT_SYSTEM_PREFERENCE);
        };
    }

    private static void switchTo(boolean nextDark) {
        if (nextDark == dark) {
            return;
        }
        applyLookAndFeel(nextDark);
        FlatLaf.updateUI();
    }

    private static void applyLookAndFeel(boolean nextDark) {
        dark = nextDark;
        FlatLaf.setGlobalExtraDefaults(nextDark ? DARK_DEFAULTS : LIGHT_DEFAULTS);
        if (nextDark) {
            FlatDarkLaf.setup();
        } else {
            FlatLightLaf.setup();
        }
    }

    private static Map<String, String> withShared(Map<String, String> themeDefaults) {
        Map<String, String> merged = new HashMap<>(SHARED_DEFAULTS);
        merged.putAll(themeDefaults);
        return Map.copyOf(merged);
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

    enum Mode {
        AUTO("Match system"),
        LIGHT("Light"),
        DARK("Dark");

        private final String label;

        Mode(String label) {
            this.label = label;
        }

        String label() {
            return label;
        }

        String settingName() {
            return name().toLowerCase(Locale.ROOT);
        }

        static Mode fromSetting(String setting) {
            for (Mode mode : values()) {
                if (mode.settingName().equalsIgnoreCase(setting)) {
                    return mode;
                }
            }
            return AUTO;
        }
    }
}
