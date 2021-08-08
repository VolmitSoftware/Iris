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
 * If something goes wrong while doing some scripting stuff, one of these
 * is thrown. The integer code indicates what's wrong and the message
 * may give more details. The reason one exception with multiple meanings
 * (via the code) [instead of multiple exception types] is used is due to
 * the interest to keep the run-time size small.
 * 
 * @author   Sanjiva Weerawarana
 */
public class BSFException extends Exception {
  public static final int REASON_INVALID_ARGUMENT = 0;
  public static final int REASON_IO_ERROR = 10;
  public static final int REASON_UNKNOWN_LANGUAGE = 20;
  public static final int REASON_EXECUTION_ERROR = 100;
  public static final int REASON_UNSUPPORTED_FEATURE = 499;
  public static final int REASON_OTHER_ERROR = 500;

  int reason;
  Throwable targetThrowable;

  public BSFException (int reason, String msg) {
	super (msg);
	this.reason = reason;
  }
  public BSFException (int reason, String msg, Throwable t) {
	this (reason, msg);
	targetThrowable = t;
  }
  public BSFException (String msg) {
	this (REASON_OTHER_ERROR, msg);
  }
  public int getReason () {
	return reason;
  }
  public Throwable getTargetException () {
	return targetThrowable;
  }
  public void printStackTrace () {
	if (targetThrowable != null) {
	  String msg = getMessage ();

	  if (msg != null && !msg.equals (targetThrowable.getMessage ())) {
		System.err.print (msg + ": ");
	  }

	  targetThrowable.printStackTrace ();
	} else {
	  super.printStackTrace ();
	}
  }
}
