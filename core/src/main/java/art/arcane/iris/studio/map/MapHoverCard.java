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

import art.arcane.iris.studio.map.MapHoverProbe.PickStep;
import art.arcane.iris.studio.map.MapHoverProbe.StepKind;
import art.arcane.iris.studio.map.NoisePickSpread.Band;
import art.arcane.iris.studio.map.components.MapLabels;
import art.arcane.iris.studio.map.components.TileType;
import art.arcane.iris.pack.loading.IrisRegistrant;
import art.arcane.iris.generation.biome.IrisBiome;

import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * The card that follows the cursor: the biome and its region, the ground's height, and a row per noise that picked
 * them, each split into one segment per option with the picked one lit and a line at the spot's value. Shift adds the
 * terrain, location and pack files; a footer bar lists the shortcuts.
 */
record MapHoverCard(Header header, String subtitle, List<NoiseRow> noiseRows, List<Group> groups, List<Hint> hints) {
    private static final int MINIMUM_CONTENT_WIDTH = 240;
    private static final int MINIMUM_SEGMENTS_WIDTH = 120;
    private static final int PADDING_X = 12;
    private static final int PADDING_TOP = 9;
    private static final int PADDING_BOTTOM = 11;
    private static final int ICON_SIZE = 12;
    private static final int ICON_GAP = 8;
    private static final int REGION_GAP = 16;
    private static final int ROWS_GAP = 9;
    private static final int ROW_HEIGHT = 18;
    private static final int COLUMN_GAP = 8;
    private static final int SEGMENT_HEIGHT = 6;
    private static final int WIDE_SEGMENT_GAP = 2;
    private static final int NARROW_SEGMENT_GAP = 1;
    private static final int WIDE_SEGMENT_WIDTH = 8;
    private static final int NARROW_SEGMENT_WIDTH = 3;
    private static final int MARKER_WIDTH = 2;
    private static final int MARKER_HEIGHT = 12;
    private static final int GROUP_GAP = 8;
    private static final int GROUP_TITLE_GAP = 4;
    private static final int DETAIL_COLUMN_GAP = 14;
    private static final int DETAIL_ROW_GAP = 3;
    private static final int FOOTER_PADDING_Y = 7;
    private static final int KEY_PADDING_X = 4;
    private static final int KEY_GAP = 4;
    private static final int HINT_GAP = 14;
    private static final Color ICON_EDGE = new ThemeColor(new Color(0, 0, 0, 38), new Color(255, 255, 255, 38));

    /**
     * @param subject  the biome, or region in the region view, the drawn tile shows; null while that tile is drawing
     * @param region   the region the drawn tile shows
     * @param details  the probed facts about the spot, or null until they arrive
     * @param detailed whether Shift is held, which adds the terrain, location and pack files
     */
    static MapHoverCard of(MapLabels.Label subject, MapLabels.Label region, MapHoverProbe.Details details,
                           TileType type, boolean detailed, double worldX, double worldZ) {
        List<PickStep> path = details == null ? List.of() : details.pickPath();
        IrisBiome biome = cardBiome(subject, details, type);
        List<Group> groups = new ArrayList<>();
        if (detailed) {
            addTerrainGroup(groups, details, type);
            groups.add(locationGroup(worldX, worldZ));
            addFilesGroup(groups, biome, details != null ? details.region() : region == null ? null : region.subject());
        }
        return new MapHoverCard(header(subject, region, biome, path), subtitle(details, path), noiseRows(path), groups,
                hints(detailed));
    }

    /**
     * Draws the card with its top left corner as close to the given point as fits inside the bounds.
     */
    void paint(Graphics2D canvas, int anchorX, int anchorY, int boundsWidth, int boundsHeight) {
        Graphics2D card = (Graphics2D) canvas.create();
        card.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        card.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        int paddingX = StudioTheme.scale(PADDING_X);
        int contentWidth = contentWidth(card);
        int width = contentWidth + paddingX * 2;
        int footerHeight = footerHeight(card);
        int height = StudioTheme.scale(PADDING_TOP) + headerHeight(card) + rowsHeight() + groupsHeight(card)
                + StudioTheme.scale(PADDING_BOTTOM) + footerHeight;
        int x = Math.max(paddingX, Math.min(boundsWidth - width - paddingX, anchorX));
        int y = Math.max(paddingX, Math.min(boundsHeight - height - paddingX, anchorY));
        StudioOverlay.drawCard(card, x, y, width, height, footerHeight);

        int left = x + paddingX;
        int top = y + StudioTheme.scale(PADDING_TOP);
        top = drawHeader(card, left, top, contentWidth);
        top = drawNoiseRows(card, left, top, contentWidth);
        drawGroups(card, left, top);
        drawFooter(card, left, y + height - footerHeight);
        card.dispose();
    }

    private int contentWidth(Graphics2D canvas) {
        int width = Math.max(headerWidth(canvas), rowsWidth(canvas));
        width = Math.max(width, groupsWidth(canvas));
        width = Math.max(width, footerWidth(canvas));
        return Math.max(StudioTheme.scale(MINIMUM_CONTENT_WIDTH), width);
    }

    // Header: the biome's colour and name with its region on the right, and the ground below.
    private int headerTextOffset() {
        return header.color() == null ? 0 : StudioTheme.scale(ICON_SIZE + ICON_GAP);
    }

    private int headerWidth(Graphics2D canvas) {
        FontMetrics small = canvas.getFontMetrics(StudioTheme.smallFont());
        int width = headerTextOffset() + canvas.getFontMetrics(StudioTheme.boldFont()).stringWidth(header.name());
        if (header.region() != null) {
            width += StudioTheme.scale(REGION_GAP) + small.stringWidth(header.region());
        }
        if (subtitle != null) {
            width = Math.max(width, headerTextOffset() + small.stringWidth(subtitle));
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
        FontMetrics small = canvas.getFontMetrics(StudioTheme.smallFont());
        if (header.color() != null) {
            int iconSize = StudioTheme.scale(ICON_SIZE);
            drawIcon(canvas, header.color(), left, top + (bold.getHeight() - iconSize) / 2, iconSize);
        }
        int textLeft = left + headerTextOffset();
        int baseline = top + bold.getAscent();
        drawText(canvas, header.name(), StudioTheme.boldFont(), StudioTheme.TEXT, textLeft, baseline);
        if (header.region() != null) {
            drawText(canvas, header.region(), StudioTheme.smallFont(), StudioTheme.MUTED_TEXT,
                    left + contentWidth - small.stringWidth(header.region()), baseline);
        }
        top += bold.getHeight();
        if (subtitle != null) {
            drawText(canvas, subtitle, StudioTheme.smallFont(), StudioTheme.SECONDARY_TEXT, textLeft, top + small.getAscent());
            top += small.getHeight();
        }
        return top;
    }

    private static void drawIcon(Graphics2D canvas, int rgb, int left, int top, int size) {
        int arc = Math.max(2, size / 2);
        canvas.setColor(new Color(rgb));
        canvas.fillRoundRect(left, top, size, size, arc, arc);
        canvas.setColor(ICON_EDGE);
        canvas.drawRoundRect(left, top, size - 1, size - 1, arc, arc);
    }

    // Noise rows: a muted label, the segments (or in hybrid terrain how the biome won), and the value on the right.
    private int labelColumnWidth(Graphics2D canvas) {
        FontMetrics small = canvas.getFontMetrics(StudioTheme.smallFont());
        return noiseRows.stream().mapToInt(row -> small.stringWidth(row.label())).max().orElse(0);
    }

    private int valueColumnWidth(Graphics2D canvas) {
        FontMetrics value = canvas.getFontMetrics(valueFont());
        return noiseRows.stream().mapToInt(row -> value.stringWidth(row.value())).max().orElse(0);
    }

    private int rowsWidth(Graphics2D canvas) {
        if (noiseRows.isEmpty()) {
            return 0;
        }
        FontMetrics small = canvas.getFontMetrics(StudioTheme.smallFont());
        int middle = noiseRows.stream()
                .mapToInt(row -> row.showsSegments() ? StudioTheme.scale(MINIMUM_SEGMENTS_WIDTH) : small.stringWidth(row.note()))
                .max().orElse(0);
        return labelColumnWidth(canvas) + valueColumnWidth(canvas) + middle + StudioTheme.scale(COLUMN_GAP) * 2;
    }

    private int rowsHeight() {
        return noiseRows.isEmpty() ? 0 : StudioTheme.scale(ROWS_GAP) + noiseRows.size() * StudioTheme.scale(ROW_HEIGHT);
    }

    private int drawNoiseRows(Graphics2D canvas, int left, int top, int contentWidth) {
        if (noiseRows.isEmpty()) {
            return top;
        }
        top += StudioTheme.scale(ROWS_GAP);
        int rowHeight = StudioTheme.scale(ROW_HEIGHT);
        int columnGap = StudioTheme.scale(COLUMN_GAP);
        int middleLeft = left + labelColumnWidth(canvas) + columnGap;
        int valueRight = left + contentWidth;
        int middleWidth = valueRight - valueColumnWidth(canvas) - columnGap - middleLeft;
        FontMetrics small = canvas.getFontMetrics(StudioTheme.smallFont());
        FontMetrics value = canvas.getFontMetrics(valueFont());
        for (NoiseRow row : noiseRows) {
            int baseline = top + (rowHeight - small.getHeight()) / 2 + small.getAscent();
            drawText(canvas, row.label(), StudioTheme.smallFont(), StudioTheme.MUTED_TEXT, left, baseline);
            if (row.showsSegments()) {
                drawSegments(canvas, row, middleLeft, top + (rowHeight - StudioTheme.scale(SEGMENT_HEIGHT)) / 2, middleWidth);
            } else {
                drawText(canvas, row.note(), StudioTheme.smallFont(), StudioTheme.SECONDARY_TEXT, middleLeft, baseline);
            }
            drawText(canvas, row.value(), valueFont(), StudioTheme.SECONDARY_TEXT, valueRight - value.stringWidth(row.value()),
                    baseline);
            top += rowHeight;
        }
        return top;
    }

    // One segment per band, as wide as the band's share of the noise; the picked one in its option's colour.
    private static void drawSegments(Graphics2D canvas, NoiseRow row, int left, int top, int width) {
        List<Band> bands = row.bands();
        int height = StudioTheme.scale(SEGMENT_HEIGHT);
        int gap = segmentGap(width, bands.size());
        int picked = pickedBandIndex(bands, row.noise());
        int arc = StudioTheme.scale(2);
        for (int index = 0; index < bands.size(); index++) {
            Band band = bands.get(index);
            int from = left + (int) Math.round(band.fromNoise() * width);
            int to = left + (int) Math.round(band.toNoise() * width);
            int segmentWidth = Math.max(1, to - from - (index == bands.size() - 1 ? 0 : gap));
            canvas.setColor(index == picked ? new Color(band.option().color()) : StudioTheme.OVERLAY_BORDER);
            canvas.fillRoundRect(from, top, segmentWidth, height, arc, arc);
        }
        drawMarker(canvas, left + (int) Math.round(Math.clamp(row.noise(), 0D, 1D) * width), top + height / 2);
    }

    private static int segmentGap(int width, int segmentCount) {
        int averageWidth = width / Math.max(1, segmentCount);
        if (averageWidth >= StudioTheme.scale(WIDE_SEGMENT_WIDTH)) {
            return StudioTheme.scale(WIDE_SEGMENT_GAP);
        }
        return averageWidth >= StudioTheme.scale(NARROW_SEGMENT_WIDTH) ? StudioTheme.scale(NARROW_SEGMENT_GAP) : 0;
    }

    // The engine gives values below 0 the first option and values above 1 the last, the same as this.
    private static int pickedBandIndex(List<Band> bands, double noise) {
        for (int index = 0; index < bands.size(); index++) {
            if (noise <= bands.get(index).toNoise()) {
                return index;
            }
        }
        return bands.size() - 1;
    }

    private static void drawMarker(Graphics2D canvas, int centerX, int centerY) {
        int width = StudioTheme.scale(MARKER_WIDTH);
        int height = StudioTheme.scale(MARKER_HEIGHT);
        int left = centerX - width / 2;
        int top = centerY - height / 2;
        canvas.setColor(StudioTheme.PANEL_BACKGROUND);
        canvas.fillRect(left - 1, top - 1, width + 2, height + 2);
        canvas.setColor(StudioTheme.TEXT);
        canvas.fillRect(left, top, width, height);
    }

    private static Font valueFont() {
        return StudioTheme.monospacedFont().deriveFont(StudioTheme.smallFont().getSize2D());
    }

    // Groups: sentence-case titles over rows of muted labels and values, all labels in one column.
    private int detailLabelWidth(Graphics2D canvas) {
        FontMetrics metrics = canvas.getFontMetrics(StudioTheme.font());
        return groups.stream().flatMap(group -> group.rows().stream())
                .mapToInt(row -> metrics.stringWidth(row.label())).max().orElse(0);
    }

    private int groupsWidth(Graphics2D canvas) {
        if (groups.isEmpty()) {
            return 0;
        }
        FontMetrics metrics = canvas.getFontMetrics(StudioTheme.font());
        int valueWidth = groups.stream().flatMap(group -> group.rows().stream())
                .mapToInt(row -> metrics.stringWidth(row.value())).max().orElse(0);
        return detailLabelWidth(canvas) + StudioTheme.scale(DETAIL_COLUMN_GAP) + valueWidth;
    }

    private int groupsHeight(Graphics2D canvas) {
        int rowHeight = canvas.getFontMetrics(StudioTheme.font()).getHeight() + StudioTheme.scale(DETAIL_ROW_GAP);
        return groups.stream().mapToInt(group -> groupTitleHeight(canvas) + group.rows().size() * rowHeight).sum();
    }

    private void drawGroups(Graphics2D canvas, int left, int top) {
        FontMetrics regular = canvas.getFontMetrics(StudioTheme.font());
        int valueLeft = left + detailLabelWidth(canvas) + StudioTheme.scale(DETAIL_COLUMN_GAP);
        for (Group group : groups) {
            top = drawGroupTitle(canvas, group.title(), left, top);
            for (Row row : group.rows()) {
                int baseline = top + regular.getAscent();
                drawText(canvas, row.label(), StudioTheme.font(), StudioTheme.SECONDARY_TEXT, left, baseline);
                drawText(canvas, row.value(), StudioTheme.font(), row.link() ? StudioTheme.LINK : StudioTheme.TEXT, valueLeft,
                        baseline);
                top += regular.getHeight() + StudioTheme.scale(DETAIL_ROW_GAP);
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
    private static IrisBiome cardBiome(MapLabels.Label subject, MapHoverProbe.Details details, TileType type) {
        if (subject != null && subject.subject() instanceof IrisBiome biome) {
            return biome;
        }
        if (details == null) {
            return null;
        }
        return type == TileType.CAVE_LAND ? details.caveBiome() : details.surfaceBiome();
    }

    private static Header header(MapLabels.Label subject, MapLabels.Label region, IrisBiome biome, List<PickStep> path) {
        String regionName = path.stream()
                .filter(step -> step.kind() == StepKind.REGION_NOISE || step.kind() == StepKind.PREVIEWED_REGION)
                .map(step -> step.picked().name())
                .findFirst()
                .orElse(region == null ? null : region.name());
        if (biome != null) {
            return new Header(biome.getName(), colorOf(biome, path), regionName);
        }
        if (subject != null) {
            return new Header(subject.name(), null, subject.kind() == MapLabels.Kind.REGION ? null : regionName);
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

    private static String subtitle(MapHoverProbe.Details details, List<PickStep> path) {
        if (details == null) {
            return null;
        }
        String ground = Math.round(details.height()) + " · " + signedHeight(details.height() - details.fluidHeight())
                + " · " + Math.round(details.slopeDegrees()) + "°";
        return parentOfChild(path).map(parent -> "child of " + parent + " · " + ground).orElse(ground);
    }

    // A child biome took the spot from the biome the noise picked, which is the step before it.
    private static Optional<String> parentOfChild(List<PickStep> path) {
        for (int index = 1; index < path.size(); index++) {
            if (path.get(index).kind() == StepKind.CHILD) {
                return Optional.of(path.get(index - 1).picked().name());
            }
        }
        return Optional.empty();
    }

    private static List<NoiseRow> noiseRows(List<PickStep> path) {
        List<NoiseRow> rows = new ArrayList<>();
        for (PickStep step : path) {
            switch (step.kind()) {
                case REGION_NOISE -> rows.add(new NoiseRow("Region", step.bands(), step.noise(), null, step.value()));
                case BIOME_NOISE -> rows.add(new NoiseRow(capitalize(step.detail()), step.bands(), step.noise(), null,
                        step.value()));
                case PREVIEWED_REGION, CHILD -> {
                }
            }
        }
        return rows;
    }

    private static void addTerrainGroup(List<Group> groups, MapHoverProbe.Details details, TileType type) {
        if (details == null) {
            return;
        }
        List<Row> rows = new ArrayList<>();
        IrisBiome surface = details.surfaceBiome();
        IrisBiome cave = details.caveBiome();
        if (type == TileType.CAVE_LAND && surface != null) {
            rows.add(new Row("Surface biome", surface.getName(), false));
        } else if (type != TileType.CAVE_LAND && cave != null && cave != surface) {
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

    private static List<Hint> hints(boolean detailed) {
        List<Hint> hints = new ArrayList<>();
        if (!detailed) {
            hints.add(new Hint("Shift", "More"));
        }
        hints.add(new Hint("H", "Highlight"));
        hints.add(new Hint("Alt+Click", "Open file"));
        return hints;
    }

    private static String signedHeight(double height) {
        long rounded = Math.round(height);
        return rounded < 0 ? "−" + -rounded : "+" + rounded;
    }

    private static String capitalize(String text) {
        return text.substring(0, 1).toUpperCase(Locale.ROOT) + text.substring(1);
    }

    /**
     * @param color  the biome's map colour, or null when no step of the path tells it
     * @param region the region's name, or null when the title already is the region
     */
    record Header(String name, Integer color, String region) {
    }

    /**
     * @param bands the bands the noise splits into, empty when no noise picked the step
     * @param note  how the step was picked when no noise did, or null
     */
    record NoiseRow(String label, List<Band> bands, double noise, String note, String value) {
        boolean showsSegments() {
            return !bands.isEmpty();
        }
    }

    record Group(String title, List<Row> rows) {
    }

    record Row(String label, String value, boolean link) {
    }

    record Hint(String key, String action) {
    }
}
