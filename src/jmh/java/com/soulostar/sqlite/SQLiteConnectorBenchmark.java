package com.soulostar.sqlite;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Group;
import org.openjdk.jmh.annotations.GroupThreads;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Warmup;

@State(Scope.Group)
@Fork(2)
public class SQLiteConnectorBenchmark {
	
	private static final int READ_THREADS = 4;
	private static final int WRITE_THREADS = 1;
	
	private static final int WARMUP_ITERATIONS = 5;
	private static final int MEASUREMENT_ITERATIONS = 5;

	private static final int WARMUP_BATCH_SIZE = 1000;
	private static final int MEASUREMENT_BATCH_SIZE = 1000;
	
	private SQLiteConnector connector = SQLiteConnectorBuilder.newBuilder().build();
	private Path benchmarkDir = Paths.get("benchmark");
	private String dbPath = Paths.get(benchmarkDir.toString(), UUID.randomUUID().toString() + ".db").toString();
	private String url = "jdbc:sqlite:" + dbPath;
	private String[] testIds = new String[] {
			"Stephen", "Klay", "Draymond", "Kevin", "Andre",
			"Shaun", "Zaza", "David"
			};
	
	@Setup(Level.Trial)
	public void setup() throws ClassNotFoundException, IOException, SQLException {
		Class.forName("org.sqlite.JDBC");
		Files.createDirectories(benchmarkDir);
		
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
	
	@TearDown(Level.Trial)
	public void teardown() throws IOException {
		Files.delete(Paths.get(dbPath));
	}
	
	@Benchmark
	@Group("concurrentShared")
	@GroupThreads(READ_THREADS)
	@Warmup(iterations = WARMUP_ITERATIONS, batchSize = WARMUP_BATCH_SIZE)
	@Measurement(iterations = MEASUREMENT_ITERATIONS, batchSize = MEASUREMENT_BATCH_SIZE)
	@BenchmarkMode(Mode.SingleShotTime)
	public boolean sharedRead() throws SQLException, IOException {
		try (Connection conn = connector.getConnection(dbPath)) {
			try (PreparedStatement insert = conn.prepareStatement("SELECT * FROM perf_test WHERE id = ? LIMIT 1")) {
				insert.setString(1, testIds[ThreadLocalRandom.current().nextInt(0, testIds.length - 1)]);
				try (ResultSet rs =	insert.executeQuery()) {
					return rs.next();
				}
			}
		}
	}
	
	@Benchmark
	@Group("concurrentShared")
	@GroupThreads(WRITE_THREADS)
	@Warmup(iterations = WARMUP_ITERATIONS, batchSize = WARMUP_BATCH_SIZE)
	@Measurement(iterations = MEASUREMENT_ITERATIONS, batchSize = MEASUREMENT_BATCH_SIZE)
	@BenchmarkMode(Mode.SingleShotTime)
	public int sharedWrite() throws SQLException, IOException {
		try (Connection conn = connector.getConnection(dbPath)) {
			try (PreparedStatement insert = conn.prepareStatement("INSERT INTO perf_test VALUES(?)")) {
				insert.setString(1, testIds[ThreadLocalRandom.current().nextInt(0, testIds.length - 1)]);
				return insert.executeUpdate();
			}
		}
	}
	
	@Benchmark
	@Group("concurrentSharedGet")
	@GroupThreads(READ_THREADS)
	@Warmup(iterations = WARMUP_ITERATIONS, batchSize = WARMUP_BATCH_SIZE)
	@Measurement(iterations = MEASUREMENT_ITERATIONS, batchSize = MEASUREMENT_BATCH_SIZE)
	@BenchmarkMode(Mode.SingleShotTime)
	public boolean sharedGet() throws SQLException, IOException {
		try (Connection conn = connector.getConnection(dbPath)) {
			return conn.isClosed();
		}
	}
	
	@Benchmark
	@Group("concurrentSharedGet")
	@GroupThreads(WRITE_THREADS)
	@Warmup(iterations = WARMUP_ITERATIONS, batchSize = WARMUP_BATCH_SIZE)
	@Measurement(iterations = MEASUREMENT_ITERATIONS, batchSize = MEASUREMENT_BATCH_SIZE)
	@BenchmarkMode(Mode.SingleShotTime)
	public int sharedWriteGetTest() throws SQLException, IOException {
		try (Connection conn = connector.getConnection(dbPath)) {
			try (PreparedStatement insert = conn.prepareStatement("INSERT INTO perf_test VALUES(?)")) {
				insert.setString(1, testIds[ThreadLocalRandom.current().nextInt(0, testIds.length - 1)]);
				return insert.executeUpdate();
			}
		}
	}
	
	@Benchmark
	@Group("concurrentUnshared")
	@GroupThreads(READ_THREADS)
	@Warmup(iterations = WARMUP_ITERATIONS, batchSize = WARMUP_BATCH_SIZE)
	@Measurement(iterations = MEASUREMENT_ITERATIONS, batchSize = MEASUREMENT_BATCH_SIZE)
	@BenchmarkMode(Mode.SingleShotTime)
	public boolean unsharedRead() throws SQLException {
		try (Connection conn = DriverManager.getConnection(url)) {
			try (PreparedStatement insert = conn.prepareStatement("SELECT * FROM perf_test WHERE id = ? LIMIT 1")) {
				insert.setString(1, testIds[ThreadLocalRandom.current().nextInt(0, testIds.length - 1)]);
				try (ResultSet rs =	insert.executeQuery()) {
					return rs.next();
				}
			}
		}
	}
	
	@Benchmark
	@Group("concurrentUnshared")
	@GroupThreads(WRITE_THREADS)
	@Warmup(iterations = WARMUP_ITERATIONS, batchSize = WARMUP_BATCH_SIZE)
	@Measurement(iterations = MEASUREMENT_ITERATIONS, batchSize = MEASUREMENT_BATCH_SIZE)
	@BenchmarkMode(Mode.SingleShotTime)
	public int unsharedWrite() throws SQLException {
		try (Connection conn = DriverManager.getConnection(url)) {
			try (PreparedStatement insert = conn.prepareStatement("INSERT INTO perf_test VALUES(?)")) {
				insert.setString(1, testIds[ThreadLocalRandom.current().nextInt(0, testIds.length - 1)]);
				return insert.executeUpdate();
			}
		}
	}

}
