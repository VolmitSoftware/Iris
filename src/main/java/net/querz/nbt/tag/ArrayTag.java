package net.querz.nbt.tag;

import java.lang.reflect.Array;

/**
 * ArrayTag is an abstract representation of any NBT array tag.
 * For implementations see {@link ByteArrayTag}, {@link IntArrayTag}, {@link LongArrayTag}.
 * @param <T> The array type.
 * */
public abstract class ArrayTag<T> extends Tag<T> {

	public ArrayTag(T value) {
		super(value);
		if (!value.getClass().isArray()) {
			throw new UnsupportedOperationException("type of array tag must be an array");
		}
	}

	public int length() {
		return Array.getLength(getValue());
	}

	@Override
	public T getValue() {
		return super.getValue();
	}

	@Override
	public void setValue(T value) {
		super.setValue(value);
	}

	@Override
	public String valueToString(int maxDepth) {
		return arrayToString("", "");
	}

	protected String arrayToString(String prefix, String suffix) {
		StringBuilder sb = new StringBuilder("[").append(prefix).append("".equals(prefix) ? "" : ";");
		for (int i = 0; i < length(); i++) {
			sb.append(i == 0 ? "" : ",").append(Array.get(getValue(), i)).append(suffix);
		}
		sb.append("]");
		return sb.toString();
	}
}
