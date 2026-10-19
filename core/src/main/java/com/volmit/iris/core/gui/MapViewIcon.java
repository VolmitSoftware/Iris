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

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.util.List;

/**
 * A small outline picture of what a map view shows, in the style of IntelliJ's new UI icons: thin single-colour
 * lines on a 16 by 16 grid, laid on half pixels so they stay crisp.
 */
final class MapViewIcon {
    private static final double GRID = 16D;
    private static final float LINE_WIDTH = 1F;
    private static final Color LINE_COLOR = new Color(0xCED0D6);

    private MapViewIcon() {
    }

    static void paint(Graphics2D graphics, MapView view, int x, int y, int size) {
        Graphics2D canvas = (Graphics2D) graphics.create();
        try {
            canvas.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            canvas.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
            canvas.translate(x, y);
            canvas.scale(size / GRID, size / GRID);
            canvas.setColor(LINE_COLOR);
            canvas.setStroke(new BasicStroke(LINE_WIDTH, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            for (Shape line : glyphOf(view)) {
                canvas.draw(line);
            }
        } finally {
            canvas.dispose();
        }
    }

    private static List<Shape> glyphOf(MapView view) {
        return switch (view) {
            case BIOMES -> leaf();
            case REGIONS -> foldedMap();
            case HEIGHT -> mountains();
            case CONTINENTS -> globe();
            case LAND_BIOMES -> tree();
            case SEA_BIOMES -> waves();
            case CAVE_BIOMES -> caveMouth();
            case PALETTES -> layers();
            case OBJECTS -> cube();
            case NOISE -> noiseWave();
        };
    }

    private static List<Shape> leaf() {
        Path2D blade = new Path2D.Double();
        blade.moveTo(3.5, 12.5);
        blade.curveTo(2.5, 6, 6.5, 2.5, 13.5, 2.5);
        blade.curveTo(13.5, 9.5, 10, 13.5, 3.5, 12.5);
        return List.of(blade, new Line2D.Double(3.5, 12.5, 9.5, 6.5));
    }

    private static List<Shape> foldedMap() {
        Path2D outline = new Path2D.Double();
        outline.moveTo(1.5, 3.5);
        outline.lineTo(5.5, 2.5);
        outline.lineTo(10.5, 3.5);
        outline.lineTo(14.5, 2.5);
        outline.lineTo(14.5, 12.5);
        outline.lineTo(10.5, 13.5);
        outline.lineTo(5.5, 12.5);
        outline.lineTo(1.5, 13.5);
        outline.closePath();
        return List.of(outline, new Line2D.Double(5.5, 2.5, 5.5, 12.5), new Line2D.Double(10.5, 3.5, 10.5, 13.5));
    }

    private static List<Shape> mountains() {
        Path2D ridge = new Path2D.Double();
        ridge.moveTo(1.5, 13.5);
        ridge.lineTo(6.5, 4.5);
        ridge.lineTo(9.5, 9.5);
        ridge.lineTo(11, 7.5);
        ridge.lineTo(14.5, 13.5);
        ridge.closePath();
        return List.of(ridge);
    }

    private static List<Shape> globe() {
        return List.of(
                new Ellipse2D.Double(1.5, 1.5, 13, 13),
                new Ellipse2D.Double(5, 1.5, 6, 13),
                new Line2D.Double(1.5, 8, 14.5, 8));
    }

    private static List<Shape> tree() {
        Path2D crown = new Path2D.Double();
        crown.moveTo(8, 1.5);
        crown.lineTo(13.5, 11.5);
        crown.lineTo(2.5, 11.5);
        crown.closePath();
        return List.of(crown, new Line2D.Double(8, 11.5, 8, 14.5));
    }

    private static List<Shape> waves() {
        return List.of(wave(5.5), wave(10.5));
    }

    private static Shape wave(double middleY) {
        Path2D wave = new Path2D.Double();
        wave.moveTo(1.5, middleY);
        wave.curveTo(3.7, middleY - 2.5, 5.8, middleY - 2.5, 8, middleY);
        wave.curveTo(10.2, middleY + 2.5, 12.3, middleY + 2.5, 14.5, middleY);
        return wave;
    }

    private static List<Shape> caveMouth() {
        Path2D arch = new Path2D.Double();
        arch.moveTo(3.5, 13.5);
        arch.lineTo(3.5, 8);
        arch.curveTo(3.5, 2, 12.5, 2, 12.5, 8);
        arch.lineTo(12.5, 13.5);
        return List.of(arch, new Line2D.Double(1.5, 13.5, 14.5, 13.5));
    }

    // Stacked sheets, like IntelliJ's layers icon.
    private static List<Shape> layers() {
        Path2D top = new Path2D.Double();
        top.moveTo(8, 1.5);
        top.lineTo(14.5, 5);
        top.lineTo(8, 8.5);
        top.lineTo(1.5, 5);
        top.closePath();
        return List.of(top, chevron(8.5), chevron(11.5));
    }

    private static Shape chevron(double sideY) {
        Path2D chevron = new Path2D.Double();
        chevron.moveTo(1.5, sideY);
        chevron.lineTo(8, sideY + 3.5);
        chevron.lineTo(14.5, sideY);
        return chevron;
    }

    private static List<Shape> cube() {
        Path2D outline = new Path2D.Double();
        outline.moveTo(8, 1.5);
        outline.lineTo(14, 4.5);
        outline.lineTo(14, 11.5);
        outline.lineTo(8, 14.5);
        outline.lineTo(2, 11.5);
        outline.lineTo(2, 4.5);
        outline.closePath();
        Path2D edges = new Path2D.Double();
        edges.moveTo(2, 4.5);
        edges.lineTo(8, 7.5);
        edges.lineTo(14, 4.5);
        edges.moveTo(8, 7.5);
        edges.lineTo(8, 14.5);
        return List.of(outline, edges);
    }

    private static List<Shape> noiseWave() {
        Path2D wave = new Path2D.Double();
        wave.moveTo(1.5, 9);
        wave.curveTo(2.8, 3, 4.6, 3, 5.8, 8);
        wave.curveTo(7, 13, 8.8, 13.5, 10, 7.5);
        wave.curveTo(11, 3.5, 13, 4.5, 14.5, 8.5);
        return List.of(wave);
    }
}
