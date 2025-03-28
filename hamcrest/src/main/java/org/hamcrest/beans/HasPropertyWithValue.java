package org.hamcrest.beans;

import org.hamcrest.Condition;
import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.hamcrest.TypeSafeDiagnosingMatcher;

import java.beans.FeatureDescriptor;
import java.beans.MethodDescriptor;
import java.beans.PropertyDescriptor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.ListIterator;

import static org.hamcrest.Condition.matched;
import static org.hamcrest.Condition.notMatched;
import static org.hamcrest.beans.PropertyUtil.NO_ARGUMENTS;

/**
 * <p>A matcher that checks if an object has a JavaBean property with the
 * specified name and an expected value. This is useful for when objects are
 * created within code under test and passed to a mock object, and you wish
 * to assert that the created object has certain properties.
 * </p>
 *
 * <h2>Example Usage</h2>
 * Consider the situation where we have a class representing a person, which
 * follows the basic JavaBean convention of having get() and possibly set()
 * methods for its properties:
 * <pre>{@code  public class Person {
 *   private String name;
 *   public Person(String person) {
 *     this.person = person;
 *   }
 *   public String getName() {
 *     return name;
 *   }
 * } }</pre>
 *
 * And that these person objects are generated within a piece of code under test
 * (a class named PersonGenerator). This object is sent to one of our mock objects
 * which overrides the PersonGenerationListener interface:
 * <pre>{@code  public interface PersonGenerationListener {
 *   public void personGenerated(Person person);
 * } }</pre>
 *
 * In order to check that the code under test generates a person with name
 * "Iain" we would do the following:
 * <pre>{@code  Mock personGenListenerMock = mock(PersonGenerationListener.class);
 * personGenListenerMock.expects(once()).method("personGenerated").with(and(isA(Person.class), hasProperty("Name", eq("Iain")));
 * PersonGenerationListener listener = (PersonGenerationListener)personGenListenerMock.proxy(); }</pre>
 *
 * <p>If an exception is thrown by the getter method for a property, the property
 * does not exist, is not readable, or a reflection related exception is thrown
 * when trying to invoke it then this is treated as an evaluation failure and
 * the matches method will return false.
 * </p>
 * <p>This matcher class will also work with JavaBean objects that have explicit
 * bean descriptions via an associated BeanInfo description class.
 * See <a href="https://docs.oracle.com/javase/8/docs/technotes/guides/beans/index.html">https://docs.oracle.com/javase/8/docs/technotes/guides/beans/index.html</a> for
 * more information on JavaBeans.
 * </p>
 *
 * @param <T> the Matcher type
 * @author Iain McGinniss
 * @author Nat Pryce
 * @author Steve Freeman
 * @author cristcost at github
 */
public class HasPropertyWithValue<T> extends TypeSafeDiagnosingMatcher<T> {

    private static final Condition.Step<FeatureDescriptor, Method> WITH_READ_METHOD = withReadMethod();
    private final String propertyName;
    private final Matcher<Object> valueMatcher;
    private final String messageFormat;

    /**
     * Constructor, best called from {@link #hasProperty(String, Matcher)} or
     * {@link #hasPropertyAtPath(String, Matcher)}.
     * @param propertyName the name of the property
     * @param valueMatcher matcher for the expected value
     */
    public HasPropertyWithValue(String propertyName, Matcher<?> valueMatcher) {
        this(propertyName, valueMatcher, " property '%s' ");
    }

    /**
     * Constructor, best called from {@link #hasProperty(String, Matcher)} or
     * {@link #hasPropertyAtPath(String, Matcher)}.
     * @param propertyName the name of the property
     * @param valueMatcher matcher for the expected value
     * @param messageFormat format string for the description
     */
    public HasPropertyWithValue(String propertyName, Matcher<?> valueMatcher, String messageFormat) {
        this.propertyName = propertyName;
        this.valueMatcher = nastyGenericsWorkaround(valueMatcher);
        this.messageFormat = messageFormat;
    }

    @Override
    public boolean matchesSafely(T bean, Description mismatch) {
        return propertyOn(bean, mismatch)
                  .and(WITH_READ_METHOD)
                  .and(withPropertyValue(bean))
                  .matching(valueMatcher, String.format(messageFormat, propertyName));
    }

    @Override
    public void describeTo(Description description) {
        description.appendText("hasProperty(").appendValue(propertyName).appendText(", ")
                   .appendDescriptionOf(valueMatcher).appendText(")");
    }

    private Condition<FeatureDescriptor> propertyOn(T bean, Description mismatch) {
        FeatureDescriptor property = PropertyUtil.getPropertyDescriptor(propertyName, bean);
        if (property == null) {
            property = PropertyUtil.getMethodDescriptor(propertyName, bean);
        }
        if (property == null) {
            mismatch.appendText("No property \"" + propertyName + "\"");
            return notMatched();
        }

        return matched(property, mismatch);
    }

    private Condition.Step<Method, Object> withPropertyValue(final T bean) {
        return (readMethod, mismatch) -> {
            try {
                return matched(readMethod.invoke(bean, NO_ARGUMENTS), mismatch);
            } catch (InvocationTargetException e) {
                mismatch
                  .appendText("Calling '")
                  .appendText(readMethod.toString())
                  .appendText("': ")
                  .appendValue(e.getTargetException().getMessage());
                return notMatched();
            } catch (Exception e) {
                throw new IllegalStateException(
                  "Calling: '" + readMethod + "' should not have thrown " + e);
            }
        };
    }

    @SuppressWarnings("unchecked")
    private static Matcher<Object> nastyGenericsWorkaround(Matcher<?> valueMatcher) {
        return (Matcher<Object>) valueMatcher;
    }

    private static Condition.Step<FeatureDescriptor, Method> withReadMethod() {
        return (property, mismatch) -> {
            final Method readMethod = property instanceof PropertyDescriptor ?
                    ((PropertyDescriptor) property).getReadMethod() :
                    (((MethodDescriptor) property).getMethod());
            if (null == readMethod || readMethod.getReturnType() == void.class) {
                mismatch.appendText("property \"" + property.getName() + "\" is not readable");
                return notMatched();
            }
            return matched(readMethod, mismatch);
        };
    }

    /**
     * Creates a matcher that matches when the examined object has a JavaBean property
     * with the specified name whose value satisfies the specified matcher.
     * For example:
     * <pre>assertThat(myBean, hasProperty("foo", equalTo("bar"))</pre>
     *
     * @param <T>
     *     the matcher type.
     * @param propertyName
     *     the name of the JavaBean property that examined beans should possess
     * @param valueMatcher
     *     a matcher for the value of the specified property of the examined bean
     * @return The matcher.
     */
    public static <T> Matcher<T> hasProperty(String propertyName, Matcher<?> valueMatcher) {
        return new HasPropertyWithValue<>(propertyName, valueMatcher);
    }

    /**
     * Creates a matcher that matches when the examined object is a graph of
     * JavaBean objects that can be navigated along the declared dot-separated path
     * and the final element of that path is a JavaBean property whose value satisfies the
     * specified matcher.
     * For example:
     * <pre>assertThat(myBean, hasProperty("foo.bar.baz", equalTo("a property value"))</pre>
     *
     * @param <T>
     *     the matcher type.
     * @param path
     *     the dot-separated path from the examined object to the JavaBean property
     * @param valueMatcher
     *     a matcher for the value of the specified property of the examined bean
     * @return The matcher.
     */
    public static <T> Matcher<T> hasPropertyAtPath(String path, Matcher<T> valueMatcher) {
        List<String> properties = Arrays.asList(path.split("\\."));
            ListIterator<String> iterator =
                properties.listIterator(properties.size());

            Matcher<T> ret = valueMatcher;
            while (iterator.hasPrevious()) {
                ret = new HasPropertyWithValue<>(iterator.previous(), ret, "%s.");
            }
            return ret;
    }

}
