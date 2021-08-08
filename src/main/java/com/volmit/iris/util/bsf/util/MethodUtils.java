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

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Enumeration;
import java.util.Vector;

/**
 * This file is a collection of reflection utilities for dealing with
 * methods and constructors.
 * 
 * @author   Sanjiva Weerawarana
 * @author   Joseph Kesselman
 */
public class MethodUtils {

  /** Internal Class for getEntryPoint(). Implements 15.11.2.2 MORE
	SPECIFIC rules.

	Retains a list of methods (already known to match the
	arguments). As each method is added, we check against past entries
	to determine which if any is "more specific" -- defined as having
	_all_ its arguments (not just a preponderance) be
	method-convertable into those of another. If such a relationship
	is found, the more-specific method is retained and the
	less-specific method is discarded. At the end, if this has yielded
	a single winner it is considered the Most Specific Method and
	hence the one that should be invoked.  Otherwise, a
	NoSuchMethodException is thrown.
	
	PERFORMANCE VERSUS ARCHITECTURE: Arguably, this should "have-a"
	Vector. But the code is 6% smaller, and possibly faster, if we
	code it as "is-a" Vector. Since it's an inner class, nobody's
	likely to abuse the privilage.
	
	Note: "Static" in the case of an inner class means "Does not
	reference instance data in the outer class", and is required since
	our caller is a static method. */
  private static class MoreSpecific
  extends Vector
  {
	/** Submit an entry-point to the list. May be discarded if a past
	  entry is more specific, or may cause others to be discarded it
	  if is more specific.

	  newEntry: Method or Constructor under consideration.
	  */
	void addItem (Object newEntry)
	{
	  if(size()==0)
		addElement(newEntry);
	  else
		{
		  Class[] newargs=entryGetParameterTypes(newEntry);
		  boolean keep=true;
		  for (Enumeration e = elements();
			   keep & e.hasMoreElements() ;
				)
			{
			  Object oldEntry=e.nextElement();
			  // CAVEAT: Implicit references to enclosing class!
			  Class[] oldargs=entryGetParameterTypes(oldEntry);
			  if(areMethodConvertable(oldargs,newargs))
				removeElement(oldEntry); // New more specific; discard old
			  else if(areMethodConvertable(newargs,oldargs))
				keep=false;     // Old more specific; discard new
			  // Else they're tied. Keep both and hope someone beats both.
			}
		  if(keep)
			addElement(newEntry);
		}
	}

	/** Obtain the single Most Specific entry-point. If there is no clear
	  winner, or if the list is empty, throw NoSuchMethodException.

	  Arguments describe the call we were hoping to resolve. They are
	  used to throw a nice verbose exception if something goes wrong.
	  */
	Object getMostSpecific(Class targetClass,String methodName,
						   Class[] argTypes,boolean isStaticReference)
		 throws NoSuchMethodException
	{
	  if(size()==1)
		return firstElement();
	  if(size()>1)
		{
		  StringBuffer buf=new StringBuffer();
		  Enumeration e=elements();
		  buf.append(e.nextElement());
		  while(e.hasMoreElements())
			buf.append(" and ").append(e.nextElement());
		  throw new NoSuchMethodException (callToString(targetClass,
														methodName,
														argTypes,
														isStaticReference)+
										   " is ambiguous. It matches "+
										   buf.toString());
		}
	  return null;
	}
  }

  /** Convenience method: Test an entire parameter-list/argument-list pair
	for isMethodConvertable(), qv. 
	*/
  static private boolean areMethodConvertable(Class[] parms,Class[] args)
  {
	if(parms.length!=args.length)
	  return false;
	
	for(int i=0;i<parms.length;++i)
	  if(!isMethodConvertable(parms[i],args[i]))
		return false;
	
	return true;
  }
  /** Internal subroutine for getEntryPoint(): Format arguments as a
	  string describing the function being searched for. Used in
	  verbose exceptions. */
  private static String callToString(Class targetClass,String methodName,
									Class[] argTypes,boolean isStaticReference)
  {
	StringBuffer buf = new StringBuffer();
	if(isStaticReference)
	  buf.append("static ");
	buf.append(StringUtils.getClassName(targetClass));
	if(methodName!=null)
	  buf.append(".").append(methodName);
	buf.append("(");
	if (argTypes != null && argTypes.length>0) {
	  if(false)
		{
		  // ????? Sanjiva has an ArrayToString method. Using it would
		  // save a few bytes, at cost of giving up some reusability.
		}
	  else
		{
		  buf.append(StringUtils.getClassName(argTypes[0]));
		  for (int i = 1; i < argTypes.length; i++) {
			buf.append(",").append(StringUtils.getClassName(argTypes[i]));
		  }
		}
	}
	else
	  buf.append("[none]");
	buf.append(")");
	return buf.toString();
  }
  /** Utility function: obtain common data from either Method or
	  Constructor. (In lieu of an EntryPoint interface.) */
  static int entryGetModifiers(Object entry)
  {
	return (entry instanceof Method)
	  ? ((Method)entry).getModifiers()
	  : ((Constructor)entry).getModifiers();
  }
  // The common lookup code would be much easier if Method and
  // Constructor shared an "EntryPoint" Interface. Unfortunately, even
  // though their APIs are almost identical, they don't. These calls
  // are a workaround...  at the cost of additional runtime overhead
  // and some extra bytecodes.
  //
  // (A JDK bug report has been submitted requesting that they add the
  // Interface; it would be easy, harmless, and useful.)

  /** Utility function: obtain common data from either Method or
	  Constructor. (In lieu of an EntryPoint interface.) */
  static String entryGetName(Object entry)
  {
	return (entry instanceof Method)
	  ? ((Method)entry).getName()
	  : ((Constructor)entry).getName();
  }
  /** Utility function: obtain common data from either Method or
	  Constructor. (In lieu of an EntryPoint interface.) */
  static Class[] entryGetParameterTypes(Object entry)
  {
	return (entry instanceof Method)
	  ? ((Method)entry).getParameterTypes()
	  : ((Constructor)entry).getParameterTypes();
  }
  /** Utility function: obtain common data from either Method or
	  Constructor. (In lieu of an EntryPoint interface.) */
  static String entryToString(Object entry)
  {
	return (entry instanceof Method)
	  ? ((Method)entry).toString()
	  : ((Constructor)entry).toString();
  }
  //////////////////////////////////////////////////////////////////////////

  /** Class.getConstructor() finds only the entry point (if any)
	_exactly_ matching the specified argument types. Our implmentation
	can decide between several imperfect matches, using the same
	search algorithm as the Java compiler.

	Note that all constructors are static by definition, so
	isStaticReference is true.

	@exception NoSuchMethodException if constructor not found.
	*/
  static public Constructor getConstructor(Class targetClass, Class[] argTypes)
	   throws SecurityException, NoSuchMethodException
  {
	return (Constructor) getEntryPoint(targetClass,null,argTypes,true);
  }
  //////////////////////////////////////////////////////////////////////////

  /**
   * Search for entry point, per  Java Language Spec 1.0
   * as amended, verified by comparison against compiler behavior.
   *
   * @param targetClass Class object for the class to be queried.
   * @param methodName  Name of method to invoke, or null for constructor.
   *                    Only Public methods will be accepted.
   * @param argTypes    Classes of intended arguments.  Note that primitives
   *                    must be specified via their TYPE equivalents, 
   *                    rather than as their wrapper classes -- Integer.TYPE
   *                    rather than Integer. "null" may be passed in as an
   *                    indication that you intend to invoke the method with
   *                    a literal null argument and therefore can accept
   *                    any object type in this position.
   * @param isStaticReference  If true, and if the target is a Class object,
   *                    only static methods will be accepted as valid matches.
   *
   * @return a Method or Constructor of the appropriate signature
   *
   * @exception SecurityException     if security violation
   * @exception NoSuchMethodException if no such method
   */
  static private Object getEntryPoint(Class targetClass,
									  String methodName,
									  Class[] argTypes,
									  boolean isStaticReference) 
	   throws SecurityException, NoSuchMethodException
  {
	// 15.11.1: OBTAIN STARTING CLASS FOR SEARCH
	Object m=null;
	
	// 15.11.2 DETERMINE ARGUMENT SIGNATURE
	// (Passed in as argTypes array.)
	
	// Shortcut: If an exact match exists, return it.
	try {
	  if(methodName!=null)
		{
		  m=targetClass.getMethod (methodName, argTypes);
		  if(isStaticReference &&
			 !Modifier.isStatic(entryGetModifiers(m)) )
			{
			  throw 
				new NoSuchMethodException (callToString (targetClass,
														 methodName,
														 argTypes,
														 isStaticReference)+
										   " resolved to instance " + m);
			}
		  return m;
		}
	  else
		return targetClass.getConstructor (argTypes);
		  
	} catch (NoSuchMethodException e) {
	  // no-args has no alternatives!
	  if(argTypes==null || argTypes.length==0)
	  {
		throw 
		  new NoSuchMethodException (callToString (targetClass,
												   methodName,
												   argTypes,
												   isStaticReference)+
									 " not found.");
	  }
	  // Else fall through.
	}
	
	// Well, _that_ didn't work. Time to search for the Most Specific
	// matching function. NOTE that conflicts are possible!
	
	// 15.11.2.1 ACCESSIBLE: We apparently need to gather from two
	// sources to be sure we have both instance and static methods.
	Object[] methods;
	if(methodName!=null)
	  {
		methods=targetClass.getMethods();
	  }
	else
	  {
		methods=targetClass.getConstructors();
	  }
	if(0==methods.length)
	  {
		throw new NoSuchMethodException("No methods!");
	  }

	MoreSpecific best=new MoreSpecific();
	for(int i=0;i<methods.length;++i)
	  {
		Object mi=methods[i];
		if (
			// 15.11.2.1 ACCESSIBLE: Method is public.
			Modifier.isPublic(entryGetModifiers(mi))
			&&
			// 15.11.2.1 APPLICABLE: Right method name (or c'tor)
			(methodName==null || entryGetName(mi).equals(methodName) )
			&&
			// 15.11.2.1 APPLICABLE: Parameters match arguments
			areMethodConvertable(entryGetParameterTypes(mi),argTypes)
			 )
		  // 15.11.2.2 MORE SPECIFIC displace less specific.
		  best.addItem(mi);
	  }

	// May throw NoSuchMethodException; we pass in info needed to
	// create a useful exception
	m=best.getMostSpecific(targetClass,methodName,argTypes,isStaticReference);
  
	// 15.11.3 APPROPRIATE: Class invocation can call only static
	// methods. Note that the defined order of evaluation permits a
	// call to be resolved to an inappropriate method and then
	// rejected, rather than finding the best of the appropriate
	// methods.
	//
	// Constructors are never static, so we don't test them.
	if(m==null)
	  {
		throw new NoSuchMethodException (callToString(targetClass,
													  methodName,
													  argTypes,
													  isStaticReference)+
										 " -- no signature match");
	  }

	if( methodName!=null &&
		isStaticReference &&
		!Modifier.isStatic(entryGetModifiers(m)) )
	  {
		throw new NoSuchMethodException (callToString(targetClass,
													  methodName,
													  argTypes,
													  isStaticReference)+
										 " resolved to instance: "+m);
	  }

	return m;
  }
  //////////////////////////////////////////////////////////////////////////

  /* Class.getMethod() finds only the entry point (if any) _exactly_
	matching the specified argument types. Our implmentation can
	decide between several imperfect matches, using the same search
	algorithm as the Java compiler.

	This version more closely resembles Class.getMethod() -- we always
	ask the Class for the method. It differs in testing for
	appropriateness before returning the method; if the query is
	being made via a static reference, only static methods will be
	found and returned. */
  static public Method getMethod(Class target,String methodName,
								 Class[] argTypes,boolean isStaticReference)
	   throws SecurityException, NoSuchMethodException
  {
	return (Method)getEntryPoint(target,methodName,argTypes,isStaticReference);
  }
  //////////////////////////////////////////////////////////////////////////

  /**
   * Class.getMethod() finds only the entry point (if any) _exactly_
   * matching the specified argument types. Our implmentation can
   * decide between several imperfect matches, using the same search
   * algorithm as the Java compiler.
   *
   * This version emulates the compiler behavior by allowing lookup to
   * be performed against either a class or an instance -- classname.foo()
   * must be a static method call, instance.foo() can invoke either static
   * or instance methods.
   *
   * @param target     object on which call is to be made
   * @param methodName name of method I'm lookin' for
   * @param argTypes   array of argument types of method
   *
   * @return the desired method
   *
   * @exception SecurityException     if security violation
   * @exception NoSuchMethodException if no such method
   */
  static public Method getMethod(Object target,String methodName,
								 Class[] argTypes)
	   throws SecurityException, NoSuchMethodException
  {
	boolean staticRef=target instanceof Class;
	return getMethod( staticRef ? (Class)target : target.getClass(),
					  methodName,argTypes,staticRef);
  }
  /** Determine whether a given type can accept assignments of another
	type. Note that class.isAssignable() is _not_ a complete test!
	(This method is not needed by getMethod() or getConstructor(), but
	is provided as a convenience for other users.)
	
	parm: The type given in the method's signature.
	arg: The type we want to pass in.

	Legal ASSIGNMENT CONVERSIONS (5.2) are METHOD CONVERSIONS (5.3)
	plus implicit narrowing of int to byte, short or char.  */
  static private boolean isAssignmentConvertable(Class parm,Class arg)
  {
	return
	  (arg.equals(Integer.TYPE) &&
	   (parm.equals(Byte.TYPE) ||
		parm.equals(Short.TYPE) ||
		parm.equals(Character.TYPE)
		 )
		) ||
	  isMethodConvertable(parm,arg);
  }
  /** Determine whether a given method parameter type can accept
	arguments of another type.

	parm: The type given in the method's signature.
	arg: The type we want to pass in.

	Legal METHOD CONVERSIONS (5.3) are Identity, Widening Primitive
	Conversion, or Widening Reference Conversion. NOTE that this is a
	subset of the legal ASSIGNMENT CONVERSIONS (5.2) -- in particular,
	we can't implicitly narrow int to byte, short or char.

	SPECIAL CASE: In order to permit invoking methods with literal
	"null" values, setting the arg Class to null will be taken as a
	request to match any Class type. POSSIBLE PROBLEM: This may match
	a primitive type, which really should not accept a null value... but
	I'm not sure how best to distinguish those, short of enumerating them
	*/
  static private boolean isMethodConvertable(Class parm, Class arg)
  {
	if (parm.equals(arg))       // If same class, short-circuit now!
	  return true;

	// Accept any type EXCEPT primitives (which can't have null values).
	if (arg == null)
	{
	  return !parm.isPrimitive();
	}

	// Arrays are convertable if their elements are convertable
	// ????? Does this have to be done before isAssignableFrom, or
	// does it successfully handle arrays of primatives?
	while(parm.isArray())
	  {
		if(!arg.isArray())
		  return false;         // Unequal array depth
		else
		  {
			parm=parm.getComponentType();
			arg=arg.getComponentType();
		  }
	  }
	if(arg.isArray())
	  return false;             // Unequal array depth
	
	// Despite its name, the 1.1.6 docs say that this function does
	// NOT return true for all legal ASSIGNMENT CONVERSIONS
	// (5.2):
	//   "Specifically, this method tests whether the type
	//   represented by the specified class can be converted
	//   to the type represented by this Class object via
	//   an identity conversion or via a widening reference
	//   conversion."
	if(parm.isAssignableFrom(arg))
	  return true;

	// That leaves us the Widening Primitives case. Four possibilities:
	// void (can only convert to void), boolean (can only convert to boolean),
	// numeric (which are sequenced) and char (which inserts itself into the
	// numerics by promoting to int or larger)

	if(parm.equals(Void.TYPE) || parm.equals(Boolean.TYPE) ||
	   arg.equals(Void.TYPE) || arg.equals(Boolean.TYPE))
	  return false;
	
	Class[] primTypes={ Character.TYPE, Byte.TYPE, Short.TYPE, Integer.TYPE,
						Long.TYPE, Float.TYPE, Double.TYPE };
	int parmscore,argscore;
	
	for(parmscore=0;parmscore<primTypes.length;++parmscore)
	  if (parm.equals(primTypes[parmscore]))
		break;
	if(parmscore>=primTypes.length)
	  return false;             // Off the end
	
	for(argscore=0;argscore<primTypes.length;++argscore)
	  if (arg.equals(primTypes[argscore]))
		break;
	if(argscore>=primTypes.length)
	  return false;             // Off the end
	
	// OK if ordered AND NOT char-to-smaller-than-int
	return (argscore<parmscore && (argscore!=0 || parmscore>2) );
  }
}
