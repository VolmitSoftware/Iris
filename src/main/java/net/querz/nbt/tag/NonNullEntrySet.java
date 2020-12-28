package net.querz.nbt.tag;

import java.util.Collection;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

/**
 * A decorator for the Set returned by CompoundTag#entrySet()
 * that disallows setting null values.
 * */
class NonNullEntrySet<K, V> implements Set<Map.Entry<K, V>> {

	private Set<Map.Entry<K, V>> set;

	NonNullEntrySet(Set<Map.Entry<K, V>> set) {
		this.set = set;
	}

	@Override
	public int size() {
		return set.size();
	}

	@Override
	public boolean isEmpty() {
		return set.isEmpty();
	}

	@Override
	public boolean contains(Object o) {
		return set.contains(o);
	}

	@Override
	public Iterator<Map.Entry<K, V>> iterator() {
		return new NonNullEntrySetIterator(set.iterator());
	}

	@Override
	public Object[] toArray() {
		return set.toArray();
	}

	@Override
	public <T> T[] toArray(T[] a) {
		return set.toArray(a);
	}

	@Override
	public boolean add(Map.Entry<K, V> kvEntry) {
		return set.add(kvEntry);
	}

	@Override
	public boolean remove(Object o) {
		return set.remove(o);
	}

	@Override
	public boolean containsAll(Collection<?> c) {
		return set.containsAll(c);
	}

	@Override
	public boolean addAll(Collection<? extends Map.Entry<K, V>> c) {
		return set.addAll(c);
	}

	@Override
	public boolean retainAll(Collection<?> c) {
		return set.retainAll(c);
	}

	@Override
	public boolean removeAll(Collection<?> c) {
		return set.removeAll(c);
	}

	@Override
	public void clear() {
		set.clear();
	}

	class NonNullEntrySetIterator implements Iterator<Map.Entry<K, V>> {

		private Iterator<Map.Entry<K, V>> iterator;

		NonNullEntrySetIterator(Iterator<Map.Entry<K, V>> iterator) {
			this.iterator = iterator;
		}

		@Override
		public boolean hasNext() {
			return iterator.hasNext();
		}

		@Override
		public Map.Entry<K, V> next() {
			return new NonNullEntry(iterator.next());
		}
	}

	class NonNullEntry implements Map.Entry<K, V> {

		private Map.Entry<K, V> entry;

		NonNullEntry(Map.Entry<K, V> entry) {
			this.entry = entry;
		}

		@Override
		public K getKey() {
			return entry.getKey();
		}

		@Override
		public V getValue() {
			return entry.getValue();
		}

		@Override
		public V setValue(V value) {
			if (value == null) {
				throw new NullPointerException(getClass().getSimpleName() + " does not allow setting null");
			}
			return entry.setValue(value);
		}

		@Override
		public boolean equals(Object o) {
			return entry.equals(o);
		}

		@Override
		public int hashCode() {
			return entry.hashCode();
		}
	}
}