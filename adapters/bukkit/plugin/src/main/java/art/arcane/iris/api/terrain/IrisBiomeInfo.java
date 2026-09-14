package art.arcane.iris.api.terrain;

import java.util.List;

public record IrisBiomeInfo(
        String key,
        String name,
        String regionKey,
        String regionName,
        String derivativeKey,
        String vanillaDerivativeKey,
        String type,
        List<IrisCustomBiomeInfo> customDerivatives) {
    public IrisBiomeInfo {
        key = key == null ? "" : key;
        name = name == null ? "" : name;
        regionKey = regionKey == null ? "" : regionKey;
        regionName = regionName == null ? "" : regionName;
        derivativeKey = derivativeKey == null ? "" : derivativeKey;
        vanillaDerivativeKey = vanillaDerivativeKey == null ? "" : vanillaDerivativeKey;
        type = type == null ? "" : type;
        customDerivatives = List.copyOf(customDerivatives);
    }
}
