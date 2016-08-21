package com.soulostar.sqlite;

import static com.soulostar.sqlite.SQLiteConnector.IN_MEMORY_SUBNAME;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.SQLException;

import org.junit.After;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.rules.TemporaryFolder;

public class SQLiteConnectorTest {
	
	@Rule
	public TemporaryFolder folder = new TemporaryFolder();
	
	@Rule
	public ExpectedException thrown = ExpectedException.none();

	@BeforeClass
	public static void beforeClass() throws ClassNotFoundException, IOException {
		Class.forName("org.sqlite.JDBC");
	}
	
	@After
	public void deleteTestDatabase() throws IOException {
		Files.deleteIfExists(Paths.get(getTempDbPath()));
	}

	@Test
	public void getConnection_sameConnectionForInMemoryDatabase() throws SQLException, IOException {
		SQLiteConnector connector = SQLiteConnectorBuilder.newBuilder().build();
		
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
		SQLiteConnector connector = SQLiteConnectorBuilder.newBuilder().build();
		
		String tempDbPath = getTempDbPath();
		try (Connection conn = connector.getConnection(tempDbPath)) {
			try (Connection conn1 = connector.getConnection(tempDbPath)) {
				try (Connection conn2 = connector.getConnection(tempDbPath)) {
					assertTrue(
							"Multiple requests for a connection to the same database " +
							"file should return the same connection object",
							conn == conn1 && conn1 == conn2);
				}
			}	
		}
	}
	
	@Test
	public void getConnection_sameConnectionForEquivalentPaths() throws IOException, SQLException {
		SQLiteConnector connector = SQLiteConnectorBuilder.newBuilder().build();
		
		// This test has to operate on a special temp directory other than
		// the TemporaryFolder, because we have to test relative paths in addition
		// to absolute/canonical paths.
		Path tmpDir = Paths.get("tmp");
		Files.createDirectory(tmpDir);
		
		String relative = "tmp" + File.separator + "test.db";
		String relative1 = "tmp" + File.separator + ".." + File.separator
				+ "tmp" + File.separator + "test.db";
		String absolute = new File("tmp").getAbsolutePath() + File.separator
				+ ".." + File.separator + "tmp" + File.separator
				+ "test.db";
		String canonical = new File("tmp").getAbsolutePath() + File.separator + "test.db";
		
		try (Connection conn = connector.getConnection(relative)) {
			try (Connection conn1 = connector.getConnection(relative1)) {
				try (Connection conn2 = connector.getConnection(absolute)) {
					try (Connection conn3 = connector.getConnection(canonical)) {
						assertTrue(
								"Connection requests to a database using equivalent "
								+ "relative/absolute/canonical paths should return the same connection object",
								conn == conn1 && conn1 == conn2 && conn2 == conn3);
					}
				}
			}
		} finally {
			Files.deleteIfExists(Paths.get(relative));
			Files.deleteIfExists(Paths.get(relative1));
			Files.deleteIfExists(Paths.get(absolute));
			Files.deleteIfExists(Paths.get(canonical));
			Files.deleteIfExists(tmpDir);
		}
	}
	
	@Test
	public void getConnection_createsDatabaseWhenCreateOn() throws SQLException, IOException {
		SQLiteConnector connector = SQLiteConnectorBuilder.newBuilder().build();

		Path db = Paths.get(getTempDbPath());
		assertTrue("Test database should not exist before getting connection", Files.notExists(db));	
		try (Connection conn = connector.getConnection(getTempDbPath())) {
		}	
		assertTrue("Test database should have been created by getting connection", Files.exists(db));
	}
	
	@Test
	public void getConnection_throwsWhenCreateOff() throws SQLException, IOException {
		SQLiteConnector connector = SQLiteConnectorBuilder.newBuilder().cannotCreateDatabases().build();

		thrown.expect(FileNotFoundException.class);
		try (Connection conn = connector.getConnection(getTempDbPath())) {
		}
	}
	
	private String getTempDbPath() {
		return folder.getRoot().getAbsolutePath() + File.separator + "Test.db";
	}

}
