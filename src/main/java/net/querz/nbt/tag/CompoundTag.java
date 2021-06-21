package net.querz.nbt.tag;

import com.volmit.iris.Iris;
import com.volmit.iris.util.KMap;
import net.querz.io.MaxDepthIO;

import java.util.*;
import java.util.function.BiConsumer;

public class CompoundTag extends Tag<Map<String, Tag<?>>> implements Iterable<Map.Entry<String, Tag<?>>>, Comparable<CompoundTag>, MaxDepthIO {

	public static final byte ID = 10;

	public CompoundTag() {
		super(createEmptyValue());
	}

	@Override
	public byte getID() {
		return ID;
	}

	private static Map<String, Tag<?>> createEmptyValue() {
		return new KMap<>();
	}

	public int size() {
		return getValue().size();
	}

	public Tag<?> remove(String key) {
		return getValue().remove(key);
	}

	public void clear() {
		getValue().clear();
	}

	public boolean containsKey(String key) {
		return getValue().containsKey(key);
	}

	public boolean containsValue(Tag<?> value) {
		return getValue().containsValue(value);
	}

	public Collection<Tag<?>> values() {
		return getValue().values();
	}

	public Set<String> keySet() {
		return getValue().keySet();
	}

	public Set<Map.Entry<String, Tag<?>>> entrySet() {
		return new NonNullEntrySet<>(getValue().entrySet());
	}

	@Override
	public Iterator<Map.Entry<String, Tag<?>>> iterator() {
		return entrySet().iterator();
	}

	public void forEach(BiConsumer<String, Tag<?>> action) {
		getValue().forEach(action);
	}

	public <C extends Tag<?>> C get(String key, Class<C> type) {
		Tag<?> t = getValue().get(key);
		if (t != null) {
			return type.cast(t);
		}
		return null;
	}

	public Tag<?> get(String key) {
		return getValue().get(key);
	}

	public ByteTag getByteTag(String key) {
		return get(key, ByteTag.class);
	}

	public ShortTag getShortTag(String key) {
		return get(key, ShortTag.class);
	}

	public IntTag getIntTag(String key) {
		return get(key, IntTag.class);
	}

	public LongTag getLongTag(String key) {
		return get(key, LongTag.class);
	}

	public FloatTag getFloatTag(String key) {
		return get(key, FloatTag.class);
	}

	public DoubleTag getDoubleTag(String key) {
		return get(key, DoubleTag.class);
	}

	public StringTag getStringTag(String key) {
		return get(key, StringTag.class);
	}

	public ByteArrayTag getByteArrayTag(String key) {
		return get(key, ByteArrayTag.class);
	}

	public IntArrayTag getIntArrayTag(String key) {
		return get(key, IntArrayTag.class);
	}

	public LongArrayTag getLongArrayTag(String key) {
		return get(key, LongArrayTag.class);
	}

	public ListTag<?> getListTag(String key) {
		return get(key, ListTag.class);
	}

	public CompoundTag getCompoundTag(String key) {
		return get(key, CompoundTag.class);
	}

	public boolean getBoolean(String key) {
		Tag<?> t = get(key);
		return t instanceof ByteTag && ((ByteTag) t).asByte() > 0;
	}

	public byte getByte(String key) {
		ByteTag t = getByteTag(key);
		return t == null ? ByteTag.ZERO_VALUE : t.asByte();
	}

	public short getShort(String key) {
		ShortTag t = getShortTag(key);
		return t == null ? ShortTag.ZERO_VALUE : t.asShort();
	}

	public int getInt(String key) {
		IntTag t = getIntTag(key);
		return t == null ? IntTag.ZERO_VALUE : t.asInt();
	}

	public long getLong(String key) {
		LongTag t = getLongTag(key);
		return t == null ? LongTag.ZERO_VALUE : t.asLong();
	}

	public float getFloat(String key) {
		FloatTag t = getFloatTag(key);
		return t == null ? FloatTag.ZERO_VALUE : t.asFloat();
	}

	public double getDouble(String key) {
		DoubleTag t = getDoubleTag(key);
		return t == null ? DoubleTag.ZERO_VALUE : t.asDouble();
	}

	public String getString(String key) {
		StringTag t = getStringTag(key);
		return t == null ? StringTag.ZERO_VALUE : t.getValue();
	}

	public byte[] getByteArray(String key) {
		ByteArrayTag t = getByteArrayTag(key);
		return t == null ? ByteArrayTag.ZERO_VALUE : t.getValue();
	}

	public int[] getIntArray(String key) {
		IntArrayTag t = getIntArrayTag(key);
		return t == null ? IntArrayTag.ZERO_VALUE : t.getValue();
	}

	public long[] getLongArray(String key) {
		LongArrayTag t = getLongArrayTag(key);
		return t == null ? LongArrayTag.ZERO_VALUE : t.getValue();
	}

	public Tag<?> put(String key, Tag<?> tag) {
		return getValue().put(Objects.requireNonNull(key), Objects.requireNonNull(tag));
	}

	public Tag<?> putBoolean(String key, boolean value) {
		return put(key, new ByteTag(value));
	}

	public Tag<?> putByte(String key, byte value) {
		return put(key, new ByteTag(value));
	}

	public Tag<?> putShort(String key, short value) {
		return put(key, new ShortTag(value));
	}

	public Tag<?> putInt(String key, int value) {
		return put(key, new IntTag(value));
	}

	public Tag<?> putLong(String key, long value) {
		return put(key, new LongTag(value));
	}

	public Tag<?> putFloat(String key, float value) {
		return put(key, new FloatTag(value));
	}

	public Tag<?> putDouble(String key, double value) {
		return put(key, new DoubleTag(value));
	}

	public Tag<?> putString(String key, String value) {
		return put(key, new StringTag(value));
	}

	public Tag<?> putByteArray(String key, byte[] value) {
		return put(key, new ByteArrayTag(value));
	}

	public Tag<?> putIntArray(String key, int[] value) {
		return put(key, new IntArrayTag(value));
	}

	public Tag<?> putLongArray(String key, long[] value) {
		return put(key, new LongArrayTag(value));
	}

	@Override
	public String valueToString(int maxDepth) {
		StringBuilder sb = new StringBuilder("{");
		boolean first = true;
		for (Map.Entry<String, Tag<?>> e : getValue().entrySet()) {
			sb.append(first ? "" : ",")
					.append(escapeString(e.getKey(), false)).append(":")
					.append(e.getValue().toString(decrementMaxDepth(maxDepth)));
			first = false;
		}
		sb.append("}");
		return sb.toString();
	}

	@Override
	public boolean equals(Object other) {
		if (this == other) {
			return true;
		}
		if (!super.equals(other) || size() != ((CompoundTag) other).size()) {
			return false;
		}
		for (Map.Entry<String, Tag<?>> e : getValue().entrySet()) {
			Tag<?> v;
			if ((v = ((CompoundTag) other).get(e.getKey())) == null || !e.getValue().equals(v)) {
				return false;
			}
		}
		return true;
	}

	@Override
	public int compareTo(CompoundTag o) {
		return Integer.compare(size(), o.getValue().size());
	}

	@Override
	public CompoundTag clone() {
		CompoundTag copy = new CompoundTag();
		for (Map.Entry<String, Tag<?>> e : getValue().entrySet()) {
			copy.put(e.getKey(), e.getValue().clone());
		}
		return copy;
	}
}
