package com.volmit.iris.engine.object;

import com.volmit.iris.engine.object.annotations.ArrayType;
import com.volmit.iris.engine.object.annotations.Desc;
import com.volmit.iris.util.collection.KList;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

@Accessors(chain = true)
@AllArgsConstructor
@NoArgsConstructor
@Desc("Tree growth override settings")
@Data
@EqualsAndHashCode(callSuper = false)
public class IrisTreeSettings {

    @Desc("Turn replacing on and off")
    boolean enabled = false;

    @Desc("Object picking modes")
    IrisTreeModes mode = IrisTreeModes.FIRST;

    @Desc("Tree override list")
    @ArrayType(min = 1, type = IrisTree.class)
    private KList<IrisTree> saplings = new KList<>();
}
