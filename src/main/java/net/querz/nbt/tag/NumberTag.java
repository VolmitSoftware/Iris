package net.querz.nbt.tag;

public abstract class NumberTag<T extends Number & Comparable<T>> extends Tag<T> {

	public NumberTag(T value) {
		super(value);
	}

	public byte asByte() {
		return getValue().byteValue();
	}

	public short asShort() {
		return getValue().shortValue();
	}

	public int asInt() {
		return getValue().intValue();
	}

	public long asLong() {
		return getValue().longValue();
	}

	public float asFloat() {
		return getValue().floatValue();
	}

	public double asDouble() {
		return getValue().doubleValue();
	}

	@Override
	public String valueToString(int maxDepth) {
		return getValue().toString();
	}
}
