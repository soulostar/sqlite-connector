package com.soulostar.sqlite;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

public class UtilsTest {

	@Rule
	public ExpectedException thrown = ExpectedException.none();
	
	@Test
	public void checkString_nullStrThrows() {
		thrown.expect(NullPointerException.class);
		Utils.checkString(null, "asdf");
	}
	
	@Test
	public void checkString_emptyStrThrows() {
		thrown.expect(IllegalArgumentException.class);
		Utils.checkString("", "asdf");
	}
	
	@Test
	public void checkString_nullNameDefaultsToString() {
		thrown.expectMessage("String is null.");
		Utils.checkString(null, null);
	}

}
