/*
 * Iris is a World Generator for Minecraft Bukkit Servers
 * Copyright (c) 2021 Arcane Arts (Volmit Software)
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

package com.volmit.iris.util.bsf;

/**
 * BSFDeclaredBeans are used internally by BSF to encapsulate information being
 * passed between a BSFManager and its various BSFEngines. Note that the
 * constructor is not public because this is not a public class.
 * 
 * @author  Matthew J. Duftler
 * @author  Sanjiva Weerawarana
 */
public class BSFDeclaredBean {
	public String name;
	public Object bean;
	public Class type;

	BSFDeclaredBean(String name, Object bean, Class type) {
		this.name = name;
		this.bean = bean;
		this.type = type;
	}
}
