package art.arcane.iris.core.nms.container;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class Pair<A, B> {
    private A a;
    private B b;
}
