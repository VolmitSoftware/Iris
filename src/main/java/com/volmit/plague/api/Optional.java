package com.volmit.plague.api;

import static java.lang.annotation.ElementType.*;
import static java.lang.annotation.RetentionPolicy.*;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

@Retention(RUNTIME)
@Target(PARAMETER)
public @interface Optional
{
	String defaultString() default "";

	int defaultInt() default 0;

	double defaultDouble() default 0;

	float defaultFloat() default 0;

	long defaultLong() default 0;

	boolean defaultBoolean() default false;
}
