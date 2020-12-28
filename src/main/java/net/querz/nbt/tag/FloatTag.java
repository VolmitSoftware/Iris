package net.querz.nbt.tag;

public class FloatTag extends NumberTag<Float> implements Comparable<FloatTag> {

	public static final byte ID = 5;
	public static final float ZERO_VALUE = 0.0F;

	public FloatTag() {
		super(ZERO_VALUE);
	}

	public FloatTag(float value) {
		super(value);
	}

	@Override
	public byte getID() {
		return ID;
	}

	public void setValue(float value) {
		super.setValue(value);
	}

	@Override
	public boolean equals(Object other) {
		return super.equals(other) && getValue().equals(((FloatTag) other).getValue());
	}

	@Override
	public int compareTo(FloatTag other) {
		return getValue().compareTo(other.getValue());
	}

	@Override
	public FloatTag clone() {
		return new FloatTag(getValue());
	}
}
