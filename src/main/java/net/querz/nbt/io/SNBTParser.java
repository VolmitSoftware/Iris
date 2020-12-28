package net.querz.nbt.io;

import net.querz.io.MaxDepthIO;
import net.querz.nbt.tag.ArrayTag;
import net.querz.nbt.tag.ByteArrayTag;
import net.querz.nbt.tag.ByteTag;
import net.querz.nbt.tag.CompoundTag;
import net.querz.nbt.tag.DoubleTag;
import net.querz.nbt.tag.EndTag;
import net.querz.nbt.tag.FloatTag;
import net.querz.nbt.tag.IntArrayTag;
import net.querz.nbt.tag.IntTag;
import net.querz.nbt.tag.ListTag;
import net.querz.nbt.tag.LongArrayTag;
import net.querz.nbt.tag.LongTag;
import net.querz.nbt.tag.ShortTag;
import net.querz.nbt.tag.StringTag;
import net.querz.nbt.tag.Tag;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

public final class SNBTParser implements MaxDepthIO {

	private static final Pattern
			FLOAT_LITERAL_PATTERN = Pattern.compile("^[-+]?(?:\\d+\\.?|\\d*\\.\\d+)(?:e[-+]?\\d+)?f$", Pattern.CASE_INSENSITIVE),
			DOUBLE_LITERAL_PATTERN = Pattern.compile("^[-+]?(?:\\d+\\.?|\\d*\\.\\d+)(?:e[-+]?\\d+)?d$", Pattern.CASE_INSENSITIVE),
			DOUBLE_LITERAL_NO_SUFFIX_PATTERN = Pattern.compile("^[-+]?(?:\\d+\\.|\\d*\\.\\d+)(?:e[-+]?\\d+)?$", Pattern.CASE_INSENSITIVE),
			BYTE_LITERAL_PATTERN = Pattern.compile("^[-+]?\\d+b$", Pattern.CASE_INSENSITIVE),
			SHORT_LITERAL_PATTERN = Pattern.compile("^[-+]?\\d+s$", Pattern.CASE_INSENSITIVE),
			INT_LITERAL_PATTERN = Pattern.compile("^[-+]?\\d+$", Pattern.CASE_INSENSITIVE),
			LONG_LITERAL_PATTERN = Pattern.compile("^[-+]?\\d+l$", Pattern.CASE_INSENSITIVE),
			NUMBER_PATTERN = Pattern.compile("^[-+]?\\d+$");

	private StringPointer ptr;

	private SNBTParser(String string) {
		this.ptr = new StringPointer(string);
	}

	public static Tag<?> parse(String string, int maxDepth) throws ParseException {
		SNBTParser parser = new SNBTParser(string);
		Tag<?> tag = parser.parseAnything(maxDepth);
		parser.ptr.skipWhitespace();
		if (parser.ptr.hasNext()) {
			throw parser.ptr.parseException("invalid characters after end of snbt");
		}
		return tag;
	}

	public static Tag<?> parse(String string) throws ParseException {
		return parse(string, Tag.DEFAULT_MAX_DEPTH);
	}

	private Tag<?> parseAnything(int maxDepth) throws ParseException {
		ptr.skipWhitespace();
		switch (ptr.currentChar()) {
			case '{':
				return parseCompoundTag(maxDepth);
			case '[':
				if (ptr.hasCharsLeft(2) && ptr.lookAhead(1) != '"' && ptr.lookAhead(2) == ';') {
					return parseNumArray();
				}
				return parseListTag(maxDepth);
		}
		return parseStringOrLiteral();
	}

	private Tag<?> parseStringOrLiteral() throws ParseException {
		ptr.skipWhitespace();
		if (ptr.currentChar() == '"') {
			return new StringTag(ptr.parseQuotedString());
		}
		String s = ptr.parseSimpleString();
		if (s.isEmpty()) {
			throw new ParseException("expected non empty value");
		}
		if (FLOAT_LITERAL_PATTERN.matcher(s).matches()) {
			return new FloatTag(Float.parseFloat(s.substring(0, s.length() - 1)));
		} else if (BYTE_LITERAL_PATTERN.matcher(s).matches()) {
			try {
				return new ByteTag(Byte.parseByte(s.substring(0, s.length() - 1)));
			} catch (NumberFormatException ex) {
				throw ptr.parseException("byte not in range: \"" + s.substring(0, s.length() - 1) + "\"");
			}
		} else if (SHORT_LITERAL_PATTERN.matcher(s).matches()) {
			try {
				return new ShortTag(Short.parseShort(s.substring(0, s.length() - 1)));
			} catch (NumberFormatException ex) {
				throw ptr.parseException("short not in range: \"" + s.substring(0, s.length() - 1) + "\"");
			}
		} else if (LONG_LITERAL_PATTERN.matcher(s).matches()) {
			try {
				return new LongTag(Long.parseLong(s.substring(0, s.length() - 1)));
			} catch (NumberFormatException ex) {
				throw ptr.parseException("long not in range: \"" + s.substring(0, s.length() - 1) + "\"");
			}
		} else if (INT_LITERAL_PATTERN.matcher(s).matches()) {
			try {
				return new IntTag(Integer.parseInt(s));
			} catch (NumberFormatException ex) {
				throw ptr.parseException("int not in range: \"" + s.substring(0, s.length() - 1) + "\"");
			}
		} else if (DOUBLE_LITERAL_PATTERN.matcher(s).matches()) {
			return new DoubleTag(Double.parseDouble(s.substring(0, s.length() - 1)));
		} else if (DOUBLE_LITERAL_NO_SUFFIX_PATTERN.matcher(s).matches()) {
			return new DoubleTag(Double.parseDouble(s));
		} else if ("true".equalsIgnoreCase(s)) {
			return new ByteTag(true);
		} else if ("false".equalsIgnoreCase(s)) {
			return new ByteTag(false);
		}
		return new StringTag(s);
	}

	private CompoundTag parseCompoundTag(int maxDepth) throws ParseException {
		ptr.expectChar('{');

		CompoundTag compoundTag = new CompoundTag();

		ptr.skipWhitespace();
		while (ptr.hasNext() && ptr.currentChar() != '}') {
			ptr.skipWhitespace();
			String key = ptr.currentChar() == '"' ? ptr.parseQuotedString() : ptr.parseSimpleString();
			if (key.isEmpty()) {
				throw new ParseException("empty keys are not allowed");
			}
			ptr.expectChar(':');

			compoundTag.put(key, parseAnything(decrementMaxDepth(maxDepth)));

			if (!ptr.nextArrayElement()) {
				break;
			}
		}
		ptr.expectChar('}');
		return compoundTag;
	}

	private ListTag<?> parseListTag(int maxDepth) throws ParseException {
		ptr.expectChar('[');
		ptr.skipWhitespace();
		ListTag<?> list = ListTag.createUnchecked(EndTag.class);
		while (ptr.currentChar() != ']') {
			Tag<?> element = parseAnything(decrementMaxDepth(maxDepth));
			try {
				list.addUnchecked(element);
			} catch (IllegalArgumentException ex) {
				throw ptr.parseException(ex.getMessage());
			}
			if (!ptr.nextArrayElement()) {
				break;
			}
		}
		ptr.expectChar(']');
		return list;
	}

	private ArrayTag<?> parseNumArray() throws ParseException {
		ptr.expectChar('[');
		char arrayType = ptr.next();
		ptr.expectChar(';');
		ptr.skipWhitespace();
		switch (arrayType) {
			case 'B':
				return parseByteArrayTag();
			case 'I':
				return parseIntArrayTag();
			case 'L':
				return parseLongArrayTag();
		}
		throw new ParseException("invalid array type '" + arrayType + "'");
	}

	private ByteArrayTag parseByteArrayTag() throws ParseException {
		List<Byte> byteList = new ArrayList<>();
		while (ptr.currentChar() != ']') {
			String s = ptr.parseSimpleString();
			ptr.skipWhitespace();
			if (NUMBER_PATTERN.matcher(s).matches()) {
				try {
					byteList.add(Byte.parseByte(s));
				} catch (NumberFormatException ex) {
					throw ptr.parseException("byte not in range: \"" + s + "\"");
				}
			} else {
				throw ptr.parseException("invalid byte in ByteArrayTag: \"" + s + "\"");
			}
			if (!ptr.nextArrayElement()) {
				break;
			}
		}
		ptr.expectChar(']');
		byte[] bytes = new byte[byteList.size()];
		for (int i = 0; i < byteList.size(); i++) {
			bytes[i] = byteList.get(i);
		}
		return new ByteArrayTag(bytes);
	}

	private IntArrayTag parseIntArrayTag() throws ParseException {
		List<Integer> intList = new ArrayList<>();
		while (ptr.currentChar() != ']') {
			String s = ptr.parseSimpleString();
			ptr.skipWhitespace();
			if (NUMBER_PATTERN.matcher(s).matches()) {
				try {
					intList.add(Integer.parseInt(s));
				} catch (NumberFormatException ex) {
					throw ptr.parseException("int not in range: \"" + s + "\"");
				}
			} else {
				throw ptr.parseException("invalid int in IntArrayTag: \"" + s + "\"");
			}
			if (!ptr.nextArrayElement()) {
				break;
			}
		}
		ptr.expectChar(']');
		return new IntArrayTag(intList.stream().mapToInt(i -> i).toArray());
	}

	private LongArrayTag parseLongArrayTag() throws ParseException {
		List<Long> longList = new ArrayList<>();
		while (ptr.currentChar() != ']') {
			String s = ptr.parseSimpleString();
			ptr.skipWhitespace();
			if (NUMBER_PATTERN.matcher(s).matches()) {
				try {
					longList.add(Long.parseLong(s));
				} catch (NumberFormatException ex) {
					throw ptr.parseException("long not in range: \"" + s + "\"");
				}
			} else {
				throw ptr.parseException("invalid long in LongArrayTag: \"" + s + "\"");
			}
			if (!ptr.nextArrayElement()) {
				break;
			}
		}
		ptr.expectChar(']');
		return new LongArrayTag(longList.stream().mapToLong(l -> l).toArray());
	}
}
