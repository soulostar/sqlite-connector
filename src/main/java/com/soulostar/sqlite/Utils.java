package com.soulostar.sqlite;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

/**
 * A package-private utility class. Keep this class small and simple.
 * @author stang
 *
 */
class Utils {
	
	// prevent instantiation
	private Utils() {
	}
	
	/**
	 * Checks that a string is not null or empty.
	 * 
	 * @param str
	 *            - the string to check
	 * @param name
	 *            - the name of the string, for error message construction; for
	 *            example, <code>"Username"</code> will lead to an error message
	 *            that reads <code>"Username is null."</code> if the string is
	 *            null, or <code>"Username is empty."</code> if it's empty. Note
	 *            that this name should typically be capitalized. If this is null,
	 *            it will default to <code>"String"</code>.
	 * @throws NullPointerException
	 *             if <code>str</code> is null.
	 * @throws IllegalArgumentException
	 *             if <code>str</code> is empty
	 */
	static void checkString(String str, String name) {
		if (name == null) name = "String";
		checkNotNull(str, "%s is null.", name);
		checkArgument(!str.isEmpty(), "%s is empty.", name);
	}
}
