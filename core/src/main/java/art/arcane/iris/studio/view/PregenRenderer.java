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
import art.arcane.iris.studio.map.IslandOverlay;
import art.arcane.iris.studio.map.IslandPanel;
import art.arcane.iris.studio.map.MapHost;
import art.arcane.iris.studio.map.StudioTheme;
import art.arcane.iris.studio.map.StudioToolbar;
import art.arcane.volmlib.util.format.Form;
import art.arcane.volmlib.util.localization.MessageArgument;
import art.arcane.volmlib.util.localization.TextKey;

import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JToggleButton;
import javax.swing.JToolBar;
import javax.swing.KeyStroke;
import javax.swing.Scrollable;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.text.DefaultCaret;
import javax.swing.text.JTextComponent;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Frame;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.KeyboardFocusManager;
import java.awt.LayoutManager;
import java.awt.Rectangle;
import java.awt.event.ActionEvent;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.Objects;

public final class PregenRenderer extends JPanel {
    private static final long serialVersionUID = 2094606939770332040L;
    private static final Color BACKGROUND = StudioTheme.EDITOR_BACKGROUND;
    private static final Color BORDER = StudioTheme.CONTROL_BORDER;
    private static final Color SURFACE = StudioTheme.PANEL_BACKGROUND;
    private static final Color TEXT = StudioTheme.TEXT;
    private static final Color MUTED = StudioTheme.SECONDARY_TEXT;
    private static final Color ACCENT = StudioTheme.ACCENT;
    private static final Color ERROR = StudioTheme.ERROR;
    static final Color GENERATING = new Color(204, 168, 107);
    static final Color GENERATED = new Color(101, 194, 149);
    static final Color EXISTS = new Color(93, 133, 112);
    static final Color NETWORK = new Color(157, 138, 176);
    static final Color NETWORK_GENERATING = new Color(129, 112, 145);

    private final PregenRenderSource source;
    private final Runnable onPause;
    private final PregenMapPanel map;
    private final Timer repaintTimer;
    private final JLabel worldLabel = label("", StudioTheme.titleFont(), TEXT);
    private final JLabel phaseLabel = label("", StudioTheme.font(), MUTED);
    private final JLabel countLabel = label("", StudioTheme.font(), TEXT);
    private final JLabel percentLabel = label("", StudioTheme.titleFont(), TEXT);
    private final JLabel currentValue = label("", StudioTheme.boldFont(), TEXT);
    private final JLabel overallValue = label("", StudioTheme.boldFont(), TEXT);
    private final JLabel thirtyValue = label("", StudioTheme.boldFont(), TEXT);
    private final JLabel sixtyValue = label("", StudioTheme.boldFont(), TEXT);
    private final JLabel etaValue = label("", StudioTheme.boldFont(), TEXT);
    private final JLabel elapsedValue = label("", StudioTheme.boldFont(), TEXT);
    private final JLabel memoryValue = label("", StudioTheme.boldFont(), TEXT);
    private final JLabel pressureValue = label("", StudioTheme.boldFont(), TEXT);
    private final JLabel methodLabel = label("", StudioTheme.smallFont(), MUTED);
    private final JTextArea failureLabel = wrappedText("", StudioTheme.font(), ERROR, SURFACE);
    private String controlFailure;
    private final JButton pauseButton = StudioToolbar.button("", "", this::togglePause);
    private final JToggleButton gridToggle = StudioToolbar.toggle(text(DesktopUiMessages.PREGEN_GRID),
            text(DesktopUiMessages.PREGEN_GRID_HINT));
    private final JLabel coordinatesLabel = label("", StudioTheme.smallFont(), MUTED);
    private final JLabel zoomLabel = label("", StudioTheme.smallFont(), MUTED);
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
        setLayout(new BorderLayout());
        setBackground(BACKGROUND);
        map.getAccessibleContext().setAccessibleName(text(DesktopUiMessages.PREGEN_MAP));
        map.setViewListener(this::updateMapStatus);
        IslandPanel sidebar = new IslandPanel(statusArea(initial.bounds()));
        sidebar.addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent event) {
                map.setCoveredLeftWidth(sidebar.getWidth());
            }
        });
        add(toolbar(), BorderLayout.NORTH);
        add(new IslandOverlay(sidebar, map, StudioTheme.scale(300), StudioTheme.scale(260),
                StudioTheme.scale(300)), BorderLayout.CENTER);
        add(statusBar(), BorderLayout.SOUTH);
        installControls();
        updateMapStatus();
        repaintTimer = new Timer(IrisSettings.get().getGui().isMaximumPregenGuiFPS() ? 4 : 250,
                event -> refresh());
        refresh();
    }

    public static PregenRenderer open(String title, PregenRenderSource source, Runnable onPause) {
        if (!SwingUtilities.isEventDispatchThread()) {
            throw new IllegalStateException("The pregeneration window must open on the Swing event thread");
        }
        JFrame frame = MapHost.createFrame(title);
        PregenRenderer renderer = new PregenRenderer(source, onPause);
        renderer.frame = frame;
        renderer.renderingEnabled = true;
        frame.setContentPane(renderer);
        frame.setSize(MapHost.fitToScreen(StudioTheme.scaledSize(1240, 800)));
        frame.setMinimumSize(StudioTheme.scaledSize(820, 520));
        frame.setLocationRelativeTo(null);
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

    private JToolBar toolbar() {
        JToolBar toolbar = StudioToolbar.create();
        worldLabel.setBorder(StudioTheme.emptyBorder(0, 6, 0, 14));
        worldLabel.setMaximumSize(StudioTheme.scaledSize(220, 36));
        worldLabel.setMinimumSize(StudioTheme.scaledSize(80, 24));
        toolbar.add(worldLabel);
        toolbar.add(pauseButton);
        toolbar.addSeparator();
        toolbar.add(StudioToolbar.button(text(DesktopUiMessages.PREGEN_FIT),
                text(DesktopUiMessages.PREGEN_FIT_HINT), map::resetView));
        toolbar.add(StudioToolbar.button("−", text(DesktopUiMessages.PREGEN_ZOOM_OUT), map::zoomOut));
        toolbar.add(StudioToolbar.button("+", text(DesktopUiMessages.PREGEN_ZOOM_IN), map::zoomIn));
        gridToggle.addActionListener(event -> toggleGrid());
        toolbar.add(gridToggle);
        toolbar.add(Box.createHorizontalGlue());
        toolbar.add(StudioToolbar.themeButton());
        toolbar.add(StudioToolbar.button(text(DesktopUiMessages.PREGEN_SHORTCUTS),
                text(DesktopUiMessages.PREGEN_SHORTCUTS_HINT), map::toggleHelp));
        for (Component control : toolbar.getComponents()) {
            if (control instanceof JButton || control instanceof JToggleButton) {
                control.setFocusable(true);
            }
        }
        return toolbar;
    }

    private JPanel statusBar() {
        JPanel status = panel(new BorderLayout(12, 0), SURFACE);
        status.setBorder(StudioTheme.emptyBorder(5, 12, 5, 12));
        status.add(coordinatesLabel, BorderLayout.CENTER);
        status.add(zoomLabel, BorderLayout.EAST);
        status.setToolTipText(text(DesktopUiMessages.PREGEN_WINDOW_HINT));
        return status;
    }

    private void updateMapStatus() {
        String coordinates = map.hoverCoordinates();
        coordinatesLabel.setText(coordinates == null ? text(DesktopUiMessages.PREGEN_NAVIGATION_HINT) : coordinates);
        zoomLabel.setText(text(DesktopUiMessages.PREGEN_ZOOM,
                MessageArgument.trusted("zoom", Form.pc(map.zoom(), 0))));
    }

    private JScrollPane statusArea(PregenRenderSnapshot.Bounds bounds) {
        JPanel status = new StatusPanel();
        status.setBackground(SURFACE);
        status.setBorder(StudioTheme.emptyBorder(14, 12, 12, 12));
        GridBagConstraints constraints = new GridBagConstraints();
        constraints.gridx = 0;
        constraints.gridy = 0;
        constraints.weightx = 1;
        constraints.fill = GridBagConstraints.HORIZONTAL;
        constraints.anchor = GridBagConstraints.NORTHWEST;
        constraints.insets = new Insets(0, 0, StudioTheme.scale(12), 0);
        status.add(label(text(DesktopUiMessages.PREGEN_TITLE), StudioTheme.titleFont(), TEXT), constraints);
        constraints.gridy++;
        status.add(phaseLabel, constraints);
        constraints.gridy++;
        JPanel progress = panel(new BorderLayout(0, StudioTheme.scale(8)), SURFACE);
        progress.add(percentLabel, BorderLayout.NORTH);
        progressBar.setForeground(ACCENT);
        progressBar.setBackground(BACKGROUND);
        progressBar.setBorderPainted(false);
        progressBar.setPreferredSize(StudioTheme.scaledSize(1, 4));
        progressBar.setFocusable(false);
        progress.add(progressBar, BorderLayout.CENTER);
        progress.add(countLabel, BorderLayout.SOUTH);
        status.add(progress, constraints);
        constraints.gridy++;
        status.add(failureLabel, constraints);
        constraints.gridy++;
        status.add(metricRow(DesktopUiMessages.PREGEN_CURRENT, currentValue), constraints);
        constraints.gridy++;
        status.add(metricRow(DesktopUiMessages.PREGEN_ETA, etaValue), constraints);
        constraints.gridy++;
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
        status.add(legend(), constraints);
        constraints.gridy++;
        status.add(wrappedText(text(DesktopUiMessages.PREGEN_BOUNDS,
                MessageArgument.trusted("minX", Form.f(bounds.minX())),
                MessageArgument.trusted("maxX", Form.f(bounds.maxX())),
                MessageArgument.trusted("minZ", Form.f(bounds.minZ())),
                MessageArgument.trusted("maxZ", Form.f(bounds.maxZ()))), StudioTheme.smallFont(), MUTED, SURFACE), constraints);
        constraints.gridy++;
        status.add(wrappedText(text(DesktopUiMessages.PREGEN_WINDOW_HINT), StudioTheme.smallFont(), MUTED, SURFACE), constraints);
        constraints.gridy++;
        constraints.weighty = 1;
        status.add(panel(new BorderLayout(), SURFACE), constraints);
        JScrollPane scroll = StudioToolbar.scroll(status);
        scroll.getViewport().setBackground(SURFACE);
        return scroll;
    }

    private JPanel legend() {
        JPanel legend = panel(new GridLayout(0, 2, StudioTheme.scale(12), StudioTheme.scale(7)), SURFACE);
        legend.setBorder(StudioTheme.emptyBorder(8, 0, 4, 0));
        legend.add(legendItem(DesktopUiMessages.PREGEN_WAITING, PregenMapState.WAITING));
        legend.add(legendItem(DesktopUiMessages.PREGEN_GENERATING, GENERATING));
        legend.add(legendItem(DesktopUiMessages.PREGEN_READY, GENERATED));
        legend.add(legendItem(DesktopUiMessages.PREGEN_EXISTING, EXISTS));
        legend.add(legendItem(DesktopUiMessages.PREGEN_NETWORK, NETWORK));
        legend.setToolTipText(text(DesktopUiMessages.PREGEN_TERRAIN_HINT));
        return legend;
    }

    private JPanel legendItem(TextKey key, Color color) {
        JPanel item = panel(new BorderLayout(StudioTheme.scale(7), 0), SURFACE);
        JPanel swatch = panel(new BorderLayout(), color);
        swatch.setPreferredSize(StudioTheme.scaledSize(10, 10));
        swatch.setBorder(BorderFactory.createLineBorder(BORDER));
        JPanel marker = panel(new BorderLayout(), SURFACE);
        marker.add(swatch, BorderLayout.NORTH);
        item.add(marker, BorderLayout.WEST);
        item.add(wrappedText(text(key), StudioTheme.smallFont(), MUTED, SURFACE), BorderLayout.CENTER);
        return item;
    }

    private JPanel metricRow(TextKey title, JLabel value) {
        JPanel row = panel(new BorderLayout(StudioTheme.scale(8), 0), SURFACE);
        row.add(wrappedText(text(title), StudioTheme.smallFont(), MUTED, SURFACE), BorderLayout.CENTER);
        row.add(value, BorderLayout.EAST);
        return row;
    }

    private void installControls() {
        pauseButton.setFocusable(true);
        pauseButton.putClientProperty("html.disable", Boolean.TRUE);
        pauseButton.getInputMap(JComponent.WHEN_FOCUSED).put(
                KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), "activatePause");
        pauseButton.getActionMap().put("activatePause", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent event) {
                pauseButton.doClick();
            }
        });
        bind(KeyStroke.getKeyStroke(KeyEvent.VK_P, 0, true), "pause", this::togglePause);
        bind(KeyStroke.getKeyStroke(KeyEvent.VK_R, 0), "reset-view", map::resetView);
        bind(KeyStroke.getKeyStroke(KeyEvent.VK_BACK_SLASH, 0), "reset-zoom", map::resetView);
        bind(KeyStroke.getKeyStroke(KeyEvent.VK_EQUALS, 0), "zoom-in", map::zoomIn);
        bind(KeyStroke.getKeyStroke(KeyEvent.VK_EQUALS, KeyEvent.SHIFT_DOWN_MASK), "zoom-in-plus", map::zoomIn);
        bind(KeyStroke.getKeyStroke(KeyEvent.VK_ADD, 0), "zoom-in-keypad", map::zoomIn);
        bind(KeyStroke.getKeyStroke(KeyEvent.VK_MINUS, 0), "zoom-out", map::zoomOut);
        bind(KeyStroke.getKeyStroke(KeyEvent.VK_SUBTRACT, 0), "zoom-out-keypad", map::zoomOut);
        bind(KeyStroke.getKeyStroke(KeyEvent.VK_G, 0), "grid", this::toggleGrid);
        bind(KeyStroke.getKeyStroke(KeyEvent.VK_LEFT, 0), "pan-left", () -> map.panBy(64, 0));
        bind(KeyStroke.getKeyStroke(KeyEvent.VK_RIGHT, 0), "pan-right", () -> map.panBy(-64, 0));
        bind(KeyStroke.getKeyStroke(KeyEvent.VK_UP, 0), "pan-up", () -> map.panBy(0, 64));
        bind(KeyStroke.getKeyStroke(KeyEvent.VK_DOWN, 0), "pan-down", () -> map.panBy(0, -64));
        bind(KeyStroke.getKeyStroke(KeyEvent.VK_SLASH, 0), "help", map::toggleHelp);
        bind(KeyStroke.getKeyStroke(KeyEvent.VK_SLASH, KeyEvent.SHIFT_DOWN_MASK), "help-question", map::toggleHelp);
        bind(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "dismiss", map::dismissHelp);
    }

    private void bind(KeyStroke key, String name, Runnable action) {
        getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(key, name);
        getActionMap().put(name, new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent event) {
                if (!(KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner() instanceof JTextComponent)) {
                    action.run();
                }
            }
        });
    }

    private void toggleGrid() {
        map.toggleGrid();
        gridToggle.setSelected(map.isGridVisible());
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
        JTextArea area = new JTextArea();
        DefaultCaret caret = (DefaultCaret) area.getCaret();
        caret.setUpdatePolicy(DefaultCaret.NEVER_UPDATE);
        area.setText(text);
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
