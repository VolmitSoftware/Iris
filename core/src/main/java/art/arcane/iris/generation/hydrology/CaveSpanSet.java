package art.arcane.iris.generation.hydrology;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

final class CaveSpanSet {
    private int[] pending;
    private int size;
    private List<CaveYSpan> normalized;

    CaveSpanSet() {
        this.pending = new int[4];
        this.normalized = null;
    }

    void add(int minimumY, int maximumY) {
        if (minimumY > maximumY) {
            return;
        }
        int requiredLength = Math.multiplyExact(size + 1, 2);
        if (requiredLength > pending.length) {
            pending = Arrays.copyOf(pending, Math.multiplyExact(pending.length, 2));
        }
        pending[size * 2] = minimumY;
        pending[size * 2 + 1] = maximumY;
        size++;
        normalized = null;
    }

    List<CaveYSpan> spans() {
        if (normalized != null) {
            return normalized;
        }
        sortPending();
        ArrayList<CaveYSpan> merged = new ArrayList<>(size);
        for (int index = 0; index < size; index++) {
            CaveYSpan span = new CaveYSpan(pending[index * 2], pending[index * 2 + 1]);
            if (merged.isEmpty()) {
                merged.add(span);
                continue;
            }
            CaveYSpan previous = merged.getLast();
            if ((long) span.minimumY() > (long) previous.maximumY() + 1L) {
                merged.add(span);
                continue;
            }
            merged.set(
                    merged.size() - 1,
                    new CaveYSpan(previous.minimumY(), Math.max(previous.maximumY(), span.maximumY()))
            );
        }
        normalized = List.copyOf(merged);
        return normalized;
    }

    private void sortPending() {
        for (int index = 1; index < size; index++) {
            int minimumY = pending[index * 2];
            int maximumY = pending[index * 2 + 1];
            int insertionIndex = index;
            while (insertionIndex > 0) {
                int previousMinimumY = pending[(insertionIndex - 1) * 2];
                int previousMaximumY = pending[(insertionIndex - 1) * 2 + 1];
                if (previousMinimumY < minimumY
                        || previousMinimumY == minimumY && previousMaximumY <= maximumY) {
                    break;
                }
                pending[insertionIndex * 2] = previousMinimumY;
                pending[insertionIndex * 2 + 1] = previousMaximumY;
                insertionIndex--;
            }
            pending[insertionIndex * 2] = minimumY;
            pending[insertionIndex * 2 + 1] = maximumY;
        }
    }

    boolean contains(int y) {
        List<CaveYSpan> spans = spans();
        int minimumIndex = 0;
        int maximumIndex = spans.size() - 1;
        while (minimumIndex <= maximumIndex) {
            int index = (minimumIndex + maximumIndex) >>> 1;
            CaveYSpan span = spans.get(index);
            if (y < span.minimumY()) {
                maximumIndex = index - 1;
            } else if (y > span.maximumY()) {
                minimumIndex = index + 1;
            } else {
                return true;
            }
        }
        return false;
    }

    long positionCount() {
        long count = 0L;
        for (CaveYSpan span : spans()) {
            count += (long) span.maximumY() - span.minimumY() + 1L;
        }
        return count;
    }
}
