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

package art.arcane.iris.pack.value;

import art.arcane.iris.generation.cache.AtomicCache;
import art.arcane.volmlib.util.documentation.Description;
import art.arcane.iris.pack.schema.annotation.MaxNumber;
import art.arcane.iris.pack.schema.annotation.MinNumber;
import art.arcane.iris.pack.schema.annotation.Snippet;
import art.arcane.iris.spi.IrisLogging;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

import java.awt.Color;

@Snippet("color")
@Accessors(chain = true)
@NoArgsConstructor
@Description("Represents a color")
@Data
public class IrisColor {
    private final transient AtomicCache<Color> color = new AtomicCache<>();
    @MaxNumber(7)
    @MinNumber(6)
    @Description("Pass in a 6 digit hexadecimal color to fill R G and B values. You can also include the # symbol, but it's not required.")
    private String hex = null;
    @MaxNumber(255)
    @MinNumber(0)
    @Description("Represents the red channel. Only define this if you are not defining the hex value.")
    private int red = 0;
    @MaxNumber(255)
    @MinNumber(0)
    @Description("Represents the green channel. Only define this if you are not defining the hex value.")
    private int green = 0;
    @MaxNumber(255)
    @MinNumber(0)
    @Description("Represents the blue channel. Only define this if you are not defining the hex value.")
    private int blue = 0;

    public static Color blend(Color... c) {
        if (c == null || c.length <= 0) {
            return null;
        }
        float ratio = 1f / ((float) c.length);

        int a = 0;
        int r = 0;
        int g = 0;
        int b = 0;

        for (Color value : c) {
            int rgb = value.getRGB();
            int a1 = (rgb >> 24 & 0xff);
            int r1 = ((rgb & 0xff0000) >> 16);
            int g1 = ((rgb & 0xff00) >> 8);
            int b1 = (rgb & 0xff);
            a += (a1 * ratio);
            r += (r1 * ratio);
            g += (g1 * ratio);
            b += (b1 * ratio);
        }

        return new Color(a << 24 | r << 16 | g << 8 | b);
    }

    public Color getColor() {
        return color.aquire(() -> {
            if (hex != null) {
                String v = (hex.startsWith("#") ? hex : "#" + hex).trim();
                try {
                    return Color.decode(v);
                } catch (Throwable e) {
                    IrisLogging.reportError(e);

                }
            }

            return new Color(red, green, blue);
        });
    }

    public int getAsRGB() {
        if (hex != null) {
            try {
                if (hex.startsWith("#")) hex = hex.substring(1);
                return Integer.parseInt(hex, 16);
            } catch (NumberFormatException e) {
                return 0;
            }
        }

        return red << 16 | green << 8 | blue;
    }
}
