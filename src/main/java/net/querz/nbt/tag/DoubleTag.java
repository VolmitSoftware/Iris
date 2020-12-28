package net.querz.nbt.tag;

public class DoubleTag extends NumberTag<Double> implements Comparable<DoubleTag> {

	public static final byte ID = 6;
	public static final double ZERO_VALUE = 0.0D;

	public DoubleTag() {
		super(ZERO_VALUE);
	}

	public DoubleTag(double value) {
		super(value);
	}

	@Override
	public byte getID() {
		return ID;
	}

	public void setValue(double value) {
		super.setValue(value);
	}

	@Override
	public boolean equals(Object other) {
		return super.equals(other) && getValue().equals(((DoubleTag) other).getValue());
	}

	@Override
	public int compareTo(DoubleTag other) {
		return getValue().compareTo(other.getValue());
	}

	@Override
	public DoubleTag clone() {
		return new DoubleTag(getValue());
	}
}
