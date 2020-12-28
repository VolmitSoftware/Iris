package net.querz.nbt.tag;

public class LongTag extends NumberTag<Long> implements Comparable<LongTag> {

	public static final byte ID = 4;
	public static final long ZERO_VALUE = 0L;

	public LongTag() {
		super(ZERO_VALUE);
	}

	public LongTag(long value) {
		super(value);
	}

	@Override
	public byte getID() {
		return ID;
	}

	public void setValue(long value) {
		super.setValue(value);
	}

	@Override
	public boolean equals(Object other) {
		return super.equals(other) && asLong() == ((LongTag) other).asLong();
	}

	@Override
	public int compareTo(LongTag other) {
		return getValue().compareTo(other.getValue());
	}

	@Override
	public LongTag clone() {
		return new LongTag(getValue());
	}
}
