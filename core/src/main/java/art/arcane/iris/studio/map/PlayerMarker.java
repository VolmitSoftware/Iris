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

import art.arcane.iris.studio.view.GuiMarker;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;

/**
 * A player on the map: an arrow pointing the way they look, like a minimap's, with their name in a pill above it.
 * Blue with a white edge and a soft shadow, so it stands out on grass, sand and water alike.
 */
final class PlayerMarker {
    private static final Color ARROW_FILL = StudioTheme.ACCENT;
    private static final Color ARROW_EDGE = Color.WHITE;
    private static final Color ARROW_OUTLINE = new Color(0, 0, 0, 150);
    private static final Color SHADOW = new Color(0, 0, 0, 90);
    private static final double ARROW_HALF_LENGTH = 9.5D;
    private static final double ARROW_HALF_WIDTH = 7.5D;
    private static final double ARROW_NOTCH = 3.5D;
    private static final double ARROW_EDGE_WIDTH = 1.75D;
    private static final double ARROW_OUTLINE_WIDTH = 1D;
    private static final double SHADOW_OFFSET = 1.5D;
    private static final int LABEL_GAP = 5;
    private static final int LABEL_PADDING_X = 6;
    private static final int LABEL_PADDING_Y = 1;
    private static final double MINECRAFT_YAW_TO_SCREEN_DEGREES = 180D;

    private PlayerMarker() {
    }

    static void paint(Graphics2D graphics, int screenX, int screenY, GuiMarker player) {
        Graphics2D canvas = (Graphics2D) graphics.create();
        try {
            canvas.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            canvas.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
            canvas.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            paintArrow(canvas, screenX, screenY, player.yaw());
            paintLabel(canvas, screenX, screenY, player.label());
        } finally {
            canvas.dispose();
        }
    }

    private static void paintArrow(Graphics2D canvas, int screenX, int screenY, float yaw) {
        Path2D arrow = arrowFacing(yaw);
        canvas.setColor(SHADOW);
        canvas.fill(AffineTransform.getTranslateInstance(screenX, screenY + StudioTheme.scale(SHADOW_OFFSET)).createTransformedShape(arrow));
        Path2D placed = (Path2D) arrow.clone();
        placed.transform(AffineTransform.getTranslateInstance(screenX, screenY));
        // Strokes are centred on the outline, so each ring is drawn twice its width and the next one covers its inner half.
        drawRing(canvas, placed, ARROW_OUTLINE, ARROW_EDGE_WIDTH + ARROW_OUTLINE_WIDTH);
        drawRing(canvas, placed, ARROW_EDGE, ARROW_EDGE_WIDTH);
        canvas.setColor(ARROW_FILL);
        canvas.fill(placed);
    }

    private static void drawRing(Graphics2D canvas, Path2D arrow, Color color, double width) {
        canvas.setStroke(new BasicStroke((float) StudioTheme.scale(width * 2D), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        canvas.setColor(color);
        canvas.draw(arrow);
    }

    /**
     * The arrow around the origin. Minecraft's yaw 0 faces south, which is down on the map, while the arrow is drawn
     * pointing up, so it turns half a circle further than the yaw.
     */
    private static Path2D arrowFacing(float yaw) {
        double length = StudioTheme.scale(ARROW_HALF_LENGTH);
        double width = StudioTheme.scale(ARROW_HALF_WIDTH);
        double notch = StudioTheme.scale(ARROW_NOTCH);
        Path2D arrow = new Path2D.Double();
        arrow.moveTo(0D, -length);
        arrow.lineTo(width, length);
        arrow.lineTo(0D, length - notch);
        arrow.lineTo(-width, length);
        arrow.closePath();
        arrow.transform(AffineTransform.getRotateInstance(Math.toRadians(yaw + MINECRAFT_YAW_TO_SCREEN_DEGREES)));
        return arrow;
    }

    private static void paintLabel(Graphics2D canvas, int screenX, int screenY, String name) {
        canvas.setFont(StudioTheme.smallFont());
        FontMetrics metrics = canvas.getFontMetrics();
        int width = metrics.stringWidth(name) + StudioTheme.scale(LABEL_PADDING_X) * 2;
        int height = metrics.getHeight() + StudioTheme.scale(LABEL_PADDING_Y) * 2;
        int left = screenX - width / 2;
        double arrowReach = ARROW_HALF_LENGTH + ARROW_EDGE_WIDTH + ARROW_OUTLINE_WIDTH;
        int bottom = screenY - (int) Math.ceil(StudioTheme.scale(arrowReach)) - StudioTheme.scale(LABEL_GAP);
        int top = bottom - height;
        RoundRectangle2D pill = new RoundRectangle2D.Double(left + 0.5D, top + 0.5D, width - 1D, height - 1D, height, height);
        canvas.setColor(StudioTheme.OVERLAY_BACKGROUND);
        canvas.fill(pill);
        canvas.setStroke(new BasicStroke(1F));
        canvas.setColor(StudioTheme.OVERLAY_BORDER);
        canvas.draw(pill);
        canvas.setColor(StudioTheme.TEXT);
        canvas.drawString(name, left + StudioTheme.scale(LABEL_PADDING_X), top + StudioTheme.scale(LABEL_PADDING_Y) + metrics.getAscent());
    }
}
