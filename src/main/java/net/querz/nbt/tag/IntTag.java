package net.querz.nbt.tag;

public class IntTag extends NumberTag<Integer> implements Comparable<IntTag> {

	public static final byte ID = 3;
	public static final int ZERO_VALUE = 0;

	public IntTag() {
		super(ZERO_VALUE);
	}

	public IntTag(int value) {
		super(value);
	}

	@Override
	public byte getID() {
		return ID;
	}

	public void setValue(int value) {
		super.setValue(value);
	}

	@Override
	public boolean equals(Object other) {
		return super.equals(other) && asInt() == ((IntTag) other).asInt();
	}

	@Override
	public int compareTo(IntTag other) {
		return getValue().compareTo(other.getValue());
	}

	@Override
	public IntTag clone() {
		return new IntTag(getValue());
	}
}
