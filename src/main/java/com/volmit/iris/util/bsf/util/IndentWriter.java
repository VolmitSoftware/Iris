/*
 * Copyright 2004,2004 The Apache Software Foundation.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.volmit.iris.util.bsf.util;

import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.Writer;

/**
 * An <code>IndentWriter</code> object behaves the same as a
 * <code>PrintWriter</code> object, with the additional capability
 * of being able to print strings that are prepended with a specified
 * amount of spaces.
 *
 * @author Matthew J. Duftler
 */
public class IndentWriter extends PrintWriter
{
  /**
   * Forwards its arguments to the <code>PrintWriter</code> constructor
   * with the same signature.
   */
  public IndentWriter(OutputStream out)
  {
	super(out);
  }
  /**
   * Forwards its arguments to the <code>PrintWriter</code> constructor
   * with the same signature.
   */
  public IndentWriter(OutputStream out, boolean autoFlush)
  {
	super(out, autoFlush);
  }
  /**
   * Forwards its arguments to the <code>PrintWriter</code> constructor
   * with the same signature.
   */
  public IndentWriter(Writer out)
  {
	super(out);
  }
  /**
   * Forwards its arguments to the <code>PrintWriter</code> constructor
   * with the same signature.
   */
  public IndentWriter(Writer out, boolean autoFlush)
  {
	super(out, autoFlush);
  }
  /**
   * Print the text (indented the specified amount) without inserting a linefeed.
   *
   * @param numberOfSpaces the number of spaces to indent the text.
   * @param text the text to print.
   */
  public void print(int numberOfSpaces, String text)
  {
	super.print(StringUtils.getChars(numberOfSpaces, ' ') + text);
  }
  /**
   * Print the text (indented the specified amount) and insert a linefeed.
   *
   * @param numberOfSpaces the number of spaces to indent the text.
   * @param text the text to print.
   */
  public void println(int numberOfSpaces, String text)
  {
	super.println(StringUtils.getChars(numberOfSpaces, ' ') + text);
  }
}
