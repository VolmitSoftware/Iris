package art.arcane.iris.generation.decoration;

import art.arcane.iris.pack.value.IrisRange;

import art.arcane.volmlib.util.collection.KList;

public final class IrisOreGeneratorBounds {
    public static final IrisOreGeneratorBounds EMPTY = new IrisOreGeneratorBounds(false, 0D, 0D);

    private final boolean hasOres;
    private final double min;
    private final double max;

    private IrisOreGeneratorBounds(boolean hasOres, double min, double max) {
        this.hasOres = hasOres;
        this.min = min;
        this.max = max;
    }

    public static IrisOreGeneratorBounds of(KList<IrisOreGenerator> ores) {
        if (ores == null || ores.isEmpty()) {
            return EMPTY;
        }

        double min = Double.POSITIVE_INFINITY;
        double max = Double.NEGATIVE_INFINITY;
        int oreCount = ores.size();
        for (int oreIndex = 0; oreIndex < oreCount; oreIndex++) {
            IrisOreGenerator oreGenerator = ores.get(oreIndex);
            IrisRange range = oreGenerator.getRange();
            min = Math.min(min, range.getMin());
            max = Math.max(max, range.getMax());
        }

        return new IrisOreGeneratorBounds(true, min, max);
    }

    public boolean hasOres() {
        return hasOres;
    }

    public boolean contains(int y) {
        return hasOres && y >= min && y <= max;
    }
}
