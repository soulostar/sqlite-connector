package com.soulostar.sqlite;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;

@State(Scope.Benchmark)
public class SQLiteConnectorBenchmark {
	
	private static Path benchmarkDir = Paths.get("benchmark");
	private static String[] testIds = new String[] {
			"Stephen", "Klay", "Draymond", "Kevin", "Andre",
			"Shaun", "Zaza", "David"
			};
	
	@Setup(Level.Trial)
	public void setup() throws ClassNotFoundException, IOException {
		Class.forName("org.sqlite.JDBC");
		Files.createDirectories(benchmarkDir);
	}
	
	@TearDown(Level.Trial)
	public void teardown() throws IOException {
		try (DirectoryStream<Path> files = Files.newDirectoryStream(benchmarkDir)) {
			Iterator<Path> iterator = files.iterator();
			while (iterator.hasNext()) {
				Files.delete(iterator.next());
			}
		}
		Files.delete(benchmarkDir);
	}
	
	@State(Scope.Thread)
	public static class ThreadState {
		protected SQLiteConnector connector = SQLiteConnectorBuilder.newBuilder().build();
		protected String dbPath = Paths.get(benchmarkDir.toString(), UUID.randomUUID().toString() + ".db").toString();
		protected List<Callable<Void>> tasks = new ArrayList<>();
		ExecutorService executor;
		List<Future<Void>> results;
		
		@Setup(Level.Trial)
		public void setup() throws SQLException, IOException {
			executor = Executors.newCachedThreadPool();
			System.out.println("Creating perf_test in: " + dbPath);
			try (Connection conn = connector.getConnection(dbPath)) {
				try (Statement statement = conn.createStatement()) {
					statement.executeUpdate("CREATE TABLE perf_test (id VARCHAR)");
				}
			}
		}
		
		@Setup(Level.Invocation)
		public void setupInvocation() throws SQLException, IOException {
			try (Connection conn = connector.getConnection(dbPath)) {
				try (Statement statement = conn.createStatement()) {
					statement.executeUpdate("DELETE FROM perf_test");
				}
			}
		}
		
		@TearDown(Level.Invocation)
		public void checkResults() throws InterruptedException, ExecutionException {
			for (Future<Void> result : results) {
				result.get();
			}
		}
		
		@TearDown(Level.Trial)
		public void shutdown() {
			executor.shutdown();
		}

	}
	
	public static class SharedConnectionState extends ThreadState {

		@Setup(Level.Trial)
		public void setupTasks() {
			for (String testId : testIds) {
				tasks.add(new ConcurrentInsert(connector, dbPath, testId, true));
				tasks.add(new ConcurrentRead(connector, dbPath, true));
			}
		}
	}
	
	public static class UnsharedConnectionState extends ThreadState {

		@Setup(Level.Trial)
		public void setupTasks() {
			for (String testId : testIds) {
				tasks.add(new ConcurrentInsert(connector, dbPath, testId, false));
				tasks.add(new ConcurrentRead(connector, dbPath, false));
			}
		}
	}
	
	@Benchmark
	public List<Future<Void>> measureSharedConnections(SharedConnectionState state) throws InterruptedException {
		state.results = state.executor.invokeAll(state.tasks);
		return state.results;
	}
	
	@Benchmark
	public List<Future<Void>> measureUnsharedConnections(UnsharedConnectionState state) throws InterruptedException {
		state.results = state.executor.invokeAll(state.tasks);
		return state.results;
	}
	
	private static class ConcurrentInsert implements Callable<Void> {

		private final SQLiteConnector connector;
		private final String value;
		private final boolean shareConnection;
		private final String dbPath;
		private final String unsharedUrl;
		
		private ConcurrentInsert(SQLiteConnector connector, String dbPath, String value, boolean shareConnection) {
			this.connector = connector;
			this.value = value;
			this.shareConnection = shareConnection;
			this.dbPath = dbPath;
			this.unsharedUrl = "jdbc:sqlite:" + dbPath;
		}
		
		@Override
		public Void call() throws SQLException, IOException {
			try (Connection conn = shareConnection ? connector.getConnection(dbPath)
												   : DriverManager.getConnection(unsharedUrl)) {
				try (PreparedStatement insert = conn.prepareStatement("INSERT INTO perf_test VALUES(?)")) {
					insert.setString(1, value);
					insert.executeUpdate();
				}
				return null;
			}
		}
		
	}
	
	private static class ConcurrentRead implements Callable<Void> {
		
		private final SQLiteConnector connector;
		private final String dbPath;
		private final boolean shareConnection;
		private final String unsharedUrl;
		
		private ConcurrentRead(SQLiteConnector connector, String dbPath, boolean shareConnection) {
			this.connector = connector;
			this.dbPath = dbPath;
			this.shareConnection = shareConnection;
			this.unsharedUrl = "jdbc:sqlite:" + dbPath;
		}

		@Override
		public Void call() throws SQLException, IOException {
			try (Connection conn = shareConnection ? connector.getConnection(dbPath)
					   							   : DriverManager.getConnection(unsharedUrl)) {
				try (PreparedStatement insert = conn.prepareStatement("SELECT * FROM perf_test")) {
					insert.executeQuery();
				}
				return null;
			}
		}
		
	}

}
