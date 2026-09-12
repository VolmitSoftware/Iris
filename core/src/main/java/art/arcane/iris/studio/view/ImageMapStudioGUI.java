package art.arcane.iris.studio.view;

import art.arcane.iris.configuration.IrisSettings;
import art.arcane.iris.pack.loading.IrisData;
import art.arcane.iris.localization.DesktopUiMessages;
import art.arcane.iris.localization.IrisLanguage;
import art.arcane.iris.studio.workspace.ImageMapStudioExporter;
import art.arcane.iris.generation.runtime.Engine;
import art.arcane.iris.generation.terrain.IrisDimension;
import art.arcane.iris.generation.image.IrisImageMap;
import art.arcane.iris.generation.image.IrisImageMapAlpha;
import art.arcane.iris.generation.image.IrisImageMapApplication;
import art.arcane.iris.generation.image.IrisImageMapBinding;
import art.arcane.iris.generation.image.IrisImageMapMask;
import art.arcane.iris.generation.image.IrisImageMapMaskOperation;
import art.arcane.iris.generation.image.IrisImageMapOrigin;
import art.arcane.iris.generation.image.IrisImageMapOutOfBounds;
import art.arcane.iris.generation.image.IrisImageMapRotation;
import art.arcane.iris.generation.image.IrisImageMapSampling;
import art.arcane.iris.generation.image.IrisImageMapType;
import art.arcane.iris.generation.image.IrisImageMapUnknownColor;
import art.arcane.iris.world.IrisWorldBoundary;
import art.arcane.iris.spi.IrisLogging;
import art.arcane.volmlib.util.collection.KMap;
import art.arcane.volmlib.util.localization.TextKey;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.DefaultComboBoxModel;
import javax.swing.DefaultCellEditor;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.SwingWorker;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.table.DefaultTableModel;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.EventQueue;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.function.DoubleBinaryOperator;

public final class ImageMapStudioGUI {
    private static final Color BACKGROUND = new Color(12, 15, 22);
    private static final Color SIDEBAR_BACKGROUND = new Color(17, 20, 29);
    private static final Color PANEL_BACKGROUND = new Color(23, 27, 38);
    private static final Color FIELD_BACKGROUND = new Color(29, 34, 47);
    private static final Color PRIMARY_TEXT = new Color(230, 234, 242);
    private static final Color SECONDARY_TEXT = new Color(151, 160, 178);
    private static final Color ACCENT = new Color(99, 161, 255);
    private static final Color BORDER = new Color(48, 55, 72);
    private static final Color ERROR = new Color(255, 116, 116);
    private static final Color SUCCESS = new Color(91, 204, 139);
    private static final Font BODY_FONT = new Font(Font.SANS_SERIF, Font.PLAIN, 12);
    private static final Font LABEL_FONT = new Font(Font.SANS_SERIF, Font.BOLD, 11);

    private final Engine engine;
    private final File packFolder;
    private final String dimensionKey;
    private final JFrame frame;
    private final ImageMapStudioPreviewPanel previewPanel = new ImageMapStudioPreviewPanel();
    private final JComboBox<String> presetPicker = new JComboBox<>();
    private final JTextField bindingKeyField = field("image-map");
    private final JTextField mapKeyField = field("image-map");
    private final JTextField imageKeyField = field("image-map");
    private final JComboBox<IrisImageMapType> typePicker = combo(IrisImageMapType.values());
    private final JComboBox<IrisImageMapApplication> applicationPicker = combo(IrisImageMapApplication.values());
    private final JTextField blocksPerPixelField = field("1");
    private final JTextField originXField = field("0");
    private final JTextField originZField = field("0");
    private final JTextField sourceOriginXField = field("0");
    private final JTextField sourceOriginZField = field("0");
    private final JComboBox<IrisImageMapRotation> rotationPicker = combo(IrisImageMapRotation.values());
    private final JCheckBox mirrorXCheck = check();
    private final JCheckBox mirrorZCheck = check();
    private final JComboBox<IrisImageMapSampling> samplingPicker = combo(IrisImageMapSampling.values());
    private final JComboBox<IrisImageMapOutOfBounds> outOfBoundsPicker = combo(IrisImageMapOutOfBounds.values());
    private final JTextField fallbackValueField = field("0");
    private final JTextField fallbackTargetField = field("");
    private final JComboBox<IrisImageMapAlpha> alphaPicker = combo(IrisImageMapAlpha.values());
    private final JTextField minimumHeightField = field("-64");
    private final JTextField maximumHeightField = field("320");
    private final JTextField verticalOffsetField = field("0");
    private final JCheckBox clampCheck = check();
    private final JCheckBox heightInvertedCheck = check();
    private final JTextField heightCurveExponentField = field("1");
    private final JTextField heightSmoothingRadiusField = field("0");
    private final JCheckBox maskInvertedCheck = check();
    private final JTextField maskCurveExponentField = field("1");
    private final JTextField maskSmoothingRadiusField = field("0");
    private final JTextField thresholdField = field("0.5");
    private final JTextField falloffField = field("0");
    private final JTextField toleranceField = field("0");
    private final JComboBox<IrisImageMapUnknownColor> unknownColorPicker = combo(IrisImageMapUnknownColor.values());
    private final DefaultTableModel legendModel = new DefaultTableModel(new Object[]{"#RRGGBB", "Target"}, 0);
    private final JTable legendTable = new JTable(legendModel);
    private final DefaultTableModel composedMaskModel = new DefaultTableModel(
            new Object[]{"Binding", "Operation", "Invert", "Threshold", "Falloff"}, 0
    ) {
        @Override
        public Class<?> getColumnClass(int columnIndex) {
            return columnIndex == 2 ? Boolean.class : Object.class;
        }
    };
    private final JTable composedMaskTable = new JTable(composedMaskModel);
    private final JPanel typeCards = new JPanel(new CardLayout());
    private final JTextArea metadataArea = area(3);
    private final JTextArea diagnosticsArea = area(5);
    private final JLabel statusLabel = new JLabel();
    private final JCheckBox chunksOverlay = selectedCheck();
    private final JCheckBox regionsOverlay = selectedCheck();
    private final JCheckBox boundaryOverlay = selectedCheck();
    private final JCheckBox coverageOverlay = selectedCheck();
    private Path sourcePath;
    private IrisWorldBoundary boundary;
    private boolean controlsUpdating;

    private ImageMapStudioGUI(Engine engine) {
        this.engine = Objects.requireNonNull(engine, "Image Map Studio engine");
        this.packFolder = engine.getPackSource().toFile();
        this.dimensionKey = engine.getDimension().getLoadKey();
        this.boundary = engine.getDimension().getWorldBoundary() == null
                ? null
                : IrisWorldBoundary.snapshot(engine.getDimension().getWorldBoundary());
        this.frame = new JFrame(text(DesktopUiMessages.IMAGEMAP_TITLE));
        configureFrame();
        configureControls();
        refreshPresets();
    }

    public static boolean isAvailable() {
        return GuiHost.isAvailable() && IrisSettings.get().getGui().isUseServerLaunchedGuis();
    }

    public static void launch(Engine engine) {
        EventQueue.invokeLater(() -> new ImageMapStudioGUI(engine).show());
    }

    static void reloadActiveEngine(Engine engine) {
        engine.hotloadSilently();
    }

    private void configureFrame() {
        GuiHost.prepareFrame(frame);
        frame.getContentPane().setBackground(BACKGROUND);
        frame.setLayout(new BorderLayout());
        frame.add(buildToolbar(), BorderLayout.NORTH);
        frame.add(buildWorkspace(), BorderLayout.CENTER);
        frame.add(buildStatusBar(), BorderLayout.SOUTH);
        frame.setSize(1540, 920);
        frame.setMinimumSize(new Dimension(1040, 680));
        frame.setLocationRelativeTo(null);
        frame.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosed(WindowEvent event) {
                previewPanel.close();
            }

            @Override
            public void windowClosing(WindowEvent event) {
                previewPanel.close();
            }
        });
    }

    private void configureControls() {
        clampCheck.setSelected(true);
        typePicker.setSelectedItem(IrisImageMapType.GRAYSCALE_HEIGHT);
        applicationPicker.setSelectedItem(IrisImageMapApplication.TERRAIN_HEIGHT);
        samplingPicker.setSelectedItem(IrisImageMapSampling.NEAREST);
        outOfBoundsPicker.setSelectedItem(IrisImageMapOutOfBounds.FALLBACK);
        alphaPicker.setSelectedItem(IrisImageMapAlpha.IGNORE);
        unknownColorPicker.setSelectedItem(IrisImageMapUnknownColor.ERROR);
        typePicker.addActionListener(event -> updateTypeControls());
        for (JCheckBox overlay : List.of(chunksOverlay, regionsOverlay, boundaryOverlay, coverageOverlay)) {
            overlay.addActionListener(event -> updateOverlays());
        }
        previewPanel.setDiagnosticConsumer(this::appendDiagnostic);
        configureComposedMaskTable();
        updateTypeControls();
        updateOverlays();
    }

    private void show() {
        frame.setVisible(true);
        EventQueue.invokeLater(() -> {
            String selected = (String) presetPicker.getSelectedItem();
            if (selected != null && !selected.isBlank()) {
                loadPreset(selected);
            }
        });
    }

    private JPanel buildToolbar() {
        JPanel toolbar = new JPanel(new BorderLayout());
        toolbar.setBackground(SIDEBAR_BACKGROUND);
        toolbar.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, BORDER));
        JPanel leading = new JPanel(new FlowLayout(FlowLayout.LEFT, 7, 6));
        leading.setOpaque(false);
        JLabel presetLabel = label(DesktopUiMessages.IMAGEMAP_PRESET);
        leading.add(presetLabel);
        presetPicker.setPrototypeDisplayValue("terrain/example-long-preset");
        styleCombo(presetPicker);
        leading.add(presetPicker);
        JButton load = button(DesktopUiMessages.IMAGEMAP_LOAD);
        load.addActionListener(event -> {
            String selected = (String) presetPicker.getSelectedItem();
            if (selected != null) {
                loadPreset(selected);
            }
        });
        leading.add(load);
        JButton importButton = button(DesktopUiMessages.IMAGEMAP_IMPORT_PNG);
        importButton.addActionListener(event -> chooseSource(false));
        leading.add(importButton);
        JButton replaceButton = button(DesktopUiMessages.IMAGEMAP_REPLACE_PNG);
        replaceButton.addActionListener(event -> chooseSource(true));
        leading.add(replaceButton);
        toolbar.add(leading, BorderLayout.WEST);

        JPanel trailing = new JPanel(new FlowLayout(FlowLayout.RIGHT, 7, 6));
        trailing.setOpaque(false);
        JButton preview = button(DesktopUiMessages.IMAGEMAP_PREVIEW);
        preview.addActionListener(event -> preview());
        trailing.add(preview);
        JButton export = button(DesktopUiMessages.IMAGEMAP_EXPORT);
        export.setForeground(SUCCESS);
        export.addActionListener(event -> export());
        trailing.add(export);
        toolbar.add(trailing, BorderLayout.EAST);
        return toolbar;
    }

    private Component buildWorkspace() {
        JScrollPane editorScroll = new JScrollPane(buildEditor());
        editorScroll.setBorder(BorderFactory.createEmptyBorder());
        editorScroll.getVerticalScrollBar().setUnitIncrement(18);
        editorScroll.setPreferredSize(new Dimension(470, 760));
        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, editorScroll, previewPanel);
        split.setBorder(BorderFactory.createEmptyBorder());
        split.setDividerSize(5);
        split.setResizeWeight(0D);
        split.setDividerLocation(470);
        return split;
    }

    private JPanel buildEditor() {
        JPanel editor = new JPanel();
        editor.setLayout(new BoxLayout(editor, BoxLayout.Y_AXIS));
        editor.setBackground(SIDEBAR_BACKGROUND);
        editor.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        editor.add(section(DesktopUiMessages.IMAGEMAP_METADATA, metadataPanel()));
        editor.add(Box.createVerticalStrut(9));
        editor.add(section(DesktopUiMessages.IMAGEMAP_RESOURCE, resourcePanel()));
        editor.add(Box.createVerticalStrut(9));
        editor.add(section(DesktopUiMessages.IMAGEMAP_COORDINATES, coordinatePanel()));
        editor.add(Box.createVerticalStrut(9));
        typeCards.add(heightPanel(), "HEIGHT");
        typeCards.add(colorPanel(), "COLOR");
        typeCards.add(maskPanel(), "MASK");
        typeCards.setBackground(SIDEBAR_BACKGROUND);
        editor.add(typeCards);
        editor.add(Box.createVerticalStrut(9));
        editor.add(section(DesktopUiMessages.IMAGEMAP_COMPOSED_MASKS, composedMaskPanel()));
        editor.add(Box.createVerticalStrut(9));
        editor.add(section(DesktopUiMessages.IMAGEMAP_OVERLAYS, overlayPanel()));
        editor.add(Box.createVerticalStrut(9));
        editor.add(section(DesktopUiMessages.IMAGEMAP_DIAGNOSTICS, diagnosticsPanel()));
        return editor;
    }

    private JPanel metadataPanel() {
        metadataArea.setText(text(DesktopUiMessages.IMAGEMAP_NO_SOURCE));
        return single(metadataArea);
    }

    private JPanel resourcePanel() {
        JPanel panel = form();
        addRow(panel, 0, DesktopUiMessages.IMAGEMAP_BINDING_KEY, bindingKeyField);
        addRow(panel, 1, DesktopUiMessages.IMAGEMAP_MAP_KEY, mapKeyField);
        addRow(panel, 2, DesktopUiMessages.IMAGEMAP_IMAGE_KEY, imageKeyField);
        addRow(panel, 3, DesktopUiMessages.IMAGEMAP_TYPE, typePicker);
        addRow(panel, 4, DesktopUiMessages.IMAGEMAP_APPLICATION, applicationPicker);
        return panel;
    }

    private JPanel coordinatePanel() {
        JPanel panel = form();
        addRow(panel, 0, DesktopUiMessages.IMAGEMAP_BLOCKS_PER_PIXEL, blocksPerPixelField);
        addRow(panel, 1, DesktopUiMessages.IMAGEMAP_ORIGIN_X, originXField);
        addRow(panel, 2, DesktopUiMessages.IMAGEMAP_ORIGIN_Z, originZField);
        addRow(panel, 3, DesktopUiMessages.IMAGEMAP_SOURCE_ORIGIN_X, sourceOriginXField);
        addRow(panel, 4, DesktopUiMessages.IMAGEMAP_SOURCE_ORIGIN_Z, sourceOriginZField);
        addRow(panel, 5, DesktopUiMessages.IMAGEMAP_ROTATION, rotationPicker);
        addRow(panel, 6, DesktopUiMessages.IMAGEMAP_MIRROR_X, mirrorXCheck);
        addRow(panel, 7, DesktopUiMessages.IMAGEMAP_MIRROR_Z, mirrorZCheck);
        addRow(panel, 8, DesktopUiMessages.IMAGEMAP_SAMPLING, samplingPicker);
        addRow(panel, 9, DesktopUiMessages.IMAGEMAP_OUT_OF_BOUNDS, outOfBoundsPicker);
        addRow(panel, 10, DesktopUiMessages.IMAGEMAP_ALPHA, alphaPicker);
        addRow(panel, 11, DesktopUiMessages.IMAGEMAP_FALLBACK_VALUE, fallbackValueField);
        addRow(panel, 12, DesktopUiMessages.IMAGEMAP_FALLBACK_TARGET, fallbackTargetField);
        return panel;
    }

    private JPanel heightPanel() {
        JPanel panel = form();
        addRow(panel, 0, DesktopUiMessages.IMAGEMAP_MINIMUM_HEIGHT, minimumHeightField);
        addRow(panel, 1, DesktopUiMessages.IMAGEMAP_MAXIMUM_HEIGHT, maximumHeightField);
        addRow(panel, 2, DesktopUiMessages.IMAGEMAP_VERTICAL_OFFSET, verticalOffsetField);
        addRow(panel, 3, DesktopUiMessages.IMAGEMAP_CLAMP, clampCheck);
        addRow(panel, 4, DesktopUiMessages.IMAGEMAP_INVERTED, heightInvertedCheck);
        addRow(panel, 5, DesktopUiMessages.IMAGEMAP_CURVE_EXPONENT, heightCurveExponentField);
        addRow(panel, 6, DesktopUiMessages.IMAGEMAP_SMOOTHING_RADIUS, heightSmoothingRadiusField);
        return section(DesktopUiMessages.IMAGEMAP_HEIGHT, panel);
    }

    private JPanel maskPanel() {
        JPanel panel = form();
        addRow(panel, 0, DesktopUiMessages.IMAGEMAP_THRESHOLD, thresholdField);
        addRow(panel, 1, DesktopUiMessages.IMAGEMAP_FALLOFF, falloffField);
        addRow(panel, 2, DesktopUiMessages.IMAGEMAP_INVERTED, maskInvertedCheck);
        addRow(panel, 3, DesktopUiMessages.IMAGEMAP_CURVE_EXPONENT, maskCurveExponentField);
        addRow(panel, 4, DesktopUiMessages.IMAGEMAP_SMOOTHING_RADIUS, maskSmoothingRadiusField);
        return section(DesktopUiMessages.IMAGEMAP_MASK, panel);
    }

    private JPanel colorPanel() {
        JPanel panel = new JPanel(new BorderLayout(0, 7));
        panel.setOpaque(false);
        JPanel settings = form();
        addRow(settings, 0, DesktopUiMessages.IMAGEMAP_COLOR_TOLERANCE, toleranceField);
        addRow(settings, 1, DesktopUiMessages.IMAGEMAP_UNKNOWN_COLOR, unknownColorPicker);
        panel.add(settings, BorderLayout.NORTH);
        configureLegendTable();
        JScrollPane scroll = new JScrollPane(legendTable);
        scroll.setPreferredSize(new Dimension(390, 160));
        scroll.setBorder(BorderFactory.createLineBorder(BORDER));
        panel.add(scroll, BorderLayout.CENTER);
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        actions.setOpaque(false);
        JButton add = button(DesktopUiMessages.IMAGEMAP_ADD_COLOR);
        add.addActionListener(event -> legendModel.addRow(new Object[]{"#000000", "iris:"}));
        actions.add(add);
        JButton remove = button(DesktopUiMessages.IMAGEMAP_REMOVE_COLOR);
        remove.addActionListener(event -> removeLegendRows());
        actions.add(remove);
        panel.add(actions, BorderLayout.SOUTH);
        return section(DesktopUiMessages.IMAGEMAP_COLOR_MAP, panel);
    }

    private JPanel overlayPanel() {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 9, 2));
        panel.setOpaque(false);
        chunksOverlay.setText(text(DesktopUiMessages.IMAGEMAP_CHUNKS));
        regionsOverlay.setText(text(DesktopUiMessages.IMAGEMAP_REGIONS));
        boundaryOverlay.setText(text(DesktopUiMessages.IMAGEMAP_BOUNDARY));
        coverageOverlay.setText(text(DesktopUiMessages.IMAGEMAP_COVERAGE));
        panel.add(chunksOverlay);
        panel.add(regionsOverlay);
        panel.add(boundaryOverlay);
        panel.add(coverageOverlay);
        return panel;
    }

    private JPanel composedMaskPanel() {
        JPanel panel = new JPanel(new BorderLayout(0, 7));
        panel.setOpaque(false);
        JScrollPane scroll = new JScrollPane(composedMaskTable);
        scroll.setPreferredSize(new Dimension(390, 120));
        scroll.setBorder(BorderFactory.createLineBorder(BORDER));
        panel.add(scroll, BorderLayout.CENTER);
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        actions.setOpaque(false);
        JButton add = button(DesktopUiMessages.IMAGEMAP_ADD_MASK);
        add.addActionListener(event -> composedMaskModel.addRow(new Object[]{
                "", IrisImageMapMaskOperation.MULTIPLY, false, "0", "0"
        }));
        actions.add(add);
        JButton remove = button(DesktopUiMessages.IMAGEMAP_REMOVE_COLOR);
        remove.addActionListener(event -> removeRows(composedMaskTable, composedMaskModel));
        actions.add(remove);
        panel.add(actions, BorderLayout.SOUTH);
        return panel;
    }

    private JPanel diagnosticsPanel() {
        diagnosticsArea.setText(text(DesktopUiMessages.IMAGEMAP_READY));
        return single(diagnosticsArea);
    }

    private JPanel buildStatusBar() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(PANEL_BACKGROUND);
        panel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, BORDER),
                BorderFactory.createEmptyBorder(5, 9, 5, 9)
        ));
        statusLabel.setForeground(SECONDARY_TEXT);
        statusLabel.setFont(BODY_FONT);
        statusLabel.setText(packFolder.getName() + "  |  " + dimensionKey + "  |  "
                + text(DesktopUiMessages.IMAGEMAP_READY));
        panel.add(statusLabel, BorderLayout.WEST);
        return panel;
    }

    private void refreshPresets() {
        IrisData data = IrisData.openRuntime(packFolder);
        try {
            String[] keys = data.getImageMapLoader().getPossibleKeys();
            Arrays.sort(keys);
            presetPicker.setModel(new DefaultComboBoxModel<>(keys));
        } finally {
            data.close();
        }
    }

    private void loadPreset(String key) {
        setStatus(DesktopUiMessages.IMAGEMAP_LOADING);
        new SwingWorker<PresetState, Void>() {
            @Override
            protected PresetState doInBackground() throws Exception {
                IrisData data = IrisData.openRuntime(packFolder);
                try {
                    IrisImageMap loaded = data.getImageMapLoader().load(key);
                    if (loaded == null) {
                        throw new IllegalArgumentException("Image-map preset '" + key + "' could not be loaded");
                    }
                    IrisImageMap definition = data.getGson().fromJson(data.getGson().toJson(loaded), IrisImageMap.class);
                    IrisDimension dimension = data.getDimensionLoader().load(dimensionKey);
                    String bindingKey = key;
                    IrisImageMapApplication application = suggestedApplication(definition.getType());
                    List<IrisImageMapMask> masks = List.of();
                    if (dimension != null) {
                        for (IrisImageMapBinding binding : dimension.getImageMaps()) {
                            if (binding != null && key.equals(binding.getMap())) {
                                bindingKey = binding.getKey();
                                application = binding.getApplication();
                                masks = binding.getMasks() == null
                                        ? List.of()
                                        : List.copyOf(binding.getMasks());
                                break;
                            }
                        }
                    }
                    File image = data.getImageLoader().findFile(definition.getSource());
                    if (image == null || !image.isFile()) {
                        throw new IllegalArgumentException("Preset source PNG is missing: " + definition.getSource());
                    }
                    IrisWorldBoundary loadedBoundary = dimension == null || dimension.getWorldBoundary() == null
                            ? null
                            : IrisWorldBoundary.snapshot(dimension.getWorldBoundary());
                    return new PresetState(
                            definition, image.toPath(), key, bindingKey, application, masks, loadedBoundary
                    );
                } finally {
                    data.close();
                }
            }

            @Override
            protected void done() {
                try {
                    PresetState state = get();
                    boundary = state.boundary();
                    sourcePath = state.source();
                    controlsUpdating = true;
                    applyDefinition(state.definition());
                    mapKeyField.setText(state.mapKey());
                    bindingKeyField.setText(state.bindingKey());
                    applicationPicker.setSelectedItem(state.application());
                    applyMasks(state.masks());
                    controlsUpdating = false;
                    preview();
                } catch (Exception exception) {
                    controlsUpdating = false;
                    showFailure(text(DesktopUiMessages.IMAGEMAP_LOAD_FAILED), exception);
                }
            }
        }.execute();
    }

    private void chooseSource(boolean replacement) {
        JFileChooser chooser = new JFileChooser(sourcePath == null ? packFolder : sourcePath.toFile().getParentFile());
        chooser.setDialogTitle(text(replacement
                ? DesktopUiMessages.IMAGEMAP_REPLACE_PNG
                : DesktopUiMessages.IMAGEMAP_IMPORT_PNG));
        chooser.setFileFilter(new FileNameExtensionFilter("PNG", "png"));
        if (chooser.showOpenDialog(frame) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        sourcePath = chooser.getSelectedFile().toPath().toAbsolutePath().normalize();
        if (!replacement) {
            String key = ImageMapStudioModel.safeKey(chooser.getSelectedFile().getName());
            bindingKeyField.setText(key);
            mapKeyField.setText(key);
            imageKeyField.setText(key);
        }
        inspectSourceAndPreview();
    }

    private void inspectSourceAndPreview() {
        Path inspectedPath = sourcePath;
        setStatus(DesktopUiMessages.IMAGEMAP_PREVIEWING);
        new SwingWorker<ImageMapStudioExporter.SourceInspection, Void>() {
            @Override
            protected ImageMapStudioExporter.SourceInspection doInBackground() throws Exception {
                return ImageMapStudioExporter.inspectSource(inspectedPath);
            }

            @Override
            protected void done() {
                try {
                    ImageMapStudioExporter.SourceInspection inspection = get();
                    ImageMapStudioModel.SourceMetadata metadata = ImageMapStudioModel.inspect(
                            inspectedPath,
                            inspection.source(),
                            inspection.format(),
                            inspection.colorProfile(),
                            inspection.minimumAlpha(),
                            inspection.maximumAlpha()
                    );
                    metadataArea.setText(metadata.path() + "\n" + metadata.summary());
                    previewPanel.setSource(inspection.source());
                    preview();
                } catch (Exception exception) {
                    showFailure(text(DesktopUiMessages.IMAGEMAP_PREVIEW_FAILED), exception);
                }
            }
        }.execute();
    }

    private void preview() {
        IrisImageMap definition;
        List<IrisImageMapMask> currentMasks;
        try {
            definition = readDefinition();
            requireSource();
            currentMasks = readMasks();
            validateMaskApplication(currentMasks);
        } catch (RuntimeException exception) {
            showFailure(text(DesktopUiMessages.IMAGEMAP_PREVIEW_FAILED), exception);
            return;
        }
        setStatus(DesktopUiMessages.IMAGEMAP_PREVIEWING);
        diagnosticsArea.setText("");
        Path currentSource = sourcePath;
        IrisImageMapApplication currentApplication = selected(applicationPicker, "application");
        DoubleBinaryOperator proceduralHeightSampler = null;
        if (currentApplication == IrisImageMapApplication.TERRAIN_HEIGHT
                && !engine.isClosed()
                && engine.getComplex() != null) {
            proceduralHeightSampler = (worldX, worldZ) -> engine.getComplex()
                    .sampleProceduralTerrainHeight(engine, worldX, worldZ);
        }
        DoubleBinaryOperator currentProceduralHeightSampler = proceduralHeightSampler;
        new SwingWorker<ImageMapStudioExporter.PreviewResult, Void>() {
            @Override
            protected ImageMapStudioExporter.PreviewResult doInBackground() throws Exception {
                return ImageMapStudioExporter.preview(
                        currentSource, definition, packFolder, dimensionKey, currentMasks
                );
            }

            @Override
            protected void done() {
                try {
                    ImageMapStudioExporter.PreviewResult result = get();
                    ImageMapStudioModel.SourceMetadata metadata = ImageMapStudioModel.inspect(
                            currentSource,
                            result.source(),
                            result.compiled().getSourceMetadata().format(),
                            result.colorProfile(),
                            result.compiled().getSourceMetadata().minimumAlpha(),
                            result.compiled().getSourceMetadata().maximumAlpha()
                    );
                    metadataArea.setText(metadata.path() + "\n" + metadata.summary());
                    previewPanel.setPreview(
                            result.source(), result.compiled(), result.maskSampler(), boundary,
                            currentApplication, engine.getMinHeight(), currentProceduralHeightSampler
                    );
                    List<String> warnings = ImageMapStudioModel.warnings(
                            definition, metadata.width(), metadata.height(), boundary
                    );
                    warnings = new ArrayList<>(warnings);
                    if (result.compiled().getClippedPixelCount() > 0) {
                        warnings.add(result.compiled().getClippedPixelCount()
                                + " source height pixel(s) are clipped to the configured vertical range.");
                    }
                    if (result.compiled().getUnknownColorPixelCount() > 0) {
                        warnings.add(result.compiled().getUnknownColorPixelCount()
                                + " source color pixel(s) are absent from the legend and use the unknown-color policy.");
                    }
                    diagnosticsArea.setText(warnings.isEmpty()
                            ? text(DesktopUiMessages.IMAGEMAP_PREVIEW_VALID)
                            : String.join("\n", warnings));
                    diagnosticsArea.setForeground(warnings.isEmpty() ? SUCCESS : PRIMARY_TEXT);
                    setStatus(DesktopUiMessages.IMAGEMAP_READY);
                } catch (Exception exception) {
                    showFailure(text(DesktopUiMessages.IMAGEMAP_PREVIEW_FAILED), exception);
                }
            }
        }.execute();
    }

    private void export() {
        IrisImageMap definition;
        List<IrisImageMapMask> masks;
        try {
            definition = readDefinition();
            requireSource();
            masks = readMasks();
            validateMaskApplication(masks);
        } catch (RuntimeException exception) {
            showFailure(text(DesktopUiMessages.IMAGEMAP_EXPORT_FAILED), exception);
            return;
        }
        String bindingKey = bindingKeyField.getText().trim();
        String mapKey = mapKeyField.getText().trim();
        String imageKey = imageKeyField.getText().trim();
        IrisImageMapApplication application = selected(applicationPicker, "application");
        ImageMapStudioExporter.ExportRequest request = new ImageMapStudioExporter.ExportRequest(
                packFolder,
                dimensionKey,
                bindingKey,
                application,
                mapKey,
                imageKey,
                definition,
                sourcePath,
                masks
        );
        setStatus(DesktopUiMessages.IMAGEMAP_EXPORTING);
        new SwingWorker<ImageMapStudioExporter.ExportResult, Void>() {
            @Override
            protected ImageMapStudioExporter.ExportResult doInBackground() throws Exception {
                ImageMapStudioExporter.ExportResult result = ImageMapStudioExporter.export(request);
                reloadActiveEngine(engine);
                return result;
            }

            @Override
            protected void done() {
                try {
                    ImageMapStudioExporter.ExportResult result = get();
                    StringBuilder message = new StringBuilder(text(DesktopUiMessages.IMAGEMAP_EXPORTED));
                    message.append('\n').append(result.imageMapFile());
                    message.append('\n').append(result.imageFile());
                    message.append('\n').append(result.dimensionFile());
                    for (String warning : result.warnings()) {
                        message.append('\n').append(warning);
                    }
                    diagnosticsArea.setForeground(SUCCESS);
                    diagnosticsArea.setText(message.toString());
                    setStatus(DesktopUiMessages.IMAGEMAP_EXPORTED);
                    refreshPresets();
                    presetPicker.setSelectedItem(mapKey);
                } catch (Exception exception) {
                    showFailure(text(DesktopUiMessages.IMAGEMAP_EXPORT_FAILED), exception);
                }
            }
        }.execute();
    }

    private IrisImageMap readDefinition() {
        IrisImageMapType type = selected(typePicker, "type");
        IrisImageMap defaults = new IrisImageMap();
        boolean heightType = type == IrisImageMapType.GRAYSCALE_HEIGHT || type == IrisImageMapType.RGB_HEIGHT;
        boolean maskType = type == IrisImageMapType.BINARY_MASK
                || type == IrisImageMapType.GRAYSCALE_MASK
                || type == IrisImageMapType.ALPHA_MASK;
        boolean colorType = type == IrisImageMapType.COLOR_MAP;
        IrisImageMap definition = new IrisImageMap()
                .setSource(requiredText(imageKeyField, "Image key"))
                .setType(type)
                .setBlocksPerPixel(number(blocksPerPixelField, "Blocks per pixel"))
                .setOrigin(new IrisImageMapOrigin(
                        number(originXField, "Origin X"),
                        number(originZField, "Origin Z")
                ))
                .setSourceOrigin(new IrisImageMapOrigin(
                        number(sourceOriginXField, "Source origin X"),
                        number(sourceOriginZField, "Source origin Z")
                ))
                .setRotation(selected(rotationPicker, "rotation"))
                .setMirrorX(mirrorXCheck.isSelected())
                .setMirrorZ(mirrorZCheck.isSelected())
                .setSampling(selected(samplingPicker, "sampling"))
                .setOutOfBounds(selected(outOfBoundsPicker, "out-of-bounds policy"))
                .setFallbackValue(number(fallbackValueField, "Fallback value"))
                .setFallbackTarget(fallbackTargetField.getText().trim())
                .setAlpha(selected(alphaPicker, "alpha policy"))
                .setMinimumHeight(heightType
                        ? number(minimumHeightField, "Minimum height") : defaults.getMinimumHeight())
                .setMaximumHeight(heightType
                        ? number(maximumHeightField, "Maximum height") : defaults.getMaximumHeight())
                .setVerticalOffset(heightType
                        ? number(verticalOffsetField, "Vertical offset") : defaults.getVerticalOffset())
                .setClamp(heightType ? clampCheck.isSelected() : defaults.isClamp())
                .setInverted(heightType
                        ? heightInvertedCheck.isSelected()
                        : maskType && maskInvertedCheck.isSelected())
                .setCurveExponent(heightType
                        ? number(heightCurveExponentField, "Curve exponent")
                        : maskType
                        ? number(maskCurveExponentField, "Curve exponent")
                        : defaults.getCurveExponent())
                .setSmoothingRadius(heightType
                        ? integer(heightSmoothingRadiusField, "Smoothing radius")
                        : maskType
                        ? integer(maskSmoothingRadiusField, "Smoothing radius")
                        : defaults.getSmoothingRadius())
                .setThreshold(type == IrisImageMapType.BINARY_MASK
                        ? number(thresholdField, "Threshold") : defaults.getThreshold())
                .setFalloff(type == IrisImageMapType.BINARY_MASK
                        ? number(falloffField, "Falloff") : defaults.getFalloff())
                .setColorTolerance(colorType
                        ? number(toleranceField, "Color tolerance") : defaults.getColorTolerance())
                .setUnknownColor(colorType
                        ? selected(unknownColorPicker, "unknown-color policy")
                        : defaults.getUnknownColor());
        if (colorType) {
            List<ImageMapStudioModel.LegendRow> rows = new ArrayList<>();
            for (int row = 0; row < legendModel.getRowCount(); row++) {
                rows.add(new ImageMapStudioModel.LegendRow(
                        String.valueOf(legendModel.getValueAt(row, 0)),
                        String.valueOf(legendModel.getValueAt(row, 1))
                ));
            }
            KMap<String, String> colors = ImageMapStudioModel.legend(rows);
            definition.setColors(colors);
        }
        requiredText(bindingKeyField, "Binding key");
        requiredText(mapKeyField, "Image-map key");
        return ImageMapStudioModel.normalizeTypeSettings(definition);
    }

    private void applyDefinition(IrisImageMap definition) {
        imageKeyField.setText(definition.getSource());
        typePicker.setSelectedItem(definition.getType());
        blocksPerPixelField.setText(Double.toString(definition.getBlocksPerPixel()));
        originXField.setText(Double.toString(definition.getOrigin().getX()));
        originZField.setText(Double.toString(definition.getOrigin().getZ()));
        sourceOriginXField.setText(Double.toString(definition.getSourceOrigin().getX()));
        sourceOriginZField.setText(Double.toString(definition.getSourceOrigin().getZ()));
        rotationPicker.setSelectedItem(definition.getRotation());
        mirrorXCheck.setSelected(definition.isMirrorX());
        mirrorZCheck.setSelected(definition.isMirrorZ());
        samplingPicker.setSelectedItem(definition.getSampling());
        outOfBoundsPicker.setSelectedItem(definition.getOutOfBounds());
        fallbackValueField.setText(Double.toString(definition.getFallbackValue()));
        fallbackTargetField.setText(definition.getFallbackTarget());
        alphaPicker.setSelectedItem(definition.getAlpha());
        minimumHeightField.setText(Double.toString(definition.getMinimumHeight()));
        maximumHeightField.setText(Double.toString(definition.getMaximumHeight()));
        verticalOffsetField.setText(Double.toString(definition.getVerticalOffset()));
        clampCheck.setSelected(definition.isClamp());
        heightInvertedCheck.setSelected(definition.isInverted());
        heightCurveExponentField.setText(Double.toString(definition.getCurveExponent()));
        heightSmoothingRadiusField.setText(Integer.toString(definition.getSmoothingRadius()));
        maskInvertedCheck.setSelected(definition.isInverted());
        maskCurveExponentField.setText(Double.toString(definition.getCurveExponent()));
        maskSmoothingRadiusField.setText(Integer.toString(definition.getSmoothingRadius()));
        thresholdField.setText(Double.toString(definition.getThreshold()));
        falloffField.setText(Double.toString(definition.getFalloff()));
        toleranceField.setText(Double.toString(definition.getColorTolerance()));
        unknownColorPicker.setSelectedItem(definition.getUnknownColor());
        legendModel.setRowCount(0);
        for (ImageMapStudioModel.LegendRow row : ImageMapStudioModel.legendRows(definition)) {
            legendModel.addRow(new Object[]{row.color(), row.target()});
        }
        updateTypeControls();
    }

    private void updateTypeControls() {
        IrisImageMapType type = (IrisImageMapType) typePicker.getSelectedItem();
        if (type == null) {
            return;
        }
        CardLayout layout = (CardLayout) typeCards.getLayout();
        layout.show(typeCards, typeCard(type));
        thresholdField.setEnabled(type == IrisImageMapType.BINARY_MASK);
        falloffField.setEnabled(type == IrisImageMapType.BINARY_MASK);
        if (controlsUpdating) {
            return;
        }
        if (type == IrisImageMapType.COLOR_MAP || type == IrisImageMapType.BINARY_MASK) {
            samplingPicker.setSelectedItem(IrisImageMapSampling.NEAREST);
        }
        if (type == IrisImageMapType.ALPHA_MASK) {
            alphaPicker.setSelectedItem(IrisImageMapAlpha.IGNORE);
        }
        applicationPicker.setSelectedItem(suggestedApplication(type));
    }

    private static IrisImageMapApplication suggestedApplication(IrisImageMapType type) {
        if (type == null) {
            return IrisImageMapApplication.CUSTOM;
        }
        return switch (type) {
            case GRAYSCALE_HEIGHT, RGB_HEIGHT -> IrisImageMapApplication.TERRAIN_HEIGHT;
            case COLOR_MAP -> IrisImageMapApplication.BIOME;
            case BINARY_MASK, GRAYSCALE_MASK, ALPHA_MASK -> IrisImageMapApplication.MASK;
        };
    }

    private static String typeCard(IrisImageMapType type) {
        return switch (type) {
            case GRAYSCALE_HEIGHT, RGB_HEIGHT -> "HEIGHT";
            case COLOR_MAP -> "COLOR";
            case BINARY_MASK, GRAYSCALE_MASK, ALPHA_MASK -> "MASK";
        };
    }

    private void updateOverlays() {
        previewPanel.setOverlays(
                chunksOverlay.isSelected(),
                regionsOverlay.isSelected(),
                boundaryOverlay.isSelected(),
                coverageOverlay.isSelected()
        );
    }

    private void configureLegendTable() {
        legendTable.setBackground(FIELD_BACKGROUND);
        legendTable.setForeground(PRIMARY_TEXT);
        legendTable.setGridColor(BORDER);
        legendTable.setSelectionBackground(new Color(42, 55, 78));
        legendTable.setSelectionForeground(PRIMARY_TEXT);
        legendTable.setFont(BODY_FONT);
        legendTable.setRowHeight(23);
        legendTable.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        legendTable.getTableHeader().setBackground(PANEL_BACKGROUND);
        legendTable.getTableHeader().setForeground(PRIMARY_TEXT);
    }

    private void configureComposedMaskTable() {
        composedMaskTable.setBackground(FIELD_BACKGROUND);
        composedMaskTable.setForeground(PRIMARY_TEXT);
        composedMaskTable.setGridColor(BORDER);
        composedMaskTable.setSelectionBackground(new Color(42, 55, 78));
        composedMaskTable.setSelectionForeground(PRIMARY_TEXT);
        composedMaskTable.setFont(BODY_FONT);
        composedMaskTable.setRowHeight(23);
        composedMaskTable.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        composedMaskTable.getTableHeader().setBackground(PANEL_BACKGROUND);
        composedMaskTable.getTableHeader().setForeground(PRIMARY_TEXT);
        composedMaskTable.getColumnModel().getColumn(1).setCellEditor(
                new DefaultCellEditor(new JComboBox<>(IrisImageMapMaskOperation.values()))
        );
    }

    private void removeLegendRows() {
        removeRows(legendTable, legendModel);
    }

    private static void removeRows(JTable table, DefaultTableModel model) {
        int[] selected = table.getSelectedRows();
        for (int index = selected.length - 1; index >= 0; index--) {
            model.removeRow(selected[index]);
        }
    }

    private List<IrisImageMapMask> readMasks() {
        List<ImageMapStudioModel.MaskRow> rows = new ArrayList<>();
        for (int row = 0; row < composedMaskModel.getRowCount(); row++) {
            rows.add(ImageMapStudioModel.maskRow(
                    String.valueOf(composedMaskModel.getValueAt(row, 0)),
                    composedMaskModel.getValueAt(row, 1),
                    Boolean.TRUE.equals(composedMaskModel.getValueAt(row, 2)),
                    composedMaskModel.getValueAt(row, 3),
                    composedMaskModel.getValueAt(row, 4)
            ));
        }
        return ImageMapStudioModel.masks(rows);
    }

    private void applyMasks(List<IrisImageMapMask> masks) {
        composedMaskModel.setRowCount(0);
        for (ImageMapStudioModel.MaskRow row : ImageMapStudioModel.maskRows(masks)) {
            composedMaskModel.addRow(new Object[]{
                    row.map(), row.operation(), row.inverted(), row.threshold(), row.falloff()
            });
        }
    }

    private void validateMaskApplication(List<IrisImageMapMask> masks) {
        if (selected(applicationPicker, "application") == IrisImageMapApplication.MASK && !masks.isEmpty()) {
            throw new IllegalArgumentException("A MASK binding cannot reference composed masks");
        }
    }

    private void appendDiagnostic(String diagnostic) {
        if (diagnostic == null || diagnostic.isBlank()) {
            return;
        }
        String current = diagnosticsArea.getText();
        diagnosticsArea.setForeground(ERROR);
        diagnosticsArea.setText(current.isBlank() ? diagnostic : current + "\n" + diagnostic);
    }

    private void showFailure(String prefix, Exception exception) {
        Throwable cause = exception;
        while (cause.getCause() != null && cause != cause.getCause()) {
            cause = cause.getCause();
        }
        if (!(cause instanceof IllegalArgumentException)) {
            IrisLogging.reportError(cause);
        }
        diagnosticsArea.setForeground(ERROR);
        diagnosticsArea.setText(prefix + ": " + String.valueOf(cause.getMessage()));
        statusLabel.setText(prefix);
    }

    private void setStatus(TextKey key) {
        statusLabel.setText(packFolder.getName() + "  |  " + dimensionKey + "  |  " + text(key));
    }

    private void requireSource() {
        if (sourcePath == null) {
            throw new IllegalArgumentException(text(DesktopUiMessages.IMAGEMAP_NO_SOURCE));
        }
    }

    private static JPanel form() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setOpaque(false);
        return panel;
    }

    private static void addRow(JPanel panel, int row, TextKey key, Component component) {
        GridBagConstraints labelConstraints = new GridBagConstraints();
        labelConstraints.gridx = 0;
        labelConstraints.gridy = row;
        labelConstraints.anchor = GridBagConstraints.WEST;
        labelConstraints.insets = new Insets(3, 0, 3, 8);
        JLabel label = label(key);
        panel.add(label, labelConstraints);

        GridBagConstraints componentConstraints = new GridBagConstraints();
        componentConstraints.gridx = 1;
        componentConstraints.gridy = row;
        componentConstraints.weightx = 1D;
        componentConstraints.fill = GridBagConstraints.HORIZONTAL;
        componentConstraints.insets = new Insets(3, 0, 3, 0);
        panel.add(component, componentConstraints);
    }

    private static JPanel section(TextKey title, Component content) {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.setBackground(PANEL_BACKGROUND);
        panel.setBorder(BorderFactory.createLineBorder(BORDER));
        JLabel heading = new JLabel(text(title));
        heading.setForeground(PRIMARY_TEXT);
        heading.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 12));
        heading.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, BORDER),
                BorderFactory.createEmptyBorder(7, 9, 7, 9)
        ));
        panel.add(heading, BorderLayout.NORTH);
        JPanel body = new JPanel(new BorderLayout());
        body.setOpaque(false);
        body.setBorder(BorderFactory.createEmptyBorder(8, 9, 9, 9));
        body.add(content, BorderLayout.CENTER);
        panel.add(body, BorderLayout.CENTER);
        panel.setMaximumSize(new Dimension(Integer.MAX_VALUE, panel.getPreferredSize().height + 300));
        return panel;
    }

    private static JPanel single(Component component) {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setOpaque(false);
        panel.add(component, BorderLayout.CENTER);
        return panel;
    }

    private static JLabel label(TextKey key) {
        JLabel label = new JLabel(text(key));
        label.setForeground(SECONDARY_TEXT);
        label.setFont(LABEL_FONT);
        return label;
    }

    private static JButton button(TextKey key) {
        JButton button = new JButton(text(key));
        button.setBackground(FIELD_BACKGROUND);
        button.setForeground(PRIMARY_TEXT);
        button.setFocusPainted(false);
        button.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(BORDER),
                BorderFactory.createEmptyBorder(5, 9, 5, 9)
        ));
        return button;
    }

    private static JTextField field(String value) {
        JTextField field = new JTextField(value);
        field.setBackground(FIELD_BACKGROUND);
        field.setForeground(PRIMARY_TEXT);
        field.setCaretColor(PRIMARY_TEXT);
        field.setFont(BODY_FONT);
        field.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(BORDER),
                BorderFactory.createEmptyBorder(5, 7, 5, 7)
        ));
        return field;
    }

    private static JTextArea area(int rows) {
        JTextArea area = new JTextArea(rows, 20);
        area.setEditable(false);
        area.setLineWrap(true);
        area.setWrapStyleWord(true);
        area.setBackground(FIELD_BACKGROUND);
        area.setForeground(PRIMARY_TEXT);
        area.setFont(BODY_FONT);
        area.setBorder(BorderFactory.createEmptyBorder(6, 7, 6, 7));
        return area;
    }

    private static JCheckBox check() {
        JCheckBox check = new JCheckBox();
        check.setOpaque(false);
        check.setForeground(PRIMARY_TEXT);
        check.setFocusPainted(false);
        return check;
    }

    private static JCheckBox selectedCheck() {
        JCheckBox check = check();
        check.setSelected(true);
        return check;
    }

    private static <T> JComboBox<T> combo(T[] values) {
        JComboBox<T> combo = new JComboBox<>(values);
        styleCombo(combo);
        return combo;
    }

    private static void styleCombo(JComboBox<?> combo) {
        combo.setBackground(FIELD_BACKGROUND);
        combo.setForeground(PRIMARY_TEXT);
        combo.setFont(BODY_FONT);
        combo.setFocusable(false);
    }

    private static double number(JTextField field, String name) {
        double value;
        try {
            value = Double.parseDouble(field.getText().trim());
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(name + " must be a number", exception);
        }
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException(name + " must be finite");
        }
        return value;
    }

    private static int integer(JTextField field, String name) {
        try {
            return Integer.parseInt(field.getText().trim());
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(name + " must be an integer", exception);
        }
    }

    private static String requiredText(JTextField field, String name) {
        String value = field.getText().trim();
        if (value.isEmpty()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }

    private static <T> T selected(JComboBox<T> combo, String name) {
        int index = combo.getSelectedIndex();
        if (index < 0) {
            throw new IllegalArgumentException("A " + name + " is required");
        }
        return combo.getItemAt(index);
    }

    private static String text(TextKey key) {
        return IrisLanguage.plain(key);
    }

    private record PresetState(
            IrisImageMap definition,
            Path source,
            String mapKey,
            String bindingKey,
            IrisImageMapApplication application,
            List<IrisImageMapMask> masks,
            IrisWorldBoundary boundary
    ) {
    }
}
