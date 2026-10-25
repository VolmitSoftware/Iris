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

import com.formdev.flatlaf.extras.FlatSVGIcon;

import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.Timer;
import java.awt.Color;
import java.awt.Dimension;
import java.lang.management.ManagementFactory;
import java.lang.management.OperatingSystemMXBean;
import java.util.Locale;

/**
 * The server's CPU and Java memory use, refreshed every second. The map runs inside the server, so heavy views show
 * up here. CPU counts like {@code top}: 100% is one thread kept busy.
 */
final class ResourceUsage extends JPanel {
    private static final long serialVersionUID = -6203817540921165472L;
    private static final int REFRESH_MILLIS = 1000;
    private static final double HIGH_USAGE = 0.8D;
    private static final double BYTES_PER_GIGABYTE = 1024D * 1024D * 1024D;
    private static final int THREADS = Runtime.getRuntime().availableProcessors();

    private final Timer refresher = new Timer(REFRESH_MILLIS, event -> refresh());
    private final OperatingSystemMXBean system = ManagementFactory.getOperatingSystemMXBean();
    private final Reading cpu = new Reading("iris/icons/lightning.svg", THREADS * 100 + "%",
            "Server CPU, 100% per busy thread, " + THREADS + " threads in all");
    private final Reading memory = new Reading("iris/icons/memory.svg", "99.9 / 99.9 GB", "Java memory the server uses, of its maximum");

    ResourceUsage() {
        setLayout(new BoxLayout(this, BoxLayout.X_AXIS));
        setOpaque(false);
        setBorder(StudioTheme.emptyBorder(0, 6, 0, 10));
        add(cpu);
        add(memory);
        refresh();
    }

    void start() {
        refresher.start();
    }

    void stop() {
        refresher.stop();
    }

    @Override
    public Dimension getMaximumSize() {
        return getPreferredSize();
    }

    private void refresh() {
        double cpuLoad = processCpuLoad();
        cpu.show(cpuLoad < 0D ? "–" : String.format(Locale.ROOT, "%d%%", Math.round(cpuLoad * THREADS * 100D)), cpuLoad >= HIGH_USAGE);
        Runtime runtime = Runtime.getRuntime();
        long usedBytes = runtime.totalMemory() - runtime.freeMemory();
        long maximumBytes = runtime.maxMemory();
        memory.show(String.format(Locale.ROOT, "%.1f / %.1f GB", usedBytes / BYTES_PER_GIGABYTE, maximumBytes / BYTES_PER_GIGABYTE),
                usedBytes >= maximumBytes * HIGH_USAGE);
    }

    // The share of every core the server uses; below zero when the JVM cannot tell, which only HotSpot's own bean can.
    private double processCpuLoad() {
        return system instanceof com.sun.management.OperatingSystemMXBean hotspot ? hotspot.getProcessCpuLoad() : -1D;
    }

    /**
     * One reading with its icon, as wide as its widest value so the toolbar holds still.
     */
    private static final class Reading extends JLabel {
        private static final long serialVersionUID = 7712069348214552014L;
        private static final int ICON_SIZE = 16;
        private static final int ICON_GAP = 4;
        private static final int READING_GAP = 12;

        private final String widestValue;
        private boolean high;

        private Reading(String iconPath, String widestValue, String tooltip) {
            this.widestValue = widestValue;
            FlatSVGIcon icon = new FlatSVGIcon(iconPath, ICON_SIZE, ICON_SIZE, ResourceUsage.class.getClassLoader());
            icon.setColorFilter(new FlatSVGIcon.ColorFilter(drawn -> color()));
            setIcon(icon);
            setIconTextGap(StudioTheme.scale(ICON_GAP));
            setFont(StudioTheme.smallFont());
            setBorder(StudioTheme.emptyBorder(0, READING_GAP, 0, 0));
            setToolTipText(tooltip);
        }

        private void show(String value, boolean highUsage) {
            high = highUsage;
            setText(value);
            setForeground(color());
            repaint();
        }

        private Color color() {
            return high ? StudioTheme.WARNING : StudioTheme.SECONDARY_TEXT;
        }

        @Override
        public Dimension getPreferredSize() {
            Dimension size = super.getPreferredSize();
            size.width = getInsets().left + getIcon().getIconWidth() + getIconTextGap()
                    + getFontMetrics(getFont()).stringWidth(widestValue) + getInsets().right;
            return size;
        }

        @Override
        public Dimension getMaximumSize() {
            return getPreferredSize();
        }
    }
}
