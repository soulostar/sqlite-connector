package com.soulostar.sqlite;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.Properties;

import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

public class SQLiteConnectorBuilderTest {
	
	@Rule
	public ExpectedException thrown = ExpectedException.none();
	
	private SQLiteConnectorBuilder builder;
	
	@BeforeClass
	public static void beforeClass() {
		RedirectedStderr.init();
	}
	
	@Before
	public void createBuilder() {
		builder = SQLiteConnectorBuilder.newBuilder();
	}

	@Test
	public void cannotCreateDatabases_buildsCorrectly() {
		SQLiteConnector connector = builder.cannotCreateDatabases().build();
		assertFalse("Connector should not be able to create databases", connector.canCreateDatabases);
	}

	@Test
	public void withSubprotocol_buildsCorrectly() {
		String builderSubprotocol = "sql";
		SQLiteConnector connector = builder.withSubprotocol(builderSubprotocol).build();
		assertEquals("Connector's subprotocol should be equal to builder's subprotocol",
				"jdbc:" + builderSubprotocol +":",
				connector.connectionStringPrefix);
	}
	
	@Test
	public void withSubprotocol_nullArgThrows() {
		thrown.expect(NullPointerException.class);
		builder.withSubprotocol(null);
	}
	
	@Test
	public void withSubprotocol_emptyArgThrows() {
		thrown.expect(IllegalArgumentException.class);
		builder.withSubprotocol("");
	}

	@Test
	public void withConnectionProperties_buildsCorrectly() {
		Properties properties = new Properties();
		properties.setProperty("junk", "some property value");
		properties.setProperty("more junk", "testing 123");
		SQLiteConnector connector = builder.withConnectionProperties(properties).build();
		assertEquals("Connector properties should be equal to builder properties", properties, connector.properties);
	}
	
	@Test
	public void withConnectionProperties_propertiesShouldBeCopiedOnBuild() {
		Properties properties = new Properties();
		SQLiteConnector connector = builder.withConnectionProperties(properties).build();
		assertTrue("Connectors should not be built with the same properties object reference as their builder",
				connector.properties != builder.properties);
	}
	
	@Test
	public void withConnectionProperties_nullArgThrows() {
		thrown.expect(NullPointerException.class);
		builder.withConnectionProperties(null);
	}

	@Test
	public void withConnectionCredentials_buildsCorrectly() {
		String builderUser = "myuser";
		String builderPassword = "password123";
		SQLiteConnector connector = builder.withConnectionCredentials(builderUser, builderPassword).build();
		assertEquals("Connector's user should be equal to builder's user", builderUser, connector.user);
		assertEquals("Connector's password should be equal to builder's password", builderPassword, connector.password);
	}
	
	@Test
	public void withConnectionCredentials_nullUserThrows() {
		thrown.expect(NullPointerException.class);
		builder.withConnectionCredentials(null, "testpass99");
	}
	
	@Test
	public void withConnectionCredentials_nullPasswordThrows() {
		thrown.expect(NullPointerException.class);
		builder.withConnectionCredentials("usertest@", null);
	}

	@Test
	public void withLockStripes_buildsCorrectly() {
		int builderLockStripes = 17;
		SQLiteConnector connector = builder.withLockStripes(builderLockStripes).build();
		// We cannot check for equality here.
		// We can only assert that the connector's lock size is greater than or equal to
		// the builder's lock size, because the Striped.lock() contract states that
		// the stripes argument is the MINIMUM number of stripes required.
		assertTrue("Connector's lock size should be greater than or equal to builder's lock stripe count",
				connector.locks.size() >= builderLockStripes);
	}

	@Ignore
	@Test
	public void testWithInitialCapacity() {
		fail("Implementation on hold: may switch to cache soon");
	}

	@Ignore
	@Test
	public void testWithLoadFactor() {
		fail("Implementation on hold: may switch to cache soon");
	}

	@Ignore
	@Test
	public void testWithConcurrencyLevel() {
		fail("Implementation on hold: may switch to cache soon");
	}

	@Test
	public void withLogging_buildsCorrectly() {
		SQLiteConnector connector = builder.withLogging(SQLiteConnectorBuilderTest.class).build();
		assertTrue("Connector logger should not be null if builder enabled logging", connector.logger != null);
	}

	@Test
	public void build_buildsWithDefaults() {
		SQLiteConnector connector = builder.build();
		assertTrue("Connector should match builder's default for: canCreateDatabases",
				connector.canCreateDatabases == builder.canCreateDatabases);
		assertEquals("Connector should match builder's default for: subprotocol",
				connector.connectionStringPrefix,
				"jdbc:" + builder.subprotocol + ":");
		assertEquals("Connector should match builder's default for: properties",
				connector.properties,
				builder.properties);
		assertEquals("Connector should match builder's default for: user", connector.user, builder.user);
		assertEquals("Connector should match builder's default for: password", connector.password, builder.password);
		assertTrue("Connector should match builder's default for: lockStripes",
				connector.locks.size() >= builder.lockStripes);
	}

}
