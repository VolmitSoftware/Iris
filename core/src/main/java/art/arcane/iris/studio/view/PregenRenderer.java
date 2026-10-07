/*
 * Iris is a World Generator for Minecraft Servers
 * Copyright (c) 2026 Arcane Arts (Volmit Software)
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

package art.arcane.iris.studio.view;

import art.arcane.iris.configuration.IrisSettings;
import art.arcane.iris.localization.DesktopUiMessages;
import art.arcane.iris.localization.IrisLanguage;
import art.arcane.iris.spi.IrisLogging;
import art.arcane.volmlib.util.format.Form;
import art.arcane.volmlib.util.localization.MessageArgument;
import art.arcane.volmlib.util.localization.TextKey;

import javax.swing.AbstractAction;
import javax.swing.AbstractButton;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.Scrollable;
import javax.swing.JTextArea;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.plaf.basic.BasicButtonUI;
import javax.swing.plaf.basic.BasicGraphicsUtils;
import javax.swing.plaf.basic.BasicProgressBarUI;
import javax.swing.plaf.basic.BasicScrollBarUI;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Font;
import java.awt.Frame;
import java.awt.GridLayout;
import java.awt.Graphics;
import java.awt.LayoutManager;
import java.awt.Rectangle;
import java.awt.event.ActionEvent;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.Objects;

public final class PregenRenderer extends JPanel {
    private static final long serialVersionUID = 2094606939770332040L;
    static final Color BACKGROUND = new Color(12, 15, 22);
    static final Color BORDER = new Color(105, 119, 139);
    private static final Color SURFACE = new Color(17, 20, 29);
    private static final Color FIELD = new Color(29, 34, 47);
    private static final Color TEXT = new Color(230, 234, 242);
    private static final Color MUTED = new Color(163, 173, 191);
    private static final Color ACCENT = new Color(101, 194, 149);
    private static final Color ERROR = new Color(255, 139, 139);
    static final Color GENERATING = new Color(204, 168, 107);
    static final Color GENERATED = ACCENT;
    static final Color EXISTS = new Color(93, 133, 112);
    static final Color NETWORK = new Color(157, 138, 176);
    static final Color NETWORK_GENERATING = new Color(129, 112, 145);
    private static final Font BODY_FONT = new Font(Font.SANS_SERIF, Font.PLAIN, 13);
    private static final Font LABEL_FONT = new Font(Font.SANS_SERIF, Font.PLAIN, 11);
    private static final Font VALUE_FONT = new Font(Font.SANS_SERIF, Font.BOLD, 15);

    private final PregenRenderSource source;
    private final Runnable onPause;
    private final PregenMapPanel map;
    private final Timer repaintTimer;
    private final JLabel worldLabel = label("", new Font(Font.SANS_SERIF, Font.BOLD, 21), TEXT);
    private final JLabel phaseLabel = label("", BODY_FONT, MUTED);
    private final JLabel countLabel = label("", BODY_FONT, TEXT);
    private final JLabel percentLabel = label("", new Font(Font.SANS_SERIF, Font.BOLD, 24), TEXT);
    private final JLabel currentValue = label("", VALUE_FONT, TEXT);
    private final JLabel overallValue = label("", VALUE_FONT, TEXT);
    private final JLabel thirtyValue = label("", VALUE_FONT, TEXT);
    private final JLabel sixtyValue = label("", VALUE_FONT, TEXT);
    private final JLabel etaValue = label("", VALUE_FONT, TEXT);
    private final JLabel elapsedValue = label("", VALUE_FONT, TEXT);
    private final JLabel memoryValue = label("", VALUE_FONT, TEXT);
    private final JLabel pressureValue = label("", VALUE_FONT, TEXT);
    private final JLabel methodLabel = label("", LABEL_FONT, MUTED);
    private final JTextArea failureLabel = wrappedText("", BODY_FONT, ERROR, SURFACE);
    private String controlFailure;
    private final JButton pauseButton = new JButton();
    private final JProgressBar progressBar = new JProgressBar(0, 10_000);
    private volatile boolean renderingEnabled;
    private volatile boolean disposed;
    private volatile JFrame frame;
    private PregenRenderSnapshot displayedSnapshot;

    private PregenRenderer(PregenRenderSource source, Runnable onPause) {
        this.source = Objects.requireNonNull(source, "Generation view source");
        this.onPause = onPause;
        failureLabel.setFocusable(true);
        PregenRenderSnapshot initial = source.renderSnapshot();
        map = new PregenMapPanel(source.renderMapState());
        setLayout(new BorderLayout(0, 18));
        setBackground(BACKGROUND);
        setBorder(BorderFactory.createEmptyBorder(20, 24, 18, 24));
        add(header(), BorderLayout.NORTH);
        JPanel content = panel(new BorderLayout(18, 0), BACKGROUND);
        content.add(mapArea(initial.bounds()), BorderLayout.CENTER);
        content.add(statusArea(), BorderLayout.EAST);
        add(content, BorderLayout.CENTER);
        add(wrappedText(text(DesktopUiMessages.PREGEN_WINDOW_HINT), LABEL_FONT, MUTED, BACKGROUND), BorderLayout.SOUTH);
        installPauseControl();
        repaintTimer = new Timer(IrisSettings.get().getGui().isMaximumPregenGuiFPS() ? 4 : 250,
                event -> refresh());
        refresh();
    }

    public static PregenRenderer open(String title, PregenRenderSource source, Runnable onPause) {
        if (!SwingUtilities.isEventDispatchThread()) {
            throw new IllegalStateException("The pregeneration window must open on the Swing event thread");
        }
        PregenRenderer renderer = new PregenRenderer(source, onPause);
        JFrame frame = new JFrame(title);
        GuiHost.prepareFrame(frame);
        renderer.frame = frame;
        renderer.renderingEnabled = true;
        frame.setContentPane(renderer);
        frame.setSize(1040, 760);
        frame.setMinimumSize(new Dimension(760, 620));
        frame.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosed(WindowEvent event) {
                renderer.disposeRenderer();
            }
        });
        frame.addWindowStateListener(event -> renderer.setRenderingEnabled((event.getNewState() & Frame.ICONIFIED) == 0));
        frame.setVisible(true);
        renderer.repaintTimer.start();
        return renderer;
    }

    public boolean isVisibleFrame() {
        return renderingEnabled && !disposed;
    }

    public void close() {
        if (!SwingUtilities.isEventDispatchThread()) {
            SwingUtilities.invokeLater(this::close);
            return;
        }
        JFrame activeFrame = frame;
        if (activeFrame != null) {
            activeFrame.dispose();
        } else {
            disposeRenderer();
        }
    }

    private JPanel header() {
        JPanel heading = panel(new BorderLayout(16, 0), BACKGROUND);
        JPanel identity = panel(new GridLayout(2, 1, 0, 4), BACKGROUND);
        identity.add(worldLabel);
        identity.add(phaseLabel);
        heading.add(identity, BorderLayout.CENTER);
        heading.add(pauseButton, BorderLayout.EAST);
        JPanel counts = panel(new BorderLayout(), BACKGROUND);
        counts.add(countLabel, BorderLayout.WEST);
        counts.add(percentLabel, BorderLayout.EAST);
        progressBar.setUI(new BasicProgressBarUI());
        progressBar.setForeground(ACCENT);
        progressBar.setBackground(FIELD);
        progressBar.setBorderPainted(false);
        progressBar.setPreferredSize(new Dimension(1, 6));
        progressBar.setFocusable(false);
        JPanel progress = panel(new BorderLayout(0, 9), BACKGROUND);
        progress.add(counts, BorderLayout.NORTH);
        progress.add(progressBar, BorderLayout.SOUTH);
        JPanel header = panel(new BorderLayout(0, 18), BACKGROUND);
        header.add(heading, BorderLayout.NORTH);
        header.add(progress, BorderLayout.SOUTH);
        return header;
    }

    private JPanel mapArea(PregenRenderSnapshot.Bounds bounds) {
        JPanel area = panel(new BorderLayout(0, 8), BACKGROUND);
        map.getAccessibleContext().setAccessibleName(text(DesktopUiMessages.PREGEN_MAP));
        area.add(map, BorderLayout.CENTER);
        JLabel coordinates = label(text(DesktopUiMessages.PREGEN_BOUNDS,
                MessageArgument.trusted("minX", Form.f(bounds.minX())),
                MessageArgument.trusted("maxX", Form.f(bounds.maxX())),
                MessageArgument.trusted("minZ", Form.f(bounds.minZ())),
                MessageArgument.trusted("maxZ", Form.f(bounds.maxZ()))), LABEL_FONT, MUTED);
        JPanel mapFooter = panel(new BorderLayout(0, 10), BACKGROUND);
        mapFooter.add(coordinates, BorderLayout.NORTH);
        mapFooter.add(legend(), BorderLayout.CENTER);
        area.add(mapFooter, BorderLayout.SOUTH);
        return area;
    }

    private JScrollPane statusArea() {
        JPanel status = new StatusPanel();
        status.setBackground(SURFACE);
        status.setBorder(BorderFactory.createEmptyBorder(18, 16, 16, 16));
        GridBagConstraints constraints = new GridBagConstraints();
        constraints.gridx = 0;
        constraints.gridy = 0;
        constraints.weightx = 1;
        constraints.fill = GridBagConstraints.HORIZONTAL;
        constraints.anchor = GridBagConstraints.NORTHWEST;
        constraints.insets = new Insets(0, 0, 18, 0);
        status.add(failureLabel, constraints);
        constraints.gridy++;
        currentValue.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 25));
        etaValue.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 23));
        status.add(metric(DesktopUiMessages.PREGEN_CURRENT, currentValue), constraints);
        constraints.gridy++;
        status.add(metric(DesktopUiMessages.PREGEN_ETA, etaValue), constraints);
        constraints.gridy++;
        constraints.insets = new Insets(0, 0, 10, 0);
        status.add(metricRow(DesktopUiMessages.PREGEN_OVERALL, overallValue), constraints);
        constraints.gridy++;
        status.add(metricRow(DesktopUiMessages.PREGEN_THIRTY, thirtyValue), constraints);
        constraints.gridy++;
        status.add(metricRow(DesktopUiMessages.PREGEN_SIXTY, sixtyValue), constraints);
        constraints.gridy++;
        status.add(metricRow(DesktopUiMessages.PREGEN_ELAPSED, elapsedValue), constraints);
        constraints.gridy++;
        status.add(metricRow(DesktopUiMessages.PREGEN_MEMORY_LABEL, memoryValue), constraints);
        constraints.gridy++;
        status.add(metricRow(DesktopUiMessages.PREGEN_PRESSURE, pressureValue), constraints);
        constraints.gridy++;
        status.add(methodLabel, constraints);
        constraints.gridy++;
        constraints.weighty = 1;
        status.add(panel(new BorderLayout(), SURFACE), constraints);
        JScrollPane scroll = new JScrollPane(status);
        scroll.setBorder(BorderFactory.createLineBorder(BORDER));
        scroll.getViewport().setBackground(SURFACE);
        scroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.setPreferredSize(new Dimension(276, 1));
        scroll.setMinimumSize(new Dimension(260, 0));
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        scroll.getVerticalScrollBar().setUI(new BasicScrollBarUI() {
            @Override
            protected void configureScrollBarColors() {
                thumbColor = BORDER;
                thumbDarkShadowColor = BORDER;
                thumbHighlightColor = BORDER;
                thumbLightShadowColor = BORDER;
                trackColor = FIELD;
            }

            @Override
            protected JButton createDecreaseButton(int orientation) {
                return scrollEnd();
            }

            @Override
            protected JButton createIncreaseButton(int orientation) {
                return scrollEnd();
            }

            private JButton scrollEnd() {
                JButton button = new JButton();
                button.setPreferredSize(new Dimension(0, 0));
                button.setFocusable(false);
                return button;
            }
        });
        return scroll;
    }

    private JPanel legend() {
        JPanel legend = panel(new GridLayout(0, 2, 12, 7), BACKGROUND);
        legend.add(legendItem(DesktopUiMessages.PREGEN_WAITING, PregenMapState.WAITING));
        legend.add(legendItem(DesktopUiMessages.PREGEN_GENERATING, GENERATING));
        legend.add(legendItem(DesktopUiMessages.PREGEN_READY, GENERATED));
        legend.add(legendItem(DesktopUiMessages.PREGEN_EXISTING, EXISTS));
        legend.add(legendItem(DesktopUiMessages.PREGEN_NETWORK, NETWORK));
        legend.setToolTipText(text(DesktopUiMessages.PREGEN_TERRAIN_HINT));
        return legend;
    }

    private JPanel legendItem(TextKey key, Color color) {
        JPanel item = panel(new BorderLayout(7, 0), BACKGROUND);
        JPanel swatch = panel(new BorderLayout(), color);
        swatch.setPreferredSize(new Dimension(10, 10));
        swatch.setBorder(BorderFactory.createLineBorder(BORDER));
        JPanel marker = panel(new BorderLayout(), BACKGROUND);
        marker.add(swatch, BorderLayout.NORTH);
        item.add(marker, BorderLayout.WEST);
        item.add(wrappedText(text(key), LABEL_FONT, MUTED, BACKGROUND), BorderLayout.CENTER);
        return item;
    }

    private JPanel metric(TextKey title, JLabel value) {
        JPanel metric = panel(new BorderLayout(0, 5), SURFACE);
        metric.add(wrappedText(text(title), LABEL_FONT, MUTED, SURFACE), BorderLayout.NORTH);
        metric.add(value, BorderLayout.CENTER);
        return metric;
    }

    private JPanel metricRow(TextKey title, JLabel value) {
        JPanel row = panel(new BorderLayout(8, 0), SURFACE);
        value.setFont(BODY_FONT);
        row.add(wrappedText(text(title), LABEL_FONT, MUTED, SURFACE), BorderLayout.CENTER);
        row.add(value, BorderLayout.EAST);
        return row;
    }

    private void installPauseControl() {
        pauseButton.setUI(new BasicButtonUI() {
            @Override
            protected void paintText(Graphics graphics, AbstractButton button, Rectangle bounds, String text) {
                if (button.isEnabled()) {
                    super.paintText(graphics, button, bounds, text);
                    return;
                }
                graphics.setColor(MUTED);
                BasicGraphicsUtils.drawStringUnderlineCharAt(graphics, text, button.getDisplayedMnemonicIndex(),
                        bounds.x, bounds.y + graphics.getFontMetrics().getAscent());
            }
        });
        pauseButton.setBackground(FIELD);
        pauseButton.setForeground(TEXT);
        pauseButton.setFont(BODY_FONT);
        pauseButton.putClientProperty("html.disable", Boolean.TRUE);
        pauseButton.setFocusPainted(false);
        pauseButton.setPreferredSize(new Dimension(108, 46));
        setPauseBorder(false);
        pauseButton.addFocusListener(new FocusAdapter() {
            @Override
            public void focusGained(FocusEvent event) {
                setPauseBorder(true);
            }

            @Override
            public void focusLost(FocusEvent event) {
                setPauseBorder(false);
            }
        });
        pauseButton.addActionListener(event -> togglePause());
        pauseButton.getInputMap(JComponent.WHEN_FOCUSED).put(
                KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), "activatePause");
        pauseButton.getActionMap().put("activatePause", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent event) {
                pauseButton.doClick();
            }
        });
        getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(KeyEvent.VK_P, 0, true), "pause");
        getActionMap().put("pause", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent event) {
                togglePause();
            }
        });
    }

    private void setPauseBorder(boolean focused) {
        pauseButton.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(focused ? ACCENT : BORDER, 2),
                BorderFactory.createEmptyBorder(8, 14, 8, 14)));
    }

    private void togglePause() {
        if (onPause == null || !pauseButton.isEnabled()) {
            return;
        }
        try {
            onPause.run();
            controlFailure = null;
            displayedSnapshot = null;
            refresh();
        } catch (RuntimeException failure) {
            IrisLogging.reportError("Unable to change pregeneration pause state.", failure);
            controlFailure = text(DesktopUiMessages.PREGEN_CONTROL_FAILED) + "\n"
                    + Objects.toString(failure.getMessage(), failure.getClass().getSimpleName());
            updateStatus(source.renderSnapshot());
        }
    }

    private void refresh() {
        if (disposed) {
            return;
        }
        PregenRenderSnapshot snapshot = source.renderSnapshot();
        if (!snapshot.equals(displayedSnapshot)) {
            updateStatus(snapshot);
            displayedSnapshot = snapshot;
        }
        if (map.flush()) {
            map.repaint();
        }
    }

    private void updateStatus(PregenRenderSnapshot snapshot) {
        PregeneratorJob.PregenProgress progress = snapshot.progress();
        boolean initializing = snapshot.phase() == PregenRenderSnapshot.Phase.INITIALIZING;
        boolean paused = snapshot.phase() == PregenRenderSnapshot.Phase.PAUSED;
        worldLabel.setText(progress.worldName() == null ? text(DesktopUiMessages.PREGEN_TITLE) : progress.worldName());
        worldLabel.setToolTipText(worldLabel.getText());
        phaseLabel.setText(text(switch (snapshot.phase()) {
            case INITIALIZING -> DesktopUiMessages.PREGEN_INITIALIZING;
            case GENERATING -> DesktopUiMessages.PREGEN_GENERATING;
            case PAUSED -> DesktopUiMessages.PREGEN_PAUSED;
            case SAVING -> DesktopUiMessages.PREGEN_SAVING;
            case STOPPING -> DesktopUiMessages.PREGEN_STOPPING;
            case COMPLETED -> DesktopUiMessages.PREGEN_COMPLETED;
            case ERROR -> DesktopUiMessages.PREGEN_ERROR;
        }));
        phaseLabel.setForeground(snapshot.phase() == PregenRenderSnapshot.Phase.ERROR ? ERROR : MUTED);
        countLabel.setText(text(DesktopUiMessages.PREGEN_PROGRESS,
                MessageArgument.trusted("generated", Form.f(progress.generated())),
                MessageArgument.trusted("total", Form.f(progress.totalChunks()))));
        percentLabel.setText(text(DesktopUiMessages.PREGEN_PERCENT,
                MessageArgument.trusted("percent", Form.pc(Math.max(0D, Math.min(1D, progress.percent() / 100D)), 1))));
        progressBar.setValue((int) Math.max(0D, Math.min(10_000D, progress.percent() * 100D)));
        progressBar.getAccessibleContext().setAccessibleName(countLabel.getText());
        map.getAccessibleContext().setAccessibleDescription(countLabel.getText() + ". " + phaseLabel.getText());
        pauseButton.setText(text(paused ? DesktopUiMessages.PREGEN_RESUME : DesktopUiMessages.PREGEN_PAUSE));
        pauseButton.setToolTipText(text(paused ? DesktopUiMessages.PREGEN_RESUME_HINT : DesktopUiMessages.PREGEN_PAUSE_HINT));
        pauseButton.setEnabled(onPause != null && switch (snapshot.phase()) {
            case GENERATING, PAUSED, SAVING -> true;
            default -> false;
        });
        currentValue.setText(initializing ? pending() : rate(progress.chunksPerSecond()));
        overallValue.setText(initializing ? pending() : rate(progress.overallChunksPerSecond()));
        thirtyValue.setText(initializing ? pending() : rate(progress.thirtySecondChunksPerSecond()));
        sixtyValue.setText(initializing ? pending() : rate(progress.sixtySecondChunksPerSecond()));
        etaValue.setText((snapshot.phase() != PregenRenderSnapshot.Phase.GENERATING
                && snapshot.phase() != PregenRenderSnapshot.Phase.SAVING
                && snapshot.phase() != PregenRenderSnapshot.Phase.COMPLETED) || progress.eta() < 0
                || (progress.eta() == 0 && progress.chunksRemaining() > 0)
                ? pending() : Form.duration(progress.eta(), 2));
        elapsedValue.setText(initializing ? pending() : Form.duration(progress.elapsed(), 2));
        memoryValue.setText(snapshot.usedMemoryBytes() < 0 ? pending() : text(DesktopUiMessages.PREGEN_MEMORY_USAGE,
                MessageArgument.trusted("used", Form.memSize(snapshot.usedMemoryBytes(), 1)),
                MessageArgument.trusted("usage", Form.pc(snapshot.memoryUsage(), 0))));
        pressureValue.setText(text(DesktopUiMessages.PREGEN_PRESSURE_VALUE,
                MessageArgument.trusted("rate", Form.memSize(snapshot.allocationBytesPerSecond(), 0))));
        String method = progress.method() == null ? pending() : progress.method();
        if (snapshot.cached()) {
            method = text(DesktopUiMessages.PREGEN_CACHED, MessageArgument.untrusted("method", method));
        }
        methodLabel.setText(text(DesktopUiMessages.PREGEN_METHOD, MessageArgument.untrusted("method", method)));
        methodLabel.setToolTipText(methodLabel.getText());
        String failureSummary = progress.failed() > 0 ? text(DesktopUiMessages.PREGEN_FAILED,
                MessageArgument.trusted("count", Form.f(progress.failed()))) : "";
        if (snapshot.failure() != null) {
            failureSummary += (failureSummary.isEmpty() ? "" : "\n")
                    + text(DesktopUiMessages.PREGEN_ERROR_DETAILS) + "\n" + snapshot.failure();
        }
        if (controlFailure != null) {
            failureSummary += (failureSummary.isEmpty() ? "" : "\n") + controlFailure;
        }
        if (!failureSummary.equals(failureLabel.getText())) {
            failureLabel.setText(failureSummary);
        }
        failureLabel.setVisible(!failureSummary.isEmpty());
    }

    private void setRenderingEnabled(boolean enabled) {
        renderingEnabled = enabled && !disposed;
        if (renderingEnabled) {
            refresh();
            repaintTimer.start();
        } else {
            repaintTimer.stop();
        }
    }

    private void disposeRenderer() {
        if (disposed) {
            return;
        }
        disposed = true;
        renderingEnabled = false;
        frame = null;
        repaintTimer.stop();
        map.disposeMap();
    }

    private static JPanel panel(LayoutManager layout, Color background) {
        JPanel panel = new JPanel(layout);
        panel.setBackground(background);
        return panel;
    }

    private static JLabel label(String text, Font font, Color foreground) {
        JLabel label = new JLabel(text);
        label.putClientProperty("html.disable", Boolean.TRUE);
        label.setFont(font);
        label.setForeground(foreground);
        return label;
    }

    private static JTextArea wrappedText(String text, Font font, Color foreground, Color background) {
        JTextArea area = new JTextArea(text);
        area.setEditable(false);
        area.setFocusable(false);
        area.setLineWrap(true);
        area.setWrapStyleWord(true);
        area.setFont(font);
        area.setForeground(foreground);
        area.setBackground(background);
        area.setBorder(BorderFactory.createEmptyBorder());
        return area;
    }

    private static String pending() {
        return text(DesktopUiMessages.PREGEN_METHOD_PENDING);
    }

    private static String rate(double rate) {
        return text(DesktopUiMessages.PREGEN_RATE, MessageArgument.trusted("rate", Form.f(rate, 1)));
    }

    private static String text(TextKey key, MessageArgument... arguments) {
        return IrisLanguage.plain(key, arguments);
    }

    private static final class StatusPanel extends JPanel implements Scrollable {
        private StatusPanel() {
            super(new GridBagLayout());
        }

        @Override
        public Dimension getPreferredScrollableViewportSize() {
            return getPreferredSize();
        }

        @Override
        public int getScrollableUnitIncrement(Rectangle visible, int orientation, int direction) {
            return 16;
        }

        @Override
        public int getScrollableBlockIncrement(Rectangle visible, int orientation, int direction) {
            return Math.max(16, visible.height - 16);
        }

        @Override
        public boolean getScrollableTracksViewportWidth() {
            return true;
        }

        @Override
        public boolean getScrollableTracksViewportHeight() {
            return getParent() != null && getPreferredSize().height < getParent().getHeight();
        }
    }
}
