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

import com.volmit.iris.core.gui.components.MapLabels;
import com.volmit.iris.core.gui.components.RenderType;
import com.volmit.iris.core.loader.IrisRegistrant;
import com.volmit.iris.engine.HybridBiomeSelector;
import com.volmit.iris.engine.object.IrisBiome;

import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The card that follows the cursor: what the drawn map shows there at once, and the rest once probed, in titled
 * sections of labelled values.
 */
record MapHoverCard(String title, String subtitle, List<Section> sections, String footer) {
    private static final int PADDING = 12;
    private static final int SUBTITLE_GAP = 2;
    private static final int SECTION_GAP = 10;
    private static final int SECTION_TITLE_GAP = 4;
    private static final int COLUMN_GAP = 14;
    private static final int ROW_GAP = 2;

    /**
     * @param subject  the biome, or region in the region view, the drawn tile shows; null while that tile is drawing
     * @param details  the probed facts about the spot, or null until they arrive
     * @param detailed whether Shift is held, which adds the pack files
     */
    static MapHoverCard of(MapLabels.Label subject, MapLabels.Label region, MapHoverProbe.Details details,
                           RenderType type, boolean detailed, double worldX, double worldZ) {
        List<Section> sections = new ArrayList<>();
        if (details != null) {
            sections.add(terrainSection(details, type));
            addPickSection(sections, details);
        }
        sections.add(locationSection(worldX, worldZ, detailed));
        if (detailed) {
            addFilesSection(sections, subject, region);
        }
        return new MapHoverCard(title(subject, details), subtitle(region, details, type), sections,
                detailed ? null : "Shift for pack files · H highlights");
    }

    /**
     * Draws the card with its top left corner as close to the given point as fits inside the bounds.
     */
    void paint(Graphics2D canvas, int anchorX, int anchorY, int boundsWidth, int boundsHeight) {
        int padding = StudioTheme.scale(PADDING);
        int labelWidth = labelColumnWidth(canvas);
        int width = Math.max(headerWidth(canvas), labelWidth + StudioTheme.scale(COLUMN_GAP) + valueColumnWidth(canvas)) + padding * 2;
        int height = contentHeight(canvas) + padding * 2;
        int x = Math.max(padding, Math.min(boundsWidth - width - padding, anchorX));
        int y = Math.max(padding, Math.min(boundsHeight - height - padding, anchorY));
        StudioOverlay.drawCard(canvas, x, y, width, height);

        int left = x + padding;
        int top = y + padding;
        top = drawLine(canvas, title, StudioTheme.boldFont(), StudioTheme.TEXT, left, top);
        if (subtitle != null) {
            top = drawLine(canvas, subtitle, StudioTheme.smallFont(), StudioTheme.SECONDARY_TEXT, left,
                    top + StudioTheme.scale(SUBTITLE_GAP));
        }
        for (Section section : sections) {
            top += StudioTheme.scale(SECTION_GAP);
            canvas.setColor(StudioTheme.OVERLAY_BORDER);
            canvas.fillRect(left, top, width - padding * 2, 1);
            top += StudioTheme.scale(SECTION_GAP) / 2;
            top = drawLine(canvas, section.title().toUpperCase(Locale.ROOT), StudioTheme.sectionFont(), StudioTheme.MUTED_TEXT,
                    left, top) + StudioTheme.scale(SECTION_TITLE_GAP);
            for (Row row : section.rows()) {
                drawLine(canvas, row.label(), StudioTheme.font(), StudioTheme.SECONDARY_TEXT, left, top);
                top = drawLine(canvas, row.value(), StudioTheme.font(), StudioTheme.TEXT,
                        left + labelWidth + StudioTheme.scale(COLUMN_GAP), top) + StudioTheme.scale(ROW_GAP);
            }
        }
        if (footer != null) {
            drawLine(canvas, footer, StudioTheme.smallFont(), StudioTheme.MUTED_TEXT, left, top + StudioTheme.scale(SECTION_GAP));
        }
    }

    private int labelColumnWidth(Graphics2D canvas) {
        FontMetrics metrics = canvas.getFontMetrics(StudioTheme.font());
        return sections.stream().flatMap(section -> section.rows().stream())
                .mapToInt(row -> metrics.stringWidth(row.label())).max().orElse(0);
    }

    private int valueColumnWidth(Graphics2D canvas) {
        FontMetrics metrics = canvas.getFontMetrics(StudioTheme.font());
        return sections.stream().flatMap(section -> section.rows().stream())
                .mapToInt(row -> metrics.stringWidth(row.value())).max().orElse(0);
    }

    private int headerWidth(Graphics2D canvas) {
        int width = canvas.getFontMetrics(StudioTheme.boldFont()).stringWidth(title);
        FontMetrics small = canvas.getFontMetrics(StudioTheme.smallFont());
        if (subtitle != null) {
            width = Math.max(width, small.stringWidth(subtitle));
        }
        if (footer != null) {
            width = Math.max(width, small.stringWidth(footer));
        }
        return width;
    }

    private int contentHeight(Graphics2D canvas) {
        int small = canvas.getFontMetrics(StudioTheme.smallFont()).getHeight();
        int row = canvas.getFontMetrics(StudioTheme.font()).getHeight() + StudioTheme.scale(ROW_GAP);
        int sectionTitle = canvas.getFontMetrics(StudioTheme.sectionFont()).getHeight() + StudioTheme.scale(SECTION_TITLE_GAP);
        int height = canvas.getFontMetrics(StudioTheme.boldFont()).getHeight();
        if (subtitle != null) {
            height += StudioTheme.scale(SUBTITLE_GAP) + small;
        }
        for (Section section : sections) {
            height += StudioTheme.scale(SECTION_GAP) + StudioTheme.scale(SECTION_GAP) / 2 + sectionTitle + section.rows().size() * row;
        }
        if (footer != null) {
            height += StudioTheme.scale(SECTION_GAP) + small;
        }
        return height;
    }

    private static int drawLine(Graphics2D canvas, String text, Font font, Color color, int left, int top) {
        canvas.setFont(font);
        canvas.setColor(color);
        FontMetrics metrics = canvas.getFontMetrics();
        canvas.drawString(text, left, top + metrics.getAscent());
        return top + metrics.getHeight();
    }

    private static String title(MapLabels.Label subject, MapHoverProbe.Details details) {
        if (subject != null) {
            return subject.name();
        }
        if (details != null && details.surfaceBiome() != null) {
            return details.surfaceBiome().getName();
        }
        return "Drawing…";
    }

    // The region view's title already is the region, so only the kind of ground is left to say.
    private static String subtitle(MapLabels.Label region, MapHoverProbe.Details details, RenderType type) {
        List<String> parts = new ArrayList<>();
        if (type != RenderType.REGION) {
            String regionName = region != null ? region.name() : details != null ? details.region().getName() : null;
            if (regionName != null) {
                parts.add(regionName + " region");
            }
        }
        if (details != null && details.terrainKind() != null) {
            parts.add(details.terrainKind());
        }
        return parts.isEmpty() ? null : String.join(" · ", parts);
    }

    private static Section terrainSection(MapHoverProbe.Details details, RenderType type) {
        List<Row> rows = new ArrayList<>();
        long height = Math.round(details.height());
        rows.add(new Row("Height", height + " · " + heightAboveSea(details.height() - details.fluidHeight())));
        rows.add(new Row("Slope", Math.round(details.slopeDegrees()) + "°"));
        IrisBiome surface = details.surfaceBiome();
        IrisBiome cave = details.caveBiome();
        if (type == RenderType.CAVE_LAND && surface != null) {
            rows.add(new Row("Surface biome", surface.getName()));
        } else if (type != RenderType.CAVE_LAND && cave != null && cave != surface) {
            rows.add(new Row("Cave biome", cave.getName()));
        }
        if (details.vanillaBiome() != null) {
            rows.add(new Row("Minecraft", details.vanillaBiome().replace('_', ' ')));
        }
        return new Section("Terrain", rows);
    }

    private static void addPickSection(List<Section> sections, MapHoverProbe.Details details) {
        List<Row> rows = new ArrayList<>();
        MapHoverProbe.PickNoise noise = details.pickNoise();
        if (noise != null && noise.regionNoise() != null) {
            rows.add(new Row("Region noise", noiseText(noise.regionNoise())));
        }
        if (noise != null && noise.biomeType() != null) {
            String label = capitalize(noise.biomeType().name().toLowerCase(Locale.ROOT)) + " noise";
            rows.add(new Row(label, biomeNoiseText(noise, details.surfaceBiome())));
        }
        if (details.placement() != null) {
            rows.add(new Row("Height", placementText(details.placement())));
        }
        if (!rows.isEmpty()) {
            sections.add(new Section("Picked by", rows));
        }
    }

    private static String biomeNoiseText(MapHoverProbe.PickNoise noise, IrisBiome surface) {
        String value = noiseText(noise.biomeNoise());
        IrisBiome picked = noise.noisePick();
        if (picked == null) {
            return value;
        }
        if (surface == null || picked.getLoadKey().equals(surface.getLoadKey())) {
            return value + " → " + picked.getName();
        }
        return value + " → " + picked.getName() + ", child " + surface.getName() + " took over";
    }

    // How hybrid terrain chose the base biome; a child biome or a coast can still replace it afterwards.
    private static String placementText(HybridBiomeSelector.Choice choice) {
        String height = heightAboveSea(choice.placementHeight()) + " · ";
        if (choice.fallback()) {
            return height + "no range covers it, closest wins";
        }
        if (choice.rivalCount() == 0) {
            return height + "only biome in range";
        }
        return height + "beat " + choice.rivalCount() + (choice.rivalCount() == 1 ? " other" : " others") + " in range";
    }

    private static Section locationSection(double worldX, double worldZ, boolean detailed) {
        int blockX = MapCanvas.floorWorldCoordinate(worldX);
        int blockZ = MapCanvas.floorWorldCoordinate(worldZ);
        List<Row> rows = new ArrayList<>();
        rows.add(new Row("Block", blockX + ", " + blockZ));
        rows.add(new Row("Chunk", (blockX >> 4) + ", " + (blockZ >> 4)));
        if (detailed) {
            rows.add(new Row("Region file", "r." + (blockX >> 9) + "." + (blockZ >> 9) + ".mca"));
        }
        return new Section("Location", rows);
    }

    private static void addFilesSection(List<Section> sections, MapLabels.Label subject, MapLabels.Label region) {
        List<Row> rows = new ArrayList<>();
        addPackFile(rows, subject);
        if (subject != null && subject.kind() != MapLabels.Kind.REGION) {
            addPackFile(rows, region);
        }
        if (!rows.isEmpty()) {
            sections.add(new Section("Pack files", rows));
        }
    }

    // The key is the path under the type's folder, so folder, key and extension give the file within the pack.
    private static void addPackFile(List<Row> rows, MapLabels.Label label) {
        if (label == null || !(label.subject() instanceof IrisRegistrant registrant)) {
            return;
        }
        String kind = label.kind() == MapLabels.Kind.REGION ? "Region" : "Biome";
        File file = registrant.getLoadFile();
        String fileName = file == null ? "" : file.getName();
        int extensionStart = fileName.lastIndexOf('.');
        String extension = extensionStart < 0 ? "" : fileName.substring(extensionStart);
        rows.add(new Row(kind, registrant.getFolderName() + "/" + label.key() + extension));
    }

    private static String heightAboveSea(double height) {
        long rounded = Math.round(height);
        return rounded < 0 ? -rounded + " below sea" : rounded + " above sea";
    }

    private static String noiseText(double noise) {
        return String.format(Locale.ROOT, "%.3f", noise);
    }

    private static String capitalize(String text) {
        return Character.toUpperCase(text.charAt(0)) + text.substring(1);
    }

    record Section(String title, List<Row> rows) {
    }

    record Row(String label, String value) {
    }
}
