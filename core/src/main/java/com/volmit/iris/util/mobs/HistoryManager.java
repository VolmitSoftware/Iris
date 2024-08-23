package com.volmit.iris.util.mobs;

import lombok.Data;

import java.util.Deque;
import java.util.concurrent.ConcurrentLinkedDeque;

public class HistoryManager<K, V, I> {
    private final Deque<HistoryEntry<K, V, I>> history = new ConcurrentLinkedDeque<>();
    private final long maxSize;

    public HistoryManager(long maxSize) {
        this.maxSize = maxSize;
    }

    public void addEntry(K key, V value, I irritation) {
        history.addFirst(new HistoryEntry<>(key, value, irritation));
        if (history.size() > maxSize) {
            history.removeLast();
        }
    }

    public HistoryEntry<K, V, I> getEntry(int index) {
        return history.stream().skip(index).findFirst().orElse(null);
    }

    public I get3rd() {
        return history.stream()
                .skip(2)
                .findFirst()
                .map(HistoryEntry::getExtra)
                .orElse(null);
    }

    @Data
    public static class HistoryEntry<K, V, I> {
        private final K key;
        private final V value;
        private final I extra;

        public HistoryEntry(K key, V value, I extra) {
            this.key = key;
            this.value = value;
            this.extra = extra;
        }
    }
}