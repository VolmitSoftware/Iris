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

package art.arcane.iris.core.gui;

import art.arcane.iris.core.IrisSettings;
import art.arcane.iris.core.localization.DesktopUiMessages;
import art.arcane.iris.core.localization.IrisLanguage;
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
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.plaf.basic.BasicButtonUI;
import javax.swing.plaf.basic.BasicGraphicsUtils;
import javax.swing.plaf.basic.BasicProgressBarUI;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
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
    private final JLabel percentLabel = label("", BODY_FONT, MUTED);
    private final JLabel currentValue = label("", VALUE_FONT, TEXT);
    private final JLabel overallValue = label("", VALUE_FONT, TEXT);
    private final JLabel thirtyValue = label("", VALUE_FONT, TEXT);
    private final JLabel sixtyValue = label("", VALUE_FONT, TEXT);
    private final JLabel etaValue = label("", VALUE_FONT, TEXT);
    private final JLabel elapsedValue = label("", VALUE_FONT, TEXT);
    private final JLabel memoryValue = label("", VALUE_FONT, TEXT);
    private final JLabel pressureValue = label("", VALUE_FONT, TEXT);
    private final JLabel methodLabel = label("", LABEL_FONT, MUTED);
    private final JLabel failureLabel = label("", LABEL_FONT, ERROR);
    private final JButton pauseButton = new JButton();
    private final JProgressBar progressBar = new JProgressBar(0, 10_000);
    private volatile boolean renderingEnabled;
    private volatile boolean disposed;
    private volatile JFrame frame;
    private PregenRenderSnapshot displayedSnapshot;

    private PregenRenderer(PregenRenderSource source, Runnable onPause) {
        this.source = Objects.requireNonNull(source, "Generation view source");
        this.onPause = onPause;
        PregenRenderSnapshot initial = source.renderSnapshot();
        map = new PregenMapPanel(initial.bounds());
        setLayout(new BorderLayout(0, 18));
        setBackground(BACKGROUND);
        setBorder(BorderFactory.createEmptyBorder(20, 24, 18, 24));
        add(header(), BorderLayout.NORTH);
        add(mapArea(initial.bounds()), BorderLayout.CENTER);
        add(statusArea(), BorderLayout.SOUTH);
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
        frame.setSize(1040, 900);
        frame.setMinimumSize(new Dimension(760, 660));
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

    public void submit(int x, int z, Color color) {
        map.submit(x, z, color);
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
        area.add(coordinates, BorderLayout.SOUTH);
        return area;
    }

    private JPanel statusArea() {
        JPanel metrics = panel(new GridLayout(2, 4, 24, 20), SURFACE);
        metrics.add(metric(DesktopUiMessages.PREGEN_CURRENT, currentValue));
        metrics.add(metric(DesktopUiMessages.PREGEN_OVERALL, overallValue));
        metrics.add(metric(DesktopUiMessages.PREGEN_THIRTY, thirtyValue));
        metrics.add(metric(DesktopUiMessages.PREGEN_SIXTY, sixtyValue));
        metrics.add(metric(DesktopUiMessages.PREGEN_ETA, etaValue));
        metrics.add(metric(DesktopUiMessages.PREGEN_ELAPSED, elapsedValue));
        metrics.add(metric(DesktopUiMessages.PREGEN_MEMORY_LABEL, memoryValue));
        metrics.add(metric(DesktopUiMessages.PREGEN_PRESSURE, pressureValue));
        JPanel method = panel(new BorderLayout(16, 0), SURFACE);
        method.add(methodLabel, BorderLayout.CENTER);
        method.add(failureLabel, BorderLayout.EAST);
        JPanel status = panel(new BorderLayout(0, 18), SURFACE);
        status.setBorder(BorderFactory.createEmptyBorder(18, 18, 16, 18));
        status.add(metrics, BorderLayout.CENTER);
        status.add(method, BorderLayout.SOUTH);
        JPanel footer = panel(new BorderLayout(0, 12), BACKGROUND);
        footer.add(legend(), BorderLayout.NORTH);
        footer.add(status, BorderLayout.CENTER);
        return footer;
    }

    private JPanel legend() {
        JPanel legend = panel(new FlowLayout(FlowLayout.LEFT, 0, 0), BACKGROUND);
        legend.add(legendItem(DesktopUiMessages.PREGEN_WAITING, PregenMapPanel.WAITING));
        legend.add(legendItem(DesktopUiMessages.PREGEN_GENERATING, GENERATING));
        legend.add(legendItem(DesktopUiMessages.PREGEN_READY, GENERATED));
        legend.add(legendItem(DesktopUiMessages.PREGEN_EXISTING, EXISTS));
        legend.add(legendItem(DesktopUiMessages.PREGEN_NETWORK, NETWORK));
        legend.setToolTipText(text(DesktopUiMessages.PREGEN_TERRAIN_HINT));
        return legend;
    }

    private JPanel legendItem(TextKey key, Color color) {
        JPanel item = panel(new FlowLayout(FlowLayout.LEFT, 6, 0), BACKGROUND);
        JPanel swatch = new JPanel();
        swatch.setBackground(color);
        swatch.setPreferredSize(new Dimension(10, 10));
        swatch.setBorder(BorderFactory.createLineBorder(BORDER));
        item.add(swatch);
        item.add(label(text(key), LABEL_FONT, MUTED));
        item.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 13));
        return item;
    }

    private JPanel metric(TextKey title, JLabel value) {
        JPanel metric = panel(new BorderLayout(0, 5), SURFACE);
        metric.add(label(text(title), LABEL_FONT, MUTED), BorderLayout.NORTH);
        metric.add(value, BorderLayout.CENTER);
        return metric;
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
            refresh();
        } catch (RuntimeException failure) {
            IrisLogging.reportError("Unable to change pregeneration pause state.", failure);
            failureLabel.setText(text(DesktopUiMessages.PREGEN_CONTROL_FAILED));
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
        etaValue.setText(initializing || paused || progress.eta() < 0 ? pending() : Form.duration(progress.eta(), 2));
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
        failureLabel.setText(progress.failed() > 0 ? text(DesktopUiMessages.PREGEN_FAILED,
                MessageArgument.trusted("count", Form.f(progress.failed()))) : "");
        failureLabel.setToolTipText(snapshot.failure());
        if (snapshot.failure() != null) {
            failureLabel.setText(text(DesktopUiMessages.PREGEN_ERROR_DETAILS));
        }
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

    private static String pending() {
        return text(DesktopUiMessages.PREGEN_METHOD_PENDING);
    }

    private static String rate(double rate) {
        return text(DesktopUiMessages.PREGEN_RATE, MessageArgument.trusted("rate", Form.f(rate, 1)));
    }

    private static String text(TextKey key, MessageArgument... arguments) {
        return IrisLanguage.plain(key, arguments);
    }
}
