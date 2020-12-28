package net.querz.nbt.tag;

import java.util.Arrays;

public class ByteArrayTag extends ArrayTag<byte[]> implements Comparable<ByteArrayTag> {

	public static final byte ID = 7;
	public static final byte[] ZERO_VALUE = new byte[0];

	public ByteArrayTag() {
		super(ZERO_VALUE);
	}

	public ByteArrayTag(byte[] value) {
		super(value);
	}

	@Override
	public byte getID() {
		return ID;
	}

	@Override
	public boolean equals(Object other) {
		return super.equals(other) && Arrays.equals(getValue(), ((ByteArrayTag) other).getValue());
	}

	@Override
	public int hashCode() {
		return Arrays.hashCode(getValue());
	}

	@Override
	public int compareTo(ByteArrayTag other) {
		return Integer.compare(length(), other.length());
	}

	@Override
	public ByteArrayTag clone() {
		return new ByteArrayTag(Arrays.copyOf(getValue(), length()));
	}
}
