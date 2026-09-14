package art.arcane.iris.api.terrain;

public record IrisCustomBiomeInfo(String id, String registryKey) {
    public IrisCustomBiomeInfo {
        id = id == null ? "" : id;
        registryKey = registryKey == null ? "" : registryKey;
    }
}
