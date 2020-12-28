package net.querz.nbt.tag;

public class ShortTag extends NumberTag<Short> implements Comparable<ShortTag> {

	public static final byte ID = 2;
	public static final short ZERO_VALUE = 0;

	public ShortTag() {
		super(ZERO_VALUE);
	}

	public ShortTag(short value) {
		super(value);
	}

	@Override
	public byte getID() {
		return ID;
	}

	public void setValue(short value) {
		super.setValue(value);
	}

	@Override
	public boolean equals(Object other) {
		return super.equals(other) && asShort() == ((ShortTag) other).asShort();
	}

	@Override
	public int compareTo(ShortTag other) {
		return getValue().compareTo(other.getValue());
	}

	@Override
	public ShortTag clone() {
		return new ShortTag(getValue());
	}
}
