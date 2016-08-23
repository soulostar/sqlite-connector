package com.soulostar.sqlite;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;

import org.junit.BeforeClass;
import org.junit.runner.RunWith;
import org.junit.runners.Suite;

@RunWith(Suite.class)
@Suite.SuiteClasses({
	UtilsTest.class,
	SQLiteConnectorBuilderTest.class,
	SQLiteConnectorTest.class
})

public class SQLiteConnectorTestSuite {
	
	static ByteArrayOutputStream bytesOut = new ByteArrayOutputStream();
	
	@BeforeClass
	public static void beforeClass() {
		// We do this at the suite level because slf4j-simple initializes 
		// the target stream for ALL LOGGERS to stderr during static initialization.
		// This means we need to redirect stderr before ANY test cases run,
		// in order to be able to capture its output.
		System.setProperty("org.slf4j.simpleLogger.defaultLogLevel", "trace");
		System.setErr(new PrintStream(bytesOut));
	}
}
