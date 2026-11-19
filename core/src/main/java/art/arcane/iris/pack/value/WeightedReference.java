package art.arcane.iris.pack.value;

import art.arcane.volmlib.util.collection.KList;

import java.util.List;

public interface WeightedReference {
    String getReferenceKey();

    int getWeight();

    boolean isWrittenAsObject();

    static boolean anyWrittenAsObject(List<? extends WeightedReference> entries) {
        for (WeightedReference entry : entries) {
            if (entry != null && entry.isWrittenAsObject()) {
                return true;
            }
        }
        return false;
    }

    static KList<String> keys(List<? extends WeightedReference> entries) {
        KList<String> keys = new KList<>();
        for (WeightedReference entry : entries) {
            if (entry != null) {
                keys.add(entry.getReferenceKey());
            }
        }
        return keys;
    }
}
