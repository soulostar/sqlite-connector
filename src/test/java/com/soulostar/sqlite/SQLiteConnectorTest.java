package com.soulostar.sqlite;

import static com.soulostar.sqlite.SQLiteConnector.IN_MEMORY_SUBNAME;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;

import org.junit.BeforeClass;
import org.junit.Test;

public class SQLiteConnectorTest {
	
	@BeforeClass
	public static void beforeClass() throws ClassNotFoundException {
		Class.forName("org.sqlite.JDBC");
	}

	@Test
	public void sameConnection_inMemory() throws SQLException, IOException {
		SQLiteConnector connector = SQLiteConnectorBuilder.newBuilder().build();
		
		try (Connection conn = connector.getConnection(IN_MEMORY_SUBNAME)) {
			try (Connection conn1 = connector.getConnection(IN_MEMORY_SUBNAME)) {
				assertTrue(conn == conn1);
			}
		}
	}

}
