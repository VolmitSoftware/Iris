package net.querz.nbt.tag;

import java.util.Arrays;

public class LongArrayTag extends ArrayTag<long[]> implements Comparable<LongArrayTag> {

	public static final byte ID = 12;
	public static final long[] ZERO_VALUE = new long[0];

	public LongArrayTag() {
		super(ZERO_VALUE);
	}

	public LongArrayTag(long[] value) {
		super(value);
	}

	@Override
	public byte getID() {
		return ID;
	}

	@Override
	public boolean equals(Object other) {
		return super.equals(other) && Arrays.equals(getValue(), ((LongArrayTag) other).getValue());
	}

	@Override
	public int hashCode() {
		return Arrays.hashCode(getValue());
	}

	@Override
	public int compareTo(LongArrayTag other) {
		return Integer.compare(length(), other.length());
	}

	@Override
	public LongArrayTag clone() {
		return new LongArrayTag(Arrays.copyOf(getValue(), length()));
	}
}
