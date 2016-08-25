package com.soulostar.sqlite;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;

/**
 * A helper class to redirect stderr in order to allow capturing slf4j-simple's
 * logging output.
 * @author stang
 *
 */
public class RedirectedStderr {
	
	static final ByteArrayOutputStream bytesOut = new ByteArrayOutputStream();
	
	private static boolean redirected = false;
	
	private RedirectedStderr() {
		// prevent instantiation
	}

	/**
	 * Initializes any necessary system properties for slf4j-simple, and
	 * redirects standard error to a {@link ByteArrayOutputStream}.
	 * <p>
	 * <b>Note:</b> This method should be called in <code>@BeforeClass</code>
	 * methods of every test class that creates {@link SQLiteConnector}
	 * instances, to ensure proper <code>slf4j-simple</code> static
	 * initialization.
	 * <p>
	 * If this method has already been called previously, all subsequent calls
	 * are no-ops.
	 */
	static void init() {
		if (!redirected) {
			System.setProperty("org.slf4j.simpleLogger.defaultLogLevel", "trace");
			System.setErr(new PrintStream(bytesOut));
			redirected = true;
		}
	}
}
