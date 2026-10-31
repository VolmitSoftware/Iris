package art.arcane.iris.pack.datapack.export;

public interface WorldgenExportService {
    WorldgenExportResult export(WorldgenExportRequest request, WorldgenExportProgress progress);
}
