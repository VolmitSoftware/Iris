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

import com.volmit.iris.core.gui.MapHoverProbe.PickStep;
import com.volmit.iris.core.gui.NoisePickSpread.Band;
import com.volmit.iris.core.gui.components.MapLabels;
import com.volmit.iris.core.gui.components.RenderType;
import com.volmit.iris.core.loader.IrisRegistrant;
import com.volmit.iris.engine.object.IrisBiome;

import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.geom.RoundRectangle2D;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * The card that follows the cursor, laid out like IntelliJ's documentation popup: the biome as the title, the steps
 * that picked it as a small tree, and with Shift the terrain, location and pack files below.
 */
record MapHoverCard(Header header, String subtitle, String pathTitle, List<PickStep> path, boolean showsBands,
                    List<Group> groups, List<Hint> hints) {
    private static final int MINIMUM_CONTENT_WIDTH = 260;
    private static final int PADDING = 12;
    private static final int HEADER_TOP = 10;
    private static final int HEADER_ICON_SIZE = 14;
    private static final int NODE_ICON_SIZE = 12;
    private static final int ICON_GAP = 8;
    private static final int KIND_GAP = 16;
    private static final int GROUP_GAP = 8;
    private static final int GROUP_TITLE_GAP = 4;
    private static final int NODE_HEIGHT = 24;
    private static final int NODE_INDENT = 18;
    private static final int CHIP_GAP = 16;
    private static final int CHIP_PADDING_X = 5;
    private static final int CHIP_PADDING_Y = 1;
    private static final int BAND_HEIGHT = 4;
    private static final int BAND_GAP = 6;
    private static final int MARKER_WIDTH = 2;
    private static final int MARKER_HEIGHT = 10;
    private static final int COLUMN_GAP = 14;
    private static final int ROW_GAP = 3;
    private static final int BOTTOM_PADDING = 10;
    private static final int FOOTER_PADDING_Y = 7;
    private static final int KEY_PADDING_X = 4;
    private static final int KEY_GAP = 4;
    private static final int HINT_GAP = 14;
    private static final Color ICON_EDGE = new ThemeColor(new Color(0, 0, 0, 38), new Color(255, 255, 255, 38));
    private static final Color MARKER_EDGE = new Color(0, 0, 0, 200);

    /**
     * @param subject  the biome, or region in the region view, the drawn tile shows; null while that tile is drawing
     * @param region   the region the drawn tile shows
     * @param details  the probed facts about the spot, or null until they arrive
     * @param detailed whether Shift is held, which trades the noise bands for terrain, location and pack files
     */
    static MapHoverCard of(MapLabels.Label subject, MapLabels.Label region, MapHoverProbe.Details details,
                           RenderType type, boolean detailed, double worldX, double worldZ) {
        List<PickStep> path = details == null ? List.of() : details.pickPath();
        IrisBiome biome = cardBiome(subject, details, type);
        List<Group> groups = new ArrayList<>();
        if (detailed) {
            addTerrainGroup(groups, details, type);
            groups.add(locationGroup(worldX, worldZ));
            addFilesGroup(groups, biome, details != null ? details.region() : region == null ? null : region.subject());
        }
        return new MapHoverCard(header(subject, biome, type, path), subtitle(details), pathTitle(path), path, !detailed,
                groups, hints(type, detailed));
    }

    /**
     * Draws the card with its top left corner as close to the given point as fits inside the bounds.
     */
    void paint(Graphics2D canvas, int anchorX, int anchorY, int boundsWidth, int boundsHeight) {
        Graphics2D card = (Graphics2D) canvas.create();
        card.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        card.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        int padding = StudioTheme.scale(PADDING);
        int contentWidth = contentWidth(card);
        int width = contentWidth + padding * 2;
        int footerHeight = footerHeight(card);
        int height = StudioTheme.scale(HEADER_TOP) + headerHeight(card) + pathHeight(card) + groupsHeight(card)
                + StudioTheme.scale(BOTTOM_PADDING) + footerHeight;
        int x = Math.max(padding, Math.min(boundsWidth - width - padding, anchorX));
        int y = Math.max(padding, Math.min(boundsHeight - height - padding, anchorY));
        StudioOverlay.drawCard(card, x, y, width, height, footerHeight);

        int left = x + padding;
        int top = y + StudioTheme.scale(HEADER_TOP);
        top = drawHeader(card, left, top, contentWidth);
        top = drawPath(card, left, top, contentWidth);
        drawGroups(card, left, top);
        drawFooter(card, left, y + height - footerHeight);
        card.dispose();
    }

    private int contentWidth(Graphics2D canvas) {
        int width = Math.max(headerWidth(canvas), pathWidth(canvas));
        width = Math.max(width, groupsWidth(canvas));
        width = Math.max(width, footerWidth(canvas));
        return Math.max(StudioTheme.scale(MINIMUM_CONTENT_WIDTH), width);
    }

    // Header: the biome's colour, its name in bold with its kind on the right, and the ground's height below.
    private int headerTextOffset() {
        return header.color() == null ? 0 : StudioTheme.scale(HEADER_ICON_SIZE + ICON_GAP);
    }

    private int headerWidth(Graphics2D canvas) {
        int width = headerTextOffset() + canvas.getFontMetrics(StudioTheme.boldFont()).stringWidth(header.name());
        if (header.kind() != null) {
            width += StudioTheme.scale(KIND_GAP) + canvas.getFontMetrics(StudioTheme.smallFont()).stringWidth(header.kind());
        }
        if (subtitle != null) {
            width = Math.max(width, headerTextOffset() + canvas.getFontMetrics(StudioTheme.smallFont()).stringWidth(subtitle));
        }
        return width;
    }

    private int headerHeight(Graphics2D canvas) {
        int height = canvas.getFontMetrics(StudioTheme.boldFont()).getHeight();
        if (subtitle != null) {
            height += canvas.getFontMetrics(StudioTheme.smallFont()).getHeight();
        }
        return height;
    }

    private int drawHeader(Graphics2D canvas, int left, int top, int contentWidth) {
        FontMetrics bold = canvas.getFontMetrics(StudioTheme.boldFont());
        if (header.color() != null) {
            int iconSize = StudioTheme.scale(HEADER_ICON_SIZE);
            drawIcon(canvas, header.color(), left, top + (bold.getHeight() - iconSize) / 2, iconSize);
        }
        int textLeft = left + headerTextOffset();
        int baseline = top + bold.getAscent();
        drawText(canvas, header.name(), StudioTheme.boldFont(), StudioTheme.TEXT, textLeft, baseline);
        if (header.kind() != null) {
            FontMetrics small = canvas.getFontMetrics(StudioTheme.smallFont());
            drawText(canvas, header.kind(), StudioTheme.smallFont(), StudioTheme.MUTED_TEXT,
                    left + contentWidth - small.stringWidth(header.kind()), baseline);
        }
        top += bold.getHeight();
        if (subtitle != null) {
            FontMetrics small = canvas.getFontMetrics(StudioTheme.smallFont());
            drawText(canvas, subtitle, StudioTheme.smallFont(), StudioTheme.SECONDARY_TEXT, textLeft, top + small.getAscent());
            top += small.getHeight();
        }
        return top;
    }

    // Path: one indented row per step, its value as a chip on the right and, without Shift, the noise bands below it.
    private int pathWidth(Graphics2D canvas) {
        int width = 0;
        for (int level = 0; level < path.size(); level++) {
            PickStep step = path.get(level);
            int row = nodeTextLeft(level) + canvas.getFontMetrics(StudioTheme.font()).stringWidth(step.picked().name() + " ")
                    + canvas.getFontMetrics(StudioTheme.smallFont()).stringWidth(step.reason());
            if (step.value() != null) {
                row += StudioTheme.scale(CHIP_GAP) + chipWidth(canvas, step.value());
            }
            width = Math.max(width, row);
        }
        return width;
    }

    private int pathHeight(Graphics2D canvas) {
        if (path.isEmpty()) {
            return 0;
        }
        int height = groupTitleHeight(canvas);
        for (PickStep step : path) {
            height += nodeHeight(step);
        }
        return height;
    }

    private int nodeHeight(PickStep step) {
        int height = StudioTheme.scale(NODE_HEIGHT);
        return showsBands && step.showsBands() ? height + StudioTheme.scale(BAND_HEIGHT + BAND_GAP) : height;
    }

    private static int nodeTextLeft(int level) {
        return StudioTheme.scale(level * NODE_INDENT + NODE_ICON_SIZE + ICON_GAP);
    }

    private int drawPath(Graphics2D canvas, int left, int top, int contentWidth) {
        if (path.isEmpty()) {
            return top;
        }
        top = drawGroupTitle(canvas, pathTitle, left, top);
        for (int level = 0; level < path.size(); level++) {
            PickStep step = path.get(level);
            drawNode(canvas, step, left, top, level, contentWidth);
            if (showsBands && step.showsBands()) {
                int bandsLeft = left + nodeTextLeft(level);
                drawBands(canvas, step, bandsLeft, top + StudioTheme.scale(NODE_HEIGHT), left + contentWidth - bandsLeft);
            }
            top += nodeHeight(step);
        }
        return top;
    }

    private void drawNode(Graphics2D canvas, PickStep step, int left, int top, int level, int contentWidth) {
        int rowHeight = StudioTheme.scale(NODE_HEIGHT);
        int iconSize = StudioTheme.scale(NODE_ICON_SIZE);
        drawIcon(canvas, step.picked().color(), left + StudioTheme.scale(level * NODE_INDENT), top + (rowHeight - iconSize) / 2,
                iconSize);
        FontMetrics regular = canvas.getFontMetrics(StudioTheme.font());
        int baseline = top + (rowHeight - regular.getHeight()) / 2 + regular.getAscent();
        int textLeft = left + nodeTextLeft(level);
        String name = step.picked().name() + " ";
        drawText(canvas, name, StudioTheme.font(), StudioTheme.TEXT, textLeft, baseline);
        drawText(canvas, step.reason(), StudioTheme.smallFont(), StudioTheme.MUTED_TEXT, textLeft + regular.stringWidth(name),
                baseline);
        if (step.value() != null) {
            drawChip(canvas, step.value(), left + contentWidth - chipWidth(canvas, step.value()), top, rowHeight);
        }
    }

    // A strip with each option's band of noise in its colour, and a marker where this spot's noise falls.
    private static void drawBands(Graphics2D canvas, PickStep step, int left, int top, int width) {
        int height = StudioTheme.scale(BAND_HEIGHT);
        Shape clip = canvas.getClip();
        canvas.clip(new RoundRectangle2D.Double(left, top, width, height, height, height));
        for (Band band : step.bands()) {
            int from = left + (int) Math.round(band.fromNoise() * width);
            int to = left + (int) Math.round(band.toNoise() * width);
            canvas.setColor(new Color(band.option().color()));
            canvas.fillRect(from, top, Math.max(1, to - from), height);
        }
        canvas.setClip(clip);
        int markerWidth = StudioTheme.scale(MARKER_WIDTH);
        int markerHeight = StudioTheme.scale(MARKER_HEIGHT);
        int markerX = left + (int) Math.round(Math.clamp(step.noise(), 0D, 1D) * (width - markerWidth));
        int markerY = top + (height - markerHeight) / 2;
        canvas.setColor(MARKER_EDGE);
        canvas.fillRect(markerX - 1, markerY - 1, markerWidth + 2, markerHeight + 2);
        canvas.setColor(Color.WHITE);
        canvas.fillRect(markerX, markerY, markerWidth, markerHeight);
    }

    private static Font chipFont() {
        return StudioTheme.monospacedFont().deriveFont(StudioTheme.smallFont().getSize2D());
    }

    private static int chipWidth(Graphics2D canvas, String value) {
        return canvas.getFontMetrics(chipFont()).stringWidth(value) + StudioTheme.scale(CHIP_PADDING_X) * 2;
    }

    // Styled like an inlay hint in IntelliJ's editor.
    private static void drawChip(Graphics2D canvas, String value, int left, int rowTop, int rowHeight) {
        FontMetrics metrics = canvas.getFontMetrics(chipFont());
        int height = metrics.getHeight() + StudioTheme.scale(CHIP_PADDING_Y) * 2;
        int top = rowTop + (rowHeight - height) / 2;
        canvas.setColor(StudioTheme.HOVER_BACKGROUND);
        int arc = StudioTheme.scale(8);
        canvas.fillRoundRect(left, top, chipWidth(canvas, value), height, arc, arc);
        drawText(canvas, value, chipFont(), StudioTheme.SECONDARY_TEXT, left + StudioTheme.scale(CHIP_PADDING_X),
                top + StudioTheme.scale(CHIP_PADDING_Y) + metrics.getAscent());
    }

    private static void drawIcon(Graphics2D canvas, int rgb, int left, int top, int size) {
        int arc = Math.max(2, size / 2);
        canvas.setColor(new Color(rgb));
        canvas.fillRoundRect(left, top, size, size, arc, arc);
        canvas.setColor(ICON_EDGE);
        canvas.drawRoundRect(left, top, size - 1, size - 1, arc, arc);
    }

    // Groups: sentence-case titles over rows of muted labels and values, all labels in one column.
    private int labelColumnWidth(Graphics2D canvas) {
        FontMetrics metrics = canvas.getFontMetrics(StudioTheme.font());
        return groups.stream().flatMap(group -> group.rows().stream())
                .mapToInt(row -> metrics.stringWidth(row.label())).max().orElse(0);
    }

    private int groupsWidth(Graphics2D canvas) {
        FontMetrics metrics = canvas.getFontMetrics(StudioTheme.font());
        int valueWidth = groups.stream().flatMap(group -> group.rows().stream())
                .mapToInt(row -> metrics.stringWidth(row.value())).max().orElse(0);
        return labelColumnWidth(canvas) + StudioTheme.scale(COLUMN_GAP) + valueWidth;
    }

    private int groupsHeight(Graphics2D canvas) {
        int rowHeight = canvas.getFontMetrics(StudioTheme.font()).getHeight() + StudioTheme.scale(ROW_GAP);
        return groups.stream().mapToInt(group -> groupTitleHeight(canvas) + group.rows().size() * rowHeight).sum();
    }

    private void drawGroups(Graphics2D canvas, int left, int top) {
        FontMetrics regular = canvas.getFontMetrics(StudioTheme.font());
        int valueLeft = left + labelColumnWidth(canvas) + StudioTheme.scale(COLUMN_GAP);
        for (Group group : groups) {
            top = drawGroupTitle(canvas, group.title(), left, top);
            for (Row row : group.rows()) {
                int baseline = top + regular.getAscent();
                drawText(canvas, row.label(), StudioTheme.font(), StudioTheme.SECONDARY_TEXT, left, baseline);
                drawText(canvas, row.value(), StudioTheme.font(), row.link() ? StudioTheme.LINK : StudioTheme.TEXT, valueLeft,
                        baseline);
                top += regular.getHeight() + StudioTheme.scale(ROW_GAP);
            }
        }
    }

    private static int groupTitleHeight(Graphics2D canvas) {
        return StudioTheme.scale(GROUP_GAP) + canvas.getFontMetrics(StudioTheme.smallFont()).getHeight()
                + StudioTheme.scale(GROUP_TITLE_GAP);
    }

    private static int drawGroupTitle(Graphics2D canvas, String title, int left, int top) {
        FontMetrics small = canvas.getFontMetrics(StudioTheme.smallFont());
        top += StudioTheme.scale(GROUP_GAP);
        drawText(canvas, title, StudioTheme.smallFont(), StudioTheme.MUTED_TEXT, left, top + small.getAscent());
        return top + small.getHeight() + StudioTheme.scale(GROUP_TITLE_GAP);
    }

    // Footer: the shortcuts as key caps, each followed by what it does.
    private int footerWidth(Graphics2D canvas) {
        FontMetrics small = canvas.getFontMetrics(StudioTheme.smallFont());
        int width = 0;
        for (Hint hint : hints) {
            width += keyWidth(small, hint.key()) + StudioTheme.scale(KEY_GAP) + small.stringWidth(hint.action());
        }
        return width + StudioTheme.scale(HINT_GAP) * Math.max(0, hints.size() - 1);
    }

    private static int footerHeight(Graphics2D canvas) {
        return canvas.getFontMetrics(StudioTheme.smallFont()).getHeight() + StudioTheme.scale(FOOTER_PADDING_Y) * 2;
    }

    private static int keyWidth(FontMetrics small, String key) {
        return small.stringWidth(key) + StudioTheme.scale(KEY_PADDING_X) * 2;
    }

    private void drawFooter(Graphics2D canvas, int left, int top) {
        FontMetrics small = canvas.getFontMetrics(StudioTheme.smallFont());
        int textTop = top + StudioTheme.scale(FOOTER_PADDING_Y);
        int baseline = textTop + small.getAscent();
        int arc = StudioTheme.scale(6);
        for (Hint hint : hints) {
            int keyWidth = keyWidth(small, hint.key());
            canvas.setColor(StudioTheme.HOVER_BACKGROUND);
            canvas.fillRoundRect(left, textTop, keyWidth, small.getHeight(), arc, arc);
            drawText(canvas, hint.key(), StudioTheme.smallFont(), StudioTheme.SECONDARY_TEXT,
                    left + StudioTheme.scale(KEY_PADDING_X), baseline);
            left += keyWidth + StudioTheme.scale(KEY_GAP);
            drawText(canvas, hint.action(), StudioTheme.smallFont(), StudioTheme.MUTED_TEXT, left, baseline);
            left += small.stringWidth(hint.action()) + StudioTheme.scale(HINT_GAP);
        }
    }

    private static void drawText(Graphics2D canvas, String text, Font font, Color color, int left, int baseline) {
        canvas.setFont(font);
        canvas.setColor(color);
        canvas.drawString(text, left, baseline);
    }

    // The card is about a biome; the region view's tiles only know the region until the probe finds the biome.
    private static IrisBiome cardBiome(MapLabels.Label subject, MapHoverProbe.Details details, RenderType type) {
        if (subject != null && subject.subject() instanceof IrisBiome biome) {
            return biome;
        }
        if (details == null) {
            return null;
        }
        return type == RenderType.CAVE_LAND ? details.caveBiome() : details.surfaceBiome();
    }

    private static Header header(MapLabels.Label subject, IrisBiome biome, RenderType type, List<PickStep> path) {
        if (biome != null) {
            return new Header(biome.getName(), colorOf(biome, path), type == RenderType.CAVE_LAND ? "Cave biome" : "Biome");
        }
        if (subject != null) {
            return new Header(subject.name(), null, subject.kind() == MapLabels.Kind.REGION ? "Region" : "Biome");
        }
        return new Header("Drawing…", null, null);
    }

    private static Integer colorOf(IrisBiome biome, List<PickStep> path) {
        return path.stream()
                .filter(step -> step.picked().key().equals(biome.getLoadKey()))
                .reduce((first, last) -> last)
                .map(step -> step.picked().color())
                .orElse(null);
    }

    private static String subtitle(MapHoverProbe.Details details) {
        if (details == null) {
            return null;
        }
        return Math.round(details.height()) + " · " + heightAboveSea(details.height() - details.fluidHeight()) + " · slope "
                + Math.round(details.slopeDegrees()) + "°";
    }

    private static String pathTitle(List<PickStep> path) {
        return path.size() > 1 ? "Picked by" : "Region";
    }

    private static void addTerrainGroup(List<Group> groups, MapHoverProbe.Details details, RenderType type) {
        if (details == null) {
            return;
        }
        List<Row> rows = new ArrayList<>();
        IrisBiome surface = details.surfaceBiome();
        IrisBiome cave = details.caveBiome();
        if (type == RenderType.CAVE_LAND && surface != null) {
            rows.add(new Row("Surface biome", surface.getName(), false));
        } else if (type != RenderType.CAVE_LAND && cave != null && cave != surface) {
            rows.add(new Row("Cave biome", cave.getName(), false));
        }
        if (details.vanillaBiome() != null) {
            rows.add(new Row("Minecraft", details.vanillaBiome().replace('_', ' '), false));
        }
        if (!rows.isEmpty()) {
            groups.add(new Group("Terrain", rows));
        }
    }

    private static Group locationGroup(double worldX, double worldZ) {
        int blockX = MapCanvas.floorWorldCoordinate(worldX);
        int blockZ = MapCanvas.floorWorldCoordinate(worldZ);
        return new Group("Location", List.of(
                new Row("Block", blockX + ", " + blockZ, false),
                new Row("Chunk", (blockX >> 4) + ", " + (blockZ >> 4), false),
                new Row("Region file", "r." + (blockX >> 9) + "." + (blockZ >> 9) + ".mca", false)));
    }

    private static void addFilesGroup(List<Group> groups, IrisBiome biome, Object region) {
        List<Row> rows = new ArrayList<>();
        if (biome != null) {
            rows.add(new Row("Biome", packPath(biome), true));
        }
        if (region instanceof IrisRegistrant registrant) {
            rows.add(new Row("Region", packPath(registrant), true));
        }
        if (!rows.isEmpty()) {
            groups.add(new Group("Files", rows));
        }
    }

    // The key is the path under the type's folder, so folder, key and extension give the file within the pack.
    private static String packPath(IrisRegistrant registrant) {
        File file = registrant.getLoadFile();
        String fileName = file == null ? "" : file.getName();
        int extensionStart = fileName.lastIndexOf('.');
        String extension = extensionStart < 0 ? "" : fileName.substring(extensionStart);
        return registrant.getFolderName() + "/" + registrant.getLoadKey() + extension;
    }

    private static List<Hint> hints(RenderType type, boolean detailed) {
        List<Hint> hints = new ArrayList<>();
        if (!detailed) {
            hints.add(new Hint("Shift", "More"));
        }
        hints.add(new Hint("H", "Highlight"));
        if (type != RenderType.CONTINENT) {
            hints.add(new Hint("Alt+Click", "Open file"));
        }
        return hints;
    }

    private static String heightAboveSea(double height) {
        long rounded = Math.round(height);
        return rounded < 0 ? -rounded + " below sea" : rounded + " above sea";
    }

    /**
     * @param color the biome's map colour, or null when no step of the path tells it
     * @param kind  what the title names, like "Biome", or null while drawing
     */
    record Header(String name, Integer color, String kind) {
    }

    record Group(String title, List<Row> rows) {
    }

    record Row(String label, String value, boolean link) {
    }

    record Hint(String key, String action) {
    }
}
