package com.volmit.iris.scaffold.engine;

import com.volmit.iris.object.IrisObject;
import com.volmit.iris.object.IrisObjectPlacement;
import lombok.AllArgsConstructor;
import lombok.Data;

import javax.annotation.Nullable;

@Data
@AllArgsConstructor
public class PlacedObject {
    @Nullable
    private IrisObjectPlacement placement;
    @Nullable
    private IrisObject object;
    private int id;
    private int xx;
    private int zz;
}
