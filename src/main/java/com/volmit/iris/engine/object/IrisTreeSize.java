package com.volmit.iris.engine.object;

import com.volmit.iris.engine.object.annotations.Desc;
import com.volmit.iris.engine.object.annotations.Required;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

@Accessors(chain = true)
@NoArgsConstructor
@AllArgsConstructor
@Desc("Sapling override object picking options")
@Data
public class IrisTreeSize {

    @Required
    @Desc("The width of the sapling area")
    int width = 1;

    @Required
    @Desc("The depth of the sapling area")
    int depth = 1;

    /**
     * Does the size match
     *
     * @param size the size to check match
     * @return true if it matches (fits within width and depth)
     */
    public boolean doesMatch(IrisTreeSize size) {
        return (width == size.getWidth() && depth == size.getDepth()) || (depth == size.getWidth() && width == size.getDepth());
    }
}
