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

package com.volmit.iris.util.format;

import com.volmit.iris.Iris;
import com.volmit.iris.util.plugin.VolmitSender;
import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.api.chat.TextComponent;
import org.apache.commons.lang.Validate;
import org.bukkit.ChatColor;
import org.bukkit.Color;
import org.bukkit.DyeColor;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Colors
 *
 * @author cyberpwn
 */
public enum C {
    /**
     * Represents black
     */
    BLACK('0', 0x00) {
        @Override
        public net.md_5.bungee.api.ChatColor asBungee() {
            return net.md_5.bungee.api.ChatColor.BLACK;
        }
    },
    /**
     * Represents dark blue
     */
    DARK_BLUE('1', 0x1) {
        @Override
        public net.md_5.bungee.api.ChatColor asBungee() {
            return net.md_5.bungee.api.ChatColor.DARK_BLUE;
        }
    },
    /**
     * Represents dark green
     */
    DARK_GREEN('2', 0x2) {
        @Override
        public net.md_5.bungee.api.ChatColor asBungee() {
            return net.md_5.bungee.api.ChatColor.DARK_GREEN;
        }
    },
    /**
     * Represents dark blue (aqua)
     */
    DARK_AQUA('3', 0x3) {
        @Override
        public net.md_5.bungee.api.ChatColor asBungee() {
            return net.md_5.bungee.api.ChatColor.DARK_AQUA;
        }
    },
    /**
     * Represents dark red
     */
    DARK_RED('4', 0x4) {
        @Override
        public net.md_5.bungee.api.ChatColor asBungee() {
            return net.md_5.bungee.api.ChatColor.DARK_RED;
        }
    },
    /**
     * Represents dark purple
     */
    DARK_PURPLE('5', 0x5) {
        @Override
        public net.md_5.bungee.api.ChatColor asBungee() {
            return net.md_5.bungee.api.ChatColor.DARK_PURPLE;
        }
    },
    /**
     * Represents gold
     */
    GOLD('6', 0x6) {
        @Override
        public net.md_5.bungee.api.ChatColor asBungee() {
            return net.md_5.bungee.api.ChatColor.GOLD;
        }
    },
    /**
     * Represents gray
     */
    GRAY('7', 0x7) {
        @Override
        public net.md_5.bungee.api.ChatColor asBungee() {
            return net.md_5.bungee.api.ChatColor.GRAY;
        }
    },
    /**
     * Represents dark gray
     */
    DARK_GRAY('8', 0x8) {
        @Override
        public net.md_5.bungee.api.ChatColor asBungee() {
            return net.md_5.bungee.api.ChatColor.DARK_GRAY;
        }
    },
    /**
     * Represents blue
     */
    BLUE('9', 0x9) {
        @Override
        public net.md_5.bungee.api.ChatColor asBungee() {
            return net.md_5.bungee.api.ChatColor.BLUE;
        }
    },
    /**
     * Represents green
     */
    GREEN('a', 0xA) {
        @Override
        public net.md_5.bungee.api.ChatColor asBungee() {
            return net.md_5.bungee.api.ChatColor.GREEN;
        }
    },

    IRIS("<#1bb19e>", 'a', 0xA) {
        @Override
        public net.md_5.bungee.api.ChatColor asBungee() {
            return net.md_5.bungee.api.ChatColor.GREEN;
        }
    },

    /**
     * Represents aqua
     */
    AQUA('b', 0xB) {
        @Override
        public net.md_5.bungee.api.ChatColor asBungee() {
            return net.md_5.bungee.api.ChatColor.AQUA;
        }
    },
    /**
     * Represents red
     */
    RED('c', 0xC) {
        @Override
        public net.md_5.bungee.api.ChatColor asBungee() {
            return net.md_5.bungee.api.ChatColor.RED;
        }
    },
    /**
     * Represents light purple
     */
    LIGHT_PURPLE('d', 0xD) {
        @Override
        public net.md_5.bungee.api.ChatColor asBungee() {
            return net.md_5.bungee.api.ChatColor.LIGHT_PURPLE;
        }
    },
    /**
     * Represents yellow
     */
    YELLOW('e', 0xE) {
        @Override
        public net.md_5.bungee.api.ChatColor asBungee() {
            return net.md_5.bungee.api.ChatColor.YELLOW;
        }
    },
    /**
     * Represents white
     */
    WHITE('f', 0xF) {
        @Override
        public net.md_5.bungee.api.ChatColor asBungee() {
            return net.md_5.bungee.api.ChatColor.WHITE;
        }
    },
    /**
     * Represents magical characters that change around randomly
     */
    MAGIC("<obf>", 'k', 0x10, true) {
        @Override
        public net.md_5.bungee.api.ChatColor asBungee() {
            return net.md_5.bungee.api.ChatColor.MAGIC;
        }
    },
    /**
     * Makes the text bold.
     */
    BOLD('l', 0x11, true) {
        @Override
        public net.md_5.bungee.api.ChatColor asBungee() {
            return net.md_5.bungee.api.ChatColor.BOLD;
        }
    },
    /**
     * Makes a line appear through the text.
     */
    STRIKETHROUGH('m', 0x12, true) {
        @Override
        public net.md_5.bungee.api.ChatColor asBungee() {
            return net.md_5.bungee.api.ChatColor.STRIKETHROUGH;
        }
    },
    /**
     * Makes the text appear underlined.
     */
    UNDERLINE("<underlined>", 'n', 0x13, true) {
        @Override
        public net.md_5.bungee.api.ChatColor asBungee() {
            return net.md_5.bungee.api.ChatColor.UNDERLINE;
        }
    },
    /**
     * Makes the text italic.
     */
    ITALIC('o', 0x14, true) {
        @Override
        public net.md_5.bungee.api.ChatColor asBungee() {
            return net.md_5.bungee.api.ChatColor.ITALIC;
        }
    },

    /**
     * Resets all previous chat colors or formats.
     */
    RESET('r', 0x15) {
        @Override
        public net.md_5.bungee.api.ChatColor asBungee() {
            return net.md_5.bungee.api.ChatColor.RESET;
        }
    },


    ;
    /**
     * The special character which prefixes all chat colour codes. Use this if you
     * need to dynamically convert colour codes from your custom format.
     */
    public static final char COLOR_CHAR = '\u00A7';
    public final static C[] COLORCYCLE = new C[]{C.GOLD, C.YELLOW, C.GREEN, C.AQUA, C.LIGHT_PURPLE, C.AQUA, C.GREEN, C.YELLOW, C.GOLD, C.RED};
    private static final Pattern STRIP_COLOR_PATTERN = Pattern.compile("(?i)" + COLOR_CHAR + "[0-9A-FK-OR]");
    private final static C[] COLORS = new C[]{C.BLACK, C.DARK_BLUE, C.DARK_GREEN, C.DARK_AQUA, C.DARK_RED, C.DARK_PURPLE, C.GOLD, C.GRAY, C.DARK_GRAY, C.BLUE, C.GREEN, C.AQUA, C.RED, C.LIGHT_PURPLE, C.YELLOW, C.WHITE};
    @SuppressWarnings("MismatchedQueryAndUpdateOfCollection")
    private final static Map<Integer, C> BY_ID = new HashMap<>();
    private final static Map<Character, C> BY_CHAR = new HashMap<>();
    private final static Map<DyeColor, C> dyeChatMap = new HashMap<>();
    private final static Map<C, String> chatHexMap = new HashMap<>();
    private final static Map<DyeColor, String> dyeHexMap = new HashMap<>();

    static {
        chatHexMap.put(C.BLACK, "#000000");
        chatHexMap.put(C.DARK_BLUE, "#0000AA");
        chatHexMap.put(C.IRIS, "#1bb19e");
        chatHexMap.put(C.DARK_GREEN, "#00AA00");
        chatHexMap.put(C.DARK_AQUA, "#00AAAA");
        chatHexMap.put(C.DARK_RED, "#AA0000");
        chatHexMap.put(C.DARK_PURPLE, "#AA00AA");
        chatHexMap.put(C.GOLD, "#FFAA00");
        chatHexMap.put(C.GRAY, "#AAAAAA");
        chatHexMap.put(C.DARK_GRAY, "#555555");
        chatHexMap.put(C.BLUE, "#5555FF");
        chatHexMap.put(C.GREEN, "#55FF55");
        chatHexMap.put(C.AQUA, "#55FFFF");
        chatHexMap.put(C.RED, "#FF5555");
        chatHexMap.put(C.LIGHT_PURPLE, "#FF55FF");
        chatHexMap.put(C.YELLOW, "#FFFF55");
        chatHexMap.put(C.WHITE, "#FFFFFF");
        dyeChatMap.put(DyeColor.BLACK, C.DARK_GRAY);
        dyeChatMap.put(DyeColor.BLUE, C.DARK_BLUE);
        dyeChatMap.put(DyeColor.BROWN, C.GOLD);
        dyeChatMap.put(DyeColor.CYAN, C.AQUA);
        dyeChatMap.put(DyeColor.GRAY, C.GRAY);
        dyeChatMap.put(DyeColor.GREEN, C.DARK_GREEN);
        dyeChatMap.put(DyeColor.LIGHT_BLUE, C.BLUE);
        dyeChatMap.put(DyeColor.LIME, C.GREEN);
        dyeChatMap.put(DyeColor.MAGENTA, C.LIGHT_PURPLE);
        dyeChatMap.put(DyeColor.ORANGE, C.GOLD);
        dyeChatMap.put(DyeColor.PINK, C.LIGHT_PURPLE);
        dyeChatMap.put(DyeColor.PURPLE, C.DARK_PURPLE);
        dyeChatMap.put(DyeColor.RED, C.RED);
        dyeChatMap.put(DyeColor.LIGHT_GRAY, C.GRAY);
        dyeChatMap.put(DyeColor.WHITE, C.WHITE);
        dyeChatMap.put(DyeColor.YELLOW, C.YELLOW);
        dyeHexMap.put(DyeColor.BLACK, "#181414");
        dyeHexMap.put(DyeColor.BLUE, "#253193");
        dyeHexMap.put(DyeColor.BROWN, "#56331c");
        dyeHexMap.put(DyeColor.CYAN, "#267191");
        dyeHexMap.put(DyeColor.GRAY, "#414141");
        dyeHexMap.put(DyeColor.GREEN, "#364b18");
        dyeHexMap.put(DyeColor.LIGHT_BLUE, "#6387d2");
        dyeHexMap.put(DyeColor.LIME, "#39ba2e");
        dyeHexMap.put(DyeColor.MAGENTA, "#be49c9");
        dyeHexMap.put(DyeColor.ORANGE, "#ea7e35");
        dyeHexMap.put(DyeColor.PINK, "#d98199");
        dyeHexMap.put(DyeColor.PURPLE, "#7e34bf");
        dyeHexMap.put(DyeColor.RED, "#9e2b27");
        dyeHexMap.put(DyeColor.LIGHT_GRAY, "#a0a7a7");
        dyeHexMap.put(DyeColor.WHITE, "#a4a4a4");
        dyeHexMap.put(DyeColor.YELLOW, "#c2b51c");
    }

    static {
        for (C color : values()) {
            BY_ID.put(color.intCode, color);
            BY_CHAR.put(color.code, color);
        }
    }

    private final int intCode;
    private final char code;
    private final String token;
    private final boolean isFormat;
    private final String toString;

    C(char code, int intCode) {
        this("^", code, intCode, false);
    }

    C(String token, char code, int intCode) {
        this(token, code, intCode, false);
    }

    C(char code, int intCode, boolean isFormat) {
        this("^", code, intCode, false);
    }

    C(String token, char code, int intCode, boolean isFormat) {
        this.code = code;
        this.token = token.equalsIgnoreCase("^") ? "<" + name().toLowerCase(Locale.ROOT) + ">" : token;
        this.intCode = intCode;
        this.isFormat = isFormat;
        this.toString = new String(new char[]{COLOR_CHAR, code});
    }

    public static float[] spin(float[] c, int shift) {
        return new float[]{spin(c[0], shift), spinc(c[1], shift), spinc(c[2], shift)};
    }

    public static float[] spin(float[] c, int a, int b, int d) {
        return new float[]{spin(c[0], a), spinc(c[1], b), spinc(c[2], d)};
    }

    public static float spin(float c, int shift) {
        float g = ((((int) Math.floor(c * 360)) + shift) % 360) / 360F;
        return g < 0 ? 1f - g : g;
    }

    public static float spinc(float c, int shift) {
        float g = ((((int) Math.floor(c * 255)) + shift)) / 255F;
        return Math.max(0f, Math.min(g, 1f));
    }

    public static java.awt.Color spin(java.awt.Color c, int h, int s, int b) {
        float[] hsb = java.awt.Color.RGBtoHSB(c.getRed(), c.getGreen(), c.getBlue(), null);
        hsb = spin(hsb, h, s, b);
        return java.awt.Color.getHSBColor(hsb[0], hsb[1], hsb[2]);
    }

    public static String spinToHex(C color, int h, int s, int b) {
        return "#" + Integer.toHexString(spin(color.awtColor(), h, s, b).getRGB()).substring(2);
    }

    public static String aura(String s, int hrad, int srad, int vrad) {
        return aura(s, hrad, srad, vrad, 0.3D);
    }

    public static String aura(String s, int hrad, int srad, int vrad, double pulse) {
        String msg = compress(s);
        StringBuilder b = new StringBuilder();
        boolean c = false;

        for (char i : msg.toCharArray()) {
            if (c) {
                c = false;

                C o = C.getByChar(i);

                if (hrad != 0 || srad != 0 || vrad != 0) {
                    if (pulse > 0) {
                        b.append(VolmitSender.pulse(spinToHex(o, hrad, srad, vrad), spinToHex(o, -hrad, -srad, -vrad), pulse));
                    } else {
                        b.append("<gradient:")
                                .append(spinToHex(o, hrad, srad, vrad))
                                .append(":")
                                .append(spinToHex(o, -hrad, -srad, -vrad))
                                .append(">");
                    }
                } else {
                    b.append(C.getByChar(i).token);
                }

                continue;
            }

            if (i == C.COLOR_CHAR) {
                c = true;
                continue;
            }

            b.append(i);
        }

        return b.toString();
    }

    public static String compress(String c) {
        return BaseComponent.toLegacyText(TextComponent.fromLegacyText(c));
    }

    /**
     * Gets the color represented by the specified color code
     *
     * @param code Code to check
     * @return Associative {@link org.bukkit.ChatColor} with the given code, or null
     * if it doesn't exist
     */
    public static C getByChar(char code) {
        try {
            C c = BY_CHAR.get(code);
            return c == null ? C.WHITE : c;
        } catch (Exception e) {
            Iris.reportError(e);
            return C.WHITE;
        }
    }

    /**
     * Gets the color represented by the specified color code
     *
     * @param code Code to check
     * @return Associative {@link org.bukkit.ChatColor} with the given code, or null
     * if it doesn't exist
     */
    public static C getByChar(String code) {
        try {
            Validate.notNull(code, "Code cannot be null");
            Validate.isTrue(code.length() > 0, "Code must have at least one char");

            return BY_CHAR.get(code.charAt(0));
        } catch (Exception e) {
            Iris.reportError(e);
            return C.WHITE;
        }
    }

    /**
     * Strips the given message of all color codes
     *
     * @param input String to strip of color
     * @return A copy of the input string, without any coloring
     */
    public static String stripColor(final String input) {
        if (input == null) {
            return null;
        }

        return STRIP_COLOR_PATTERN.matcher(input).replaceAll("");
    }

    /**
     * DyeColor to ChatColor
     *
     * @param dclr the dye color
     * @return the color
     */
    public static C dyeToChat(DyeColor dclr) {
        if (dyeChatMap.containsKey(dclr)) {
            return dyeChatMap.get(dclr);
        }

        return C.MAGIC;
    }

    public static DyeColor chatToDye(ChatColor color) {
        for (Map.Entry<DyeColor, C> entry : dyeChatMap.entrySet()) {
            if (entry.getValue().toString().equals(color.toString())) {
                return entry.getKey();
            }
        }

        return DyeColor.BLACK;
    }

    @SuppressWarnings("unlikely-arg-type")
    public static String chatToHex(C clr) {
        if (chatHexMap.containsKey(clr)) {
            return chatHexMap.get(clr);
        }

        return "#000000";
    }

    public static String dyeToHex(DyeColor clr) {
        if (dyeHexMap.containsKey(clr)) {
            return dyeHexMap.get(clr);
        }

        return "#000000";
    }

    public static Color hexToColor(String hex) {
        if (hex.startsWith("#")) {
            hex = hex.substring(1);
        }

        if (hex.contains("x")) {
            hex = hex.substring(hex.indexOf("x"));
        }

        if (hex.length() != 6 && hex.length() != 3) {
            return null;
        }
        int sz = hex.length() / 3, mult = 1 << ((2 - sz) * 4), x = 0;

        for (int i = 0, z = 0; z < hex.length(); ++i, z += sz) {
            x |= (mult * Integer.parseInt(hex.substring(z, z + sz), 16)) << (i * 8);
        }

        return Color.fromBGR(x & 0xffffff);
    }

    public static Color rgbToColor(String rgb) {
        String[] parts = rgb.split("[^0-9]+");
        if (parts.length < 3) {
            return null;
        }

        int x = 0, i;

        for (i = 0; i < 3; ++i) {
            x |= Integer.parseInt(parts[i]) << (i * 8);
        }

        return Color.fromBGR(x & 0xffffff);
    }

    public static String generateColorTable() {
        StringBuilder str = new StringBuilder();

        str.append("<table><tr><td>Chat Color</td><td>Color</td></tr>");

        for (Map.Entry<C, String> e : chatHexMap.entrySet()) {
            str.append(String.format("<tr><td style='color: %2$s;'>%1$s</td>" + "<td style='color: %2$s;'>Test String</td></tr>", e.getKey().name(), e.getValue()));
        }

        str.append("</table>");
        str.append("<table><tr><td>Dye Color</td><td>Color</td></tr>");
        for (Map.Entry<DyeColor, String> e : dyeHexMap.entrySet()) {
            str.append(String.format("<tr><td style='color: %2$s;'>%1$s</td>" + "<td style='color: %2$s;'>Test String</td></tr>", e.getKey().name(), e.getValue()));
        }

        str.append("</table>");

        return str.toString();
    }

    /**
     * Translates a string using an alternate color code character into a string
     * that uses the internal ChatColor.COLOR_CODE color code character. The
     * alternate color code character will only be replaced if it is immediately
     * followed by 0-9, A-F, a-f, K-O, k-o, R or r.
     *
     * @param altColorChar    The alternate color code character to replace. Ex: {@literal &}
     * @param textToTranslate Text containing the alternate color code character.
     * @return Text containing the ChatColor.COLOR_CODE color code character.
     */
    public static String translateAlternateColorCodes(char altColorChar, String textToTranslate) {
        if (textToTranslate == null) {
            return null;
        }

        char[] b = textToTranslate.toCharArray();
        for (int i = 0; i < b.length - 1; i++) {
            if (b[i] == altColorChar && "0123456789AaBbCcDdEeFfKkLlMmNnOoRr".indexOf(b[i + 1]) > -1) {
                b[i] = C.COLOR_CHAR;
                b[i + 1] = Character.toLowerCase(b[i + 1]);
            }
        }
        return new String(b);
    }

    public static C fromItemMeta(byte c) {
        for (C i : C.values()) {
            if (i.getItemMeta() == c) {
                return i;
            }
        }

        return null;
    }

    public static C randomColor() {
        return COLORS[(int) (Math.random() * (COLORS.length - 1))];
    }

    /**
     * Gets the ChatColors used at the end of the given input string.
     *
     * @param input Input string to retrieve the colors from.
     * @return Any remaining ChatColors to pass onto the next line.
     */
    public static String getLastColors(String input) {
        StringBuilder result = new StringBuilder();
        int length = input.length();

        // Search backwards from the end as it is faster
        for (int index = length - 1; index > -1; index--) {
            char section = input.charAt(index);
            if (section == COLOR_CHAR && index < length - 1) {
                char c = input.charAt(index + 1);
                C color = getByChar(c);

                if (color != null) {
                    result.insert(0, color);

                    // Once we find a color or reset we can stop searching
                    if (color.isColor() || color.equals(RESET)) {
                        break;
                    }
                }
            }
        }

        return result.toString();
    }

    public net.md_5.bungee.api.ChatColor asBungee() {
        return net.md_5.bungee.api.ChatColor.RESET;
    }

    /**
     * Gets the char value associated with this color
     *
     * @return A char value of this color code
     */
    public char getChar() {
        return code;
    }

    @Override
    public String toString() {
        return intCode == -1 ? token : toString;
    }

    /**
     * get the dye color for the chatcolor
     */
    public DyeColor dye() {
        return chatToDye(chatColor());
    }

    public String hex() {
        return chatToHex(this);
    }

    public java.awt.Color awtColor() {
        return java.awt.Color.decode(hex());
    }

    /**
     * Checks if this code is a format code as opposed to a color code.
     *
     * @return whether this ChatColor is a format code
     */
    public boolean isFormat() {
        return isFormat;
    }

    /**
     * Checks if this code is a color code as opposed to a format code.
     *
     * @return whether this ChatColor is a color code
     */
    public boolean isColor() {
        return !isFormat && this != RESET;
    }

    /**
     * Get the ChatColor enum instance instead of C
     */
    public ChatColor chatColor() {
        return ChatColor.getByChar(code);
    }

    public byte getMeta() {
        return switch (this) {
            case AQUA -> (byte) 11;
            case BLACK -> (byte) 0;
            case BLUE, DARK_AQUA -> (byte) 9;
            case BOLD, UNDERLINE, STRIKETHROUGH, RESET, MAGIC, ITALIC -> (byte) -1;
            case DARK_BLUE -> (byte) 1;
            case DARK_GRAY -> (byte) 8;
            case DARK_GREEN -> (byte) 2;
            case DARK_PURPLE -> (byte) 5;
            case DARK_RED -> (byte) 4;
            case GOLD -> (byte) 6;
            case GRAY -> (byte) 7;
            case GREEN -> (byte) 10;
            case LIGHT_PURPLE -> (byte) 13;
            case RED -> (byte) 12;
            case YELLOW -> (byte) 14;
            default -> (byte) 15;
        };
    }

    public byte getItemMeta() {
        return switch (this) {
            case AQUA, DARK_AQUA -> (byte) 9;
            case BLUE -> (byte) 3;
            case BOLD, UNDERLINE, RESET, STRIKETHROUGH, MAGIC, ITALIC -> (byte) -1;
            case DARK_BLUE -> (byte) 11;
            case DARK_GRAY -> (byte) 7;
            case DARK_GREEN -> (byte) 13;
            case DARK_PURPLE -> (byte) 10;
            case DARK_RED, RED -> (byte) 14;
            case GOLD, YELLOW -> (byte) 4;
            case GRAY -> (byte) 8;
            case GREEN -> (byte) 5;
            case LIGHT_PURPLE -> (byte) 2;
            case WHITE -> (byte) 0;
            default -> (byte) 15;
        };
    }
}