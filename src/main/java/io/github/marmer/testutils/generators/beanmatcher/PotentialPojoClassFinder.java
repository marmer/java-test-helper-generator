package io.github.marmer.testutils.generators.beanmatcher;

import java.util.List;


/**
 * A Finder for classes.
 *
 * @author  marmer
 * @date    18.06.2017
 */
public interface PotentialPojoClassFinder {

	/**
	 * Finds all classes in the given package and its sub package.
	 *
	 * @param   packageNames  the package names
	 *
	 * @return  the list of classes
	 */
	List<Class<?>> findClasses(String... packageNames);
}
