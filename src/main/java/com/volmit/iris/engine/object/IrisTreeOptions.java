package com.volmit.iris.engine.object;

import com.volmit.iris.engine.object.annotations.ArrayType;
import com.volmit.iris.engine.object.annotations.Desc;
import com.volmit.iris.util.collection.KList;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

@Accessors(chain = true)
@AllArgsConstructor
@NoArgsConstructor
@Desc("Tree replace options for this object placer")
@Data
public class IrisTreeOptions {

    @Desc("Toggles this object placer's tree overrides")
    private boolean enabled = false;

    @Desc("Tree overrides affected by these object placements")
    @ArrayType(min = 1, type = IrisTree.class)
    private KList<IrisTree> trees = new KList<>();
}
