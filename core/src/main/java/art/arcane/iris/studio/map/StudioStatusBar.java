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

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import java.awt.Dimension;
import java.util.Objects;

/**
 * An IntelliJ style status bar: what is shown on the left, work in progress in the middle and where the pointer is on the right.
 */
final class StudioStatusBar extends JPanel {
    private static final long serialVersionUID = 6302468216735125611L;
    private static final int HEIGHT = 26;
    private static final int PROGRESS_STEPS = 1_000;

    private final JLabel summary = statusLabel();
    private final JLabel activity = statusLabel();
    private final JProgressBar progress = new JProgressBar(0, PROGRESS_STEPS);
    private final JLabel position = statusLabel();

    StudioStatusBar() {
        setLayout(new BoxLayout(this, BoxLayout.X_AXIS));
        setBackground(StudioTheme.PANEL_BACKGROUND);
        setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, StudioTheme.SEPARATOR),
                StudioTheme.emptyBorder(0, 10, 0, 10)
        ));
        setPreferredSize(StudioTheme.scaledSize(0, HEIGHT));
        progress.setMaximumSize(StudioTheme.scaledSize(120, 4));
        progress.setPreferredSize(StudioTheme.scaledSize(120, 4));
        progress.setVisible(false);
        allowShrinking(summary);
        allowShrinking(activity);
        add(summary);
        add(Box.createHorizontalGlue());
        add(activity);
        add(Box.createHorizontalStrut(StudioTheme.scale(8)));
        add(progress);
        add(Box.createHorizontalStrut(StudioTheme.scale(18)));
        add(position);
    }

    void showSummary(String text) {
        setTextIfChanged(summary, text);
        summary.setForeground(StudioTheme.SECONDARY_TEXT);
    }

    void showError(String text) {
        setTextIfChanged(summary, text);
        summary.setForeground(StudioTheme.ERROR);
    }

    void showActivity(String text) {
        setTextIfChanged(activity, text);
    }

    void showProgress(double completion) {
        boolean working = completion < 1D;
        progress.setVisible(working);
        if (working) {
            progress.setValue((int) Math.round(Math.max(0D, completion) * PROGRESS_STEPS));
        }
    }

    void showPosition(String text) {
        setTextIfChanged(position, text);
    }

    // Long text gives way with an ellipsis instead of pushing the other parts of the bar out of view.
    private static void allowShrinking(JLabel label) {
        label.setMinimumSize(new Dimension(0, 0));
    }

    private static JLabel statusLabel() {
        JLabel label = new JLabel();
        label.setFont(StudioTheme.smallFont());
        label.setForeground(StudioTheme.SECONDARY_TEXT);
        return label;
    }

    // Status text changes on every mouse move, and an unchanged label shouldn't trigger a relayout.
    private static void setTextIfChanged(JLabel label, String text) {
        String next = text == null ? "" : text;
        if (!Objects.equals(label.getText(), next)) {
            label.setText(next);
        }
    }
}
