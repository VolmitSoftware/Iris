/*
 * Iris is a World Generator for Minecraft Bukkit Servers
 * Copyright (c) 2022 Arcane Arts (Volmit Software)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.volmit.iris.util.decree.annotations;

import com.volmit.iris.util.decree.DecreeOrigin;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.TYPE})
public @interface Decree {

    String DEFAULT_DESCRIPTION = "No Description Provided";

    /**
     * The name of this command, which is the Method's name by default
     */
    String name() default "";

    /**
     * Only allow if studio mode is enabled
     *
     * @return defaults to false
     */
    boolean studio() default false;

    /**
     * If the node's functions MUST be run in sync, set this to true.<br>
     * Defaults to false
     */
    boolean sync() default false;

    /**
     * The description of this command.<br>
     * Is {@link #DEFAULT_DESCRIPTION} by default
     */
    String description() default DEFAULT_DESCRIPTION;

    /**
     * The origin this command must come from.<br>
     * Must be elements of the {@link DecreeOrigin} enum<br>
     * By default, is {@link DecreeOrigin#BOTH}, meaning both console & player can send the command
     */
    DecreeOrigin origin() default DecreeOrigin.BOTH;

    /**
     * The aliases of this parameter (instead of just the {@link #name() name} (if specified) or Method Name (name of
     * method))<br>
     * Can be initialized as just a string (ex. "alias") or as an array (ex. {"alias1", "alias2"})<br>
     * If someone uses /plugin foo and you specify alias="f" here, /plugin f will do the exact same.
     */
    String[] aliases() default "";
}
