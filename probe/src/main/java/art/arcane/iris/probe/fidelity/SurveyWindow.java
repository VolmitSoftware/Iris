package art.arcane.iris.probe.fidelity;

public record SurveyWindow(int centerX, int centerZ, int size, int stride) {
    public SurveyWindow {
        if (stride < 1) {
            throw new IllegalArgumentException("Survey stride must be positive, got " + stride);
        }
        if (size < stride || size % stride != 0) {
            throw new IllegalArgumentException("Survey window " + size + " must be a positive multiple of the stride " + stride);
        }
    }

    public int perAxis() {
        return size / stride;
    }

    public long samples() {
        return (long) perAxis() * perAxis();
    }

    public int coordinate(int center, int index) {
        return center - size / 2 + stride / 2 + index * stride;
    }
}
