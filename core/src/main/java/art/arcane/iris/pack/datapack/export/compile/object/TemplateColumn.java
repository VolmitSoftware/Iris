package art.arcane.iris.pack.datapack.export.compile.object;

import art.arcane.iris.pack.datapack.export.TemplateVolume;

record TemplateColumn(int x, int z) {
    static TemplateColumn of(TemplateVolume volume, int templateX, int templateZ, int quarter) {
        return new TemplateColumn(templateX - volume.sizeX() / 2, templateZ - volume.sizeZ() / 2).turn(quarter);
    }

    TemplateColumn turn(int quarter) {
        return switch (quarter) {
            case 1 -> new TemplateColumn(z, -x);
            case 2 -> new TemplateColumn(-x, -z);
            case 3 -> new TemplateColumn(-z, x);
            default -> this;
        };
    }
}
