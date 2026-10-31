package art.arcane.iris.spi;

import java.util.List;
import java.util.Optional;

public interface VanillaDataSource {
    Optional<byte[]> read(String dataPath);

    List<String> list(String prefix);
}
