/*  Copyright (c) 2000-2006 hamcrest.org
 */
package org.hamcrest.core;

import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.hamcrest.Factory;


/**
 * Tests whether the value is an instance of a class.
 */
public class IsInstanceOf<T> implements Matcher<T> {
    private final Class<T> theClass;

    /**
     * Creates a new instance of IsInstanceOf
     *
     * @param theClass The predicate evaluates to true for instances of this class
     *                 or one of its subclasses.
     */
    public IsInstanceOf(Class<T> theClass) {
        this.theClass = theClass;
    }

    public boolean match(Object item) {
        return theClass.isInstance(item);
    }

    public void describeTo(Description description) {
        description.appendText("an instance of ")
                .appendText(theClass.getName());
    }

    @Factory
    public static <T> Matcher<T> isA(Class<T> instanceOf) {
        return new IsInstanceOf<T>(instanceOf);
    }

}
