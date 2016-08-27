package com.soulostar.sqlite;

import static com.soulostar.sqlite.SQLiteConnector.IN_MEMORY_SUBNAME;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashSet;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.rules.TemporaryFolder;
import org.sqlite.SQLiteConfig;

public class SQLiteConnectorTest {
	
	@Rule
	public TemporaryFolder folder = new TemporaryFolder();
	
	@Rule
	public ExpectedException thrown = ExpectedException.none();
	
	private SQLiteConnector connector;
	private SQLiteConnectorBuilder builder;
	private String tempDbPath;

	@BeforeClass
	public static void beforeClass() throws ClassNotFoundException, IOException {
		Class.forName("org.sqlite.JDBC");
		RedirectedStderr.init();
	}
	
	@Before
	public void setTempDatabasePath() {
		tempDbPath = folder.getRoot().getAbsolutePath() + File.separator + "Test.db";
		builder = SQLiteConnectorBuilder.newBuilder();
	}
	
	@After
	public void deleteTestDatabase() throws IOException {
		Files.deleteIfExists(Paths.get(tempDbPath));
	}

	@Test
	public void getConnection_sameConnectionForInMemoryDatabase() throws SQLException, IOException {
		connector = builder.build();
		
		try (Connection conn = connector.getConnection(IN_MEMORY_SUBNAME)) {
			try (Connection conn1 = connector.getConnection(IN_MEMORY_SUBNAME)) {
				try (Connection conn2 = connector.getConnection(IN_MEMORY_SUBNAME)) {
					assertTrue(
							"Multiple requests for in-memory database connection "
									+ "should return the same connection object",
							conn == conn1 && conn1 == conn2);
				}
			}
		}
	}
	
	@Test
	public void getConnection_sameConnectionForSameFile() throws SQLException, IOException {
		connector = builder.build();

		try (Connection conn = connector.getConnection(tempDbPath)) {
			try (Connection conn1 = connector.getConnection(tempDbPath)) {
				try (Connection conn2 = connector.getConnection(tempDbPath)) {
					assertTrue(
							"Multiple requests for a connection to the same database "
									+ "file should return the same connection object",
							conn == conn1 && conn1 == conn2);
				}
			}	
		}
	}
	
	@Test
	public void getConnection_sameConnectionForEquivalentPaths() throws IOException, SQLException {
		connector = builder.build();
		
		// This test has to operate on a special temp directory other than
		// the TemporaryFolder, because we have to test relative paths in addition
		// to absolute/canonical paths.
		Path tmpDir = Paths.get("tmp");
		Path relative = Paths.get("tmp", "test.db");
		Path relative1 = Paths.get("tmp", "..", "tmp", "test.db");
		Path absolute = Paths.get("tmp", "..", "tmp", "test.db").toAbsolutePath();
		Path canonical = Paths.get("tmp", "test.db").toAbsolutePath();
		try {
			Files.createDirectory(tmpDir);
			try (Connection conn = connector.getConnection(relative.toString())) {
				try (Connection conn1 = connector.getConnection(relative1.toString())) {
					try (Connection conn2 = connector.getConnection(absolute.toString())) {
						try (Connection conn3 = connector.getConnection(canonical.toString())) {
							assertTrue(
									"Connection requests to a database using equivalent "
											+ "relative/absolute/canonical paths should "
											+ "return the same connection object",
									conn == conn1 && conn1 == conn2 && conn2 == conn3);
						}
					}
				}
			}
		} finally {
			Files.deleteIfExists(relative);
			Files.deleteIfExists(relative1);
			Files.deleteIfExists(absolute);
			Files.deleteIfExists(canonical);
			Files.deleteIfExists(tmpDir);
		}
	}
	
	@Test
	public void getConnection_sameConnectionForMultipleConcurrentThreads() throws InterruptedException, SQLException, IOException {
		connector = builder.build();
		
		AtomicInteger identicalConnections = new AtomicInteger();
		ExecutorService executor = Executors.newCachedThreadPool();
		int threadTestCount = 100;
		try (Connection conn = connector.getConnection(tempDbPath)) {
			for (int i = 0; i < threadTestCount; i++) {
				executor.execute(new IdenticalConnectionTest(identicalConnections, conn));				
			}
			executor.shutdown();
			executor.awaitTermination(10, TimeUnit.SECONDS);
		}
		assertTrue("Concurrent connections to the same database from different threads should return the same object",
				identicalConnections.get() == threadTestCount);
	}
	
	private class IdenticalConnectionTest implements Runnable {
		
		private final AtomicInteger identicalConnectionCounter;
		private final Connection connToCompare;
		
		private IdenticalConnectionTest(AtomicInteger identicalConnectionCounter, Connection connToCompare) {
			this.identicalConnectionCounter = identicalConnectionCounter;
			this.connToCompare = connToCompare;
		}

		@Override
		public void run() {
			try (Connection conn = connector.getConnection(tempDbPath)) {
				// Sleep for a short random time to encourage more concurrent connection requests
				Thread.sleep((long)(Math.random() * 1000));
				if (conn == connToCompare) {
					identicalConnectionCounter.incrementAndGet();
				}
			} catch (@SuppressWarnings("unused") SQLException | IOException | InterruptedException ex) {
			}
		}
		
	}
	
	@Test
	public void getConnection_differentConnectionForDifferentFiles() throws SQLException, IOException {
		connector = builder.build();
		
		try (Connection conn = connector.getConnection(tempDbPath)) {
			String otherTempDbPath = folder.getRoot().getAbsolutePath() + File.separator + "Test1.db";
			try (Connection conn1 = connector.getConnection(otherTempDbPath)) {
				assertFalse("Connections to different databases should not be identical", conn == conn1);
			} finally {
				Files.deleteIfExists(Paths.get(otherTempDbPath));
			}
		}
	}
	
	@Test
	public void getConnection_differentConnectionForSequentialRequests() throws SQLException, IOException {
		connector = builder.build();
		
		int requestCount = 100;
		Set<Connection> openedConnections = new HashSet<>();
		for (int i = 0; i < requestCount; i++) {
			try (Connection conn = connector.getConnection(tempDbPath)) {
				openedConnections.add(conn);
			}
		}			
		assertTrue("Sequential requests for a given database should return a new connection object each time",
				openedConnections.size() == requestCount);
	}
	
	@Test
	public void getConnection_createsDatabaseByDefault() throws SQLException, IOException {
		connector = builder.build();

		Path db = Paths.get(tempDbPath);
		assertTrue("Test database should not exist before getting connection", Files.notExists(db));	
		try (Connection conn = connector.getConnection(tempDbPath)) {
		}	
		assertTrue("Test database should have been created by getting connection", Files.exists(db));
	}
	
	@Test
	public void getConnection_throwsWhenCreateIsOff() throws SQLException, IOException {
		connector = builder.cannotCreateDatabases().build();

		thrown.expect(FileNotFoundException.class);
		try (Connection conn = connector.getConnection(tempDbPath)) {
		}
	}
	
	@Test
	public void getConnection_logsWhenLoggingIsOn() throws SQLException, IOException {
		connector = builder.withLogging(SQLiteConnectorTest.class).build();
		
		assertTrue("Logging should occur when connector is configured to log.", connectorDoesLog());
	}
	
	@Test
	public void getConnection_doesNotLogByDefault() throws SQLException, IOException {
		connector = builder.build();
		
		assertFalse("Logging should not occur by default.", connectorDoesLog());
	}
	
	/**
	 * Checks if logging occurs when getting connections via a number of
	 * different methods with the given connector.
	 * 
	 * @return true if logging occurred; false if not.
	 * @throws SQLException
	 *             if a database access error occurs
	 * @throws IOException
	 *             if an I/O error occurs
	 */
	private boolean connectorDoesLog() throws SQLException, IOException {
		RedirectedStderr.bytesOut.reset();
		
		// Get connections in a bunch of different ways, and then check
		// if System.err has written anything (the slf4j-simple binding logs to
		// System.err).
		try (Connection conn = connector.getConnection(tempDbPath)) {
			try (Connection innerConn = connector.getConnection(tempDbPath, false)) {	
			}	
			try (Connection innerConn = connector.getUnsharedConnection(tempDbPath)) {
			}
		}
		try (Connection con = connector.getUnsharedConnection(tempDbPath, new Properties())) {
		}
	
		return RedirectedStderr.bytesOut.toByteArray().length > 0;
	}

	@Test
	public void getConnection_usesPropertiesWhenConfigured() throws SQLException, IOException {
		SQLiteConfig config = new SQLiteConfig();
		config.enforceForeignKeys(true);
		connector = SQLiteConnectorBuilder
				.newBuilder()
				.withConnectionProperties(config.toProperties())
				.build();
		
		try (Connection conn = connector.getConnection(tempDbPath)) {
			try (Statement statement = conn.createStatement()) {
				statement.executeUpdate("CREATE TABLE fk_source (FKID INT PRIMARY KEY)");
				statement.executeUpdate("INSERT INTO fk_source VALUES(1)");
				statement.executeUpdate("CREATE TABLE fk_user (PKID INT PRIMARY KEY REFERENCES fk_source(FKID))");
				statement.executeUpdate("INSERT INTO fk_user VALUES(1)");
				thrown.expectMessage("foreign key constraint failed");
				statement.executeUpdate("DELETE FROM fk_source");
			}
		}
	}

}
