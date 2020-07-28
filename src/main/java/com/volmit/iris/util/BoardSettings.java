package com.volmit.iris.util;

import lombok.Builder;
import lombok.Getter;

/**
 * @author Missionary (missionarymc@gmail.com)
 * @since 5/31/2018
 */
@Getter
@Builder
public class BoardSettings {

    private BoardProvider boardProvider;

    private ScoreDirection scoreDirection;

}
