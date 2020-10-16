package com.volmit.iris.util;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

import org.apache.commons.lang.Validate;
import org.bukkit.ChatColor;
import org.bukkit.Color;
import org.bukkit.DyeColor;

/**
 * Colors
 *
 * @author cyberpwn
 */
public enum C
{
	/**
	 * Represents black
	 */
	BLACK('0', 0x00)
	{
		@Override
		public net.md_5.bungee.api.ChatColor asBungee()
		{
			return net.md_5.bungee.api.ChatColor.BLACK;
		}
	},
	/**
	 * Represents dark blue
	 */
	DARK_BLUE('1', 0x1)
	{
		@Override
		public net.md_5.bungee.api.ChatColor asBungee()
		{
			return net.md_5.bungee.api.ChatColor.DARK_BLUE;
		}
	},
	/**
	 * Represents dark green
	 */
	DARK_GREEN('2', 0x2)
	{
		@Override
		public net.md_5.bungee.api.ChatColor asBungee()
		{
			return net.md_5.bungee.api.ChatColor.DARK_GREEN;
		}
	},
	/**
	 * Represents dark blue (aqua)
	 */
	DARK_AQUA('3', 0x3)
	{
		@Override
		public net.md_5.bungee.api.ChatColor asBungee()
		{
			return net.md_5.bungee.api.ChatColor.DARK_AQUA;
		}
	},
	/**
	 * Represents dark red
	 */
	DARK_RED('4', 0x4)
	{
		@Override
		public net.md_5.bungee.api.ChatColor asBungee()
		{
			return net.md_5.bungee.api.ChatColor.DARK_RED;
		}
	},
	/**
	 * Represents dark purple
	 */
	DARK_PURPLE('5', 0x5)
	{
		@Override
		public net.md_5.bungee.api.ChatColor asBungee()
		{
			return net.md_5.bungee.api.ChatColor.DARK_PURPLE;
		}
	},
	/**
	 * Represents gold
	 */
	GOLD('6', 0x6)
	{
		@Override
		public net.md_5.bungee.api.ChatColor asBungee()
		{
			return net.md_5.bungee.api.ChatColor.GOLD;
		}
	},
	/**
	 * Represents gray
	 */
	GRAY('7', 0x7)
	{
		@Override
		public net.md_5.bungee.api.ChatColor asBungee()
		{
			return net.md_5.bungee.api.ChatColor.GRAY;
		}
	},
	/**
	 * Represents dark gray
	 */
	DARK_GRAY('8', 0x8)
	{
		@Override
		public net.md_5.bungee.api.ChatColor asBungee()
		{
			return net.md_5.bungee.api.ChatColor.DARK_GRAY;
		}
	},
	/**
	 * Represents blue
	 */
	BLUE('9', 0x9)
	{
		@Override
		public net.md_5.bungee.api.ChatColor asBungee()
		{
			return net.md_5.bungee.api.ChatColor.BLUE;
		}
	},
	/**
	 * Represents green
	 */
	GREEN('a', 0xA)
	{
		@Override
		public net.md_5.bungee.api.ChatColor asBungee()
		{
			return net.md_5.bungee.api.ChatColor.GREEN;
		}
	},
	/**
	 * Represents aqua
	 */
	AQUA('b', 0xB)
	{
		@Override
		public net.md_5.bungee.api.ChatColor asBungee()
		{
			return net.md_5.bungee.api.ChatColor.AQUA;
		}
	},
	/**
	 * Represents red
	 */
	RED('c', 0xC)
	{
		@Override
		public net.md_5.bungee.api.ChatColor asBungee()
		{
			return net.md_5.bungee.api.ChatColor.RED;
		}
	},
	/**
	 * Represents light purple
	 */
	LIGHT_PURPLE('d', 0xD)
	{
		@Override
		public net.md_5.bungee.api.ChatColor asBungee()
		{
			return net.md_5.bungee.api.ChatColor.LIGHT_PURPLE;
		}
	},
	/**
	 * Represents yellow
	 */
	YELLOW('e', 0xE)
	{
		@Override
		public net.md_5.bungee.api.ChatColor asBungee()
		{
			return net.md_5.bungee.api.ChatColor.YELLOW;
		}
	},
	/**
	 * Represents white
	 */
	WHITE('f', 0xF)
	{
		@Override
		public net.md_5.bungee.api.ChatColor asBungee()
		{
			return net.md_5.bungee.api.ChatColor.WHITE;
		}
	},
	/**
	 * Represents magical characters that change around randomly
	 */
	MAGIC('k', 0x10, true)
	{
		@Override
		public net.md_5.bungee.api.ChatColor asBungee()
		{
			return net.md_5.bungee.api.ChatColor.MAGIC;
		}
	},
	/**
	 * Makes the text bold.
	 */
	BOLD('l', 0x11, true)
	{
		@Override
		public net.md_5.bungee.api.ChatColor asBungee()
		{
			return net.md_5.bungee.api.ChatColor.BOLD;
		}
	},
	/**
	 * Makes a line appear through the text.
	 */
	STRIKETHROUGH('m', 0x12, true)
	{
		@Override
		public net.md_5.bungee.api.ChatColor asBungee()
		{
			return net.md_5.bungee.api.ChatColor.STRIKETHROUGH;
		}
	},
	/**
	 * Makes the text appear underlined.
	 */
	UNDERLINE('n', 0x13, true)
	{
		@Override
		public net.md_5.bungee.api.ChatColor asBungee()
		{
			return net.md_5.bungee.api.ChatColor.UNDERLINE;
		}
	},
	/**
	 * Makes the text italic.
	 */
	ITALIC('o', 0x14, true)
	{
		@Override
		public net.md_5.bungee.api.ChatColor asBungee()
		{
			return net.md_5.bungee.api.ChatColor.ITALIC;
		}
	},

	/**
	 * Resets all previous chat colors or formats.
	 */
	RESET('r', 0x15)
	{
		@Override
		public net.md_5.bungee.api.ChatColor asBungee()
		{
			return net.md_5.bungee.api.ChatColor.RESET;
		}
	};

	/**
	 * The special character which prefixes all chat colour codes. Use this if you
	 * need to dynamically convert colour codes from your custom format.
	 */
	public static final char COLOR_CHAR = '\u00A7';
	private static final Pattern STRIP_COLOR_PATTERN = Pattern.compile("(?i)" + String.valueOf(COLOR_CHAR) + "[0-9A-FK-OR]");
	public final static C[] COLORCYCLE = new C[] {C.GOLD, C.YELLOW, C.GREEN, C.AQUA, C.LIGHT_PURPLE, C.AQUA, C.GREEN, C.YELLOW, C.GOLD, C.RED};
	private final static C[] COLORS = new C[] {C.BLACK, C.DARK_BLUE, C.DARK_GREEN, C.DARK_AQUA, C.DARK_RED, C.DARK_PURPLE, C.GOLD, C.GRAY, C.DARK_GRAY, C.BLUE, C.GREEN, C.AQUA, C.RED, C.LIGHT_PURPLE, C.YELLOW, C.WHITE};
	private final int intCode;
	private final char code;
	private final boolean isFormat;
	private final String toString;
	private final static Map<Integer, C> BY_ID = new HashMap<Integer, C>();
	private final static Map<Character, C> BY_CHAR = new HashMap<Character, C>();
	private final static Map<DyeColor, C> dyeChatMap = new HashMap<DyeColor, C>();
	private final static Map<C, String> chatHexMap = new HashMap<C, String>();
	private final static Map<DyeColor, String> dyeHexMap = new HashMap<DyeColor, String>();

	static
	{
		chatHexMap.put(C.BLACK, "#000");
		chatHexMap.put(C.DARK_BLUE, "#00a");
		chatHexMap.put(C.DARK_GREEN, "#0a0");
		chatHexMap.put(C.DARK_AQUA, "#0aa");
		chatHexMap.put(C.DARK_RED, "#a00");
		chatHexMap.put(C.DARK_PURPLE, "#a0a");
		chatHexMap.put(C.GOLD, "#fa0");
		chatHexMap.put(C.GRAY, "#999");
		chatHexMap.put(C.DARK_GRAY, "#555");
		chatHexMap.put(C.BLUE, "#55f");
		chatHexMap.put(C.GREEN, "#5c5");
		chatHexMap.put(C.AQUA, "#5cc");
		chatHexMap.put(C.RED, "#f55");
		chatHexMap.put(C.LIGHT_PURPLE, "#f5f");
		chatHexMap.put(C.YELLOW, "#cc5");
		chatHexMap.put(C.WHITE, "#aaa");
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

	private C(char code, int intCode)
	{
		this(code, intCode, false);
	}

	private C(char code, int intCode, boolean isFormat)
	{
		this.code = code;
		this.intCode = intCode;
		this.isFormat = isFormat;
		this.toString = new String(new char[] {COLOR_CHAR, code});
	}

	public net.md_5.bungee.api.ChatColor asBungee()
	{
		return net.md_5.bungee.api.ChatColor.RESET;
	};

	/**
	 * Gets the char value associated with this color
	 *
	 * @return A char value of this color code
	 */
	public char getChar()
	{
		return code;
	}

	@Override
	public String toString()
	{
		return toString;
	}

	/**
	 * get the dye color for the chatcolor
	 *
	 * @return
	 */
	public DyeColor dye()
	{
		return chatToDye(chatColor());
	}

	public String hex()
	{
		return chatToHex(chatColor());
	}

	/**
	 * Checks if this code is a format code as opposed to a color code.
	 *
	 * @return whether this ChatColor is a format code
	 */
	public boolean isFormat()
	{
		return isFormat;
	}

	/**
	 * Checks if this code is a color code as opposed to a format code.
	 *
	 * @return whether this ChatColor is a color code
	 */
	public boolean isColor()
	{
		return !isFormat && this != RESET;
	}

	/**
	 * Gets the color represented by the specified color code
	 *
	 * @param code
	 *            Code to check
	 * @return Associative {@link org.bukkit.ChatColor} with the given code, or null
	 *         if it doesn't exist
	 */
	public static C getByChar(char code)
	{
		try
		{
			return BY_CHAR.get(code);
		}

		catch(Exception e)
		{
			return C.WHITE;
		}
	}

	/**
	 * Gets the color represented by the specified color code
	 *
	 * @param code
	 *            Code to check
	 * @return Associative {@link org.bukkit.ChatColor} with the given code, or null
	 *         if it doesn't exist
	 */
	public static C getByChar(String code)
	{
		try
		{
			Validate.notNull(code, "Code cannot be null");
			Validate.isTrue(code.length() > 0, "Code must have at least one char");

			return BY_CHAR.get(code.charAt(0));
		}

		catch(Exception e)
		{
			return C.WHITE;
		}
	}

	/**
	 * Strips the given message of all color codes
	 *
	 * @param input
	 *            String to strip of color
	 * @return A copy of the input string, without any coloring
	 */
	public static String stripColor(final String input)
	{
		if(input == null)
		{
			return null;
		}

		return STRIP_COLOR_PATTERN.matcher(input).replaceAll("");
	}

	/**
	 * DyeColor to ChatColor
	 *
	 * @param dclr
	 *            the dye color
	 * @return the color
	 */
	public static C dyeToChat(DyeColor dclr)
	{
		if(dyeChatMap.containsKey(dclr))
		{
			return dyeChatMap.get(dclr);
		}

		return C.MAGIC;
	}

	public static DyeColor chatToDye(ChatColor color)
	{
		for(DyeColor i : dyeChatMap.keySet())
		{
			if(dyeChatMap.get(i).toString().equals(color.toString()))
			{
				return i;
			}
		}

		return DyeColor.BLACK;
	}

	@SuppressWarnings("unlikely-arg-type")
	public static String chatToHex(ChatColor clr)
	{
		if(chatHexMap.containsKey(clr))
		{
			return chatHexMap.get(clr);
		}

		return "#000";
	}

	public static String dyeToHex(DyeColor clr)
	{
		if(dyeHexMap.containsKey(clr))
		{
			return dyeHexMap.get(clr);
		}

		return "#000";
	}

	public static Color hexToColor(String hex)
	{
		if(hex.startsWith("#"))
		{
			hex = hex.substring(1);
		}

		if(hex.indexOf("x") != -1)
		{
			hex = hex.substring(hex.indexOf("x"));
		}

		if(hex.length() != 6 && hex.length() != 3)
		{
			return null;
		}
		int sz = hex.length() / 3, mult = 1 << ((2 - sz) * 4), x = 0;

		for(int i = 0, z = 0; z < hex.length(); ++i, z += sz)
		{
			x |= (mult * Integer.parseInt(hex.substring(z, z + sz), 16)) << (i * 8);
		}

		return Color.fromBGR(x & 0xffffff);
	}

	public static Color rgbToColor(String rgb)
	{
		String parts[] = rgb.split("[^0-9]+");
		if(parts.length < 3)
		{
			return null;
		}

		int x = 0, i;

		for(i = 0; i < 3; ++i)
		{
			x |= Integer.parseInt(parts[i]) << (i * 8);
		}

		return Color.fromBGR(x & 0xffffff);
	}

	public static String generateColorTable()
	{
		StringBuilder str = new StringBuilder();

		str.append("<table><tr><td>Chat Color</td><td>Color</td></tr>");

		for(Map.Entry<C, String> e : chatHexMap.entrySet())
		{
			str.append(String.format("<tr><td style='color: %2$s;'>%1$s</td>" + "<td style='color: %2$s;'>Test String</td></tr>", e.getKey().name(), e.getValue()));
		}

		str.append("</table>");
		str.append("<table><tr><td>Dye Color</td><td>Color</td></tr>");
		for(Map.Entry<DyeColor, String> e : dyeHexMap.entrySet())
		{
			str.append(String.format("<tr><td style='color: %2$s;'>%1$s</td>" + "<td style='color: %2$s;'>Test String</td></tr>", e.getKey().name(), e.getValue()));
		}

		str.append("</table>");

		return str.toString();
	}

	/**
	 * Get the ChatColor enum instance instead of C
	 *
	 * @return
	 */
	public ChatColor chatColor()
	{
		return ChatColor.getByChar(code);
	}

	/**
	 * Translates a string using an alternate color code character into a string
	 * that uses the internal ChatColor.COLOR_CODE color code character. The
	 * alternate color code character will only be replaced if it is immediately
	 * followed by 0-9, A-F, a-f, K-O, k-o, R or r.
	 *
	 * @param altColorChar
	 *            The alternate color code character to replace. Ex: {@literal &}
	 * @param textToTranslate
	 *            Text containing the alternate color code character.
	 * @return Text containing the ChatColor.COLOR_CODE color code character.
	 */
	public static String translateAlternateColorCodes(char altColorChar, String textToTranslate)
	{
		if(textToTranslate == null)
		{
			return null;
		}

		char[] b = textToTranslate.toCharArray();
		for(int i = 0; i < b.length - 1; i++)
		{
			if(b[i] == altColorChar && "0123456789AaBbCcDdEeFfKkLlMmNnOoRr".indexOf(b[i + 1]) > -1)
			{
				b[i] = C.COLOR_CHAR;
				b[i + 1] = Character.toLowerCase(b[i + 1]);
			}
		}
		return new String(b);
	}

	public static C fromItemMeta(byte c)
	{
		for(C i : C.values())
		{
			if(i.getItemMeta() == c)
			{
				return i;
			}
		}

		return null;
	}

	public byte getMeta()
	{
		switch(this)
		{
			case AQUA:
				return 11;
			case BLACK:
				return 0;
			case BLUE:
				return 9;
			case BOLD:
				return -1;
			case DARK_AQUA:
				return 9;
			case DARK_BLUE:
				return 1;
			case DARK_GRAY:
				return 8;
			case DARK_GREEN:
				return 2;
			case DARK_PURPLE:
				return 5;
			case DARK_RED:
				return 4;
			case GOLD:
				return 6;
			case GRAY:
				return 7;
			case GREEN:
				return 10;
			case ITALIC:
				return -1;
			case LIGHT_PURPLE:
				return 13;
			case MAGIC:
				return -1;
			case RED:
				return 12;
			case RESET:
				return -1;
			case STRIKETHROUGH:
				return -1;
			case UNDERLINE:
				return -1;
			case WHITE:
				return 15;
			case YELLOW:
				return 14;
			default:
				return -1;
		}
	}

	public byte getItemMeta()
	{
		switch(this)
		{
			case AQUA:
				return 9;
			case BLACK:
				return 15;
			case BLUE:
				return 3;
			case BOLD:
				return -1;
			case DARK_AQUA:
				return 9;
			case DARK_BLUE:
				return 11;
			case DARK_GRAY:
				return 7;
			case DARK_GREEN:
				return 13;
			case DARK_PURPLE:
				return 10;
			case DARK_RED:
				return 14;
			case GOLD:
				return 4;
			case GRAY:
				return 8;
			case GREEN:
				return 5;
			case ITALIC:
				return -1;
			case LIGHT_PURPLE:
				return 2;
			case MAGIC:
				return -1;
			case RED:
				return 14;
			case RESET:
				return -1;
			case STRIKETHROUGH:
				return -1;
			case UNDERLINE:
				return -1;
			case WHITE:
				return 0;
			case YELLOW:
				return 4;
			default:
				return -1;
		}
	}

	public static C randomColor()
	{
		return COLORS[(int) (Math.random() * (COLORS.length - 1))];
	}

	/**
	 * Gets the ChatColors used at the end of the given input string.
	 *
	 * @param input
	 *            Input string to retrieve the colors from.
	 * @return Any remaining ChatColors to pass onto the next line.
	 */
	public static String getLastColors(String input)
	{
		String result = "";
		int length = input.length();

		// Search backwards from the end as it is faster
		for(int index = length - 1; index > -1; index--)
		{
			char section = input.charAt(index);
			if(section == COLOR_CHAR && index < length - 1)
			{
				char c = input.charAt(index + 1);
				C color = getByChar(c);

				if(color != null)
				{
					result = color.toString() + result;

					// Once we find a color or reset we can stop searching
					if(color.isColor() || color.equals(RESET))
					{
						break;
					}
				}
			}
		}

		return result;
	}

	static
	{
		for(C color : values())
		{
			BY_ID.put(color.intCode, color);
			BY_CHAR.put(color.code, color);
		}
	}
}