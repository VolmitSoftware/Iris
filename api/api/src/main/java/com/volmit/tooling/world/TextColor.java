package com.volmit.tooling.world;

/**
 * The sixteen named colours of Minecraft text, in the order of their formatting codes. Every platform shows them the same
 * way, so a tool picks a colour by name and the host maps it.
 */
public enum TextColor {
    BLACK('0'),
    DARK_BLUE('1'),
    DARK_GREEN('2'),
    DARK_AQUA('3'),
    DARK_RED('4'),
    DARK_PURPLE('5'),
    GOLD('6'),
    GRAY('7'),
    DARK_GRAY('8'),
    BLUE('9'),
    GREEN('a'),
    AQUA('b'),
    RED('c'),
    LIGHT_PURPLE('d'),
    YELLOW('e'),
    WHITE('f');

    private final char code;

    TextColor(char code) {
        this.code = code;
    }

    /** The character that follows the formatting sign in Minecraft's legacy codes, such as {@code 'a'} for green. */
    public char code() {
        return code;
    }
}
