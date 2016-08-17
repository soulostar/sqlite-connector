package com.soulostar.sqlite;


public class SQLiteConnectorBuilder {
	
	private String connStringPrefix = "jdbc:sqlite:";
	private int lockStripes = 5;
	private int initialCapacity = 16;
	private float loadFactor = 0.75f;
	private int concurrencyLevel = 16;
	
	// prevent instantiation
	private SQLiteConnectorBuilder() {
	}
	
	/**
	 * Creates and returns a new <code>SQLiteConnectorBuilder</code> with default parameter values.
	 * Default values are as follows:
	 * <p>
	 * <table>
	 * <thead><tr><th>Parameter</th><th>Default Value</th></tr></thead>
	 * <tbody>
	 * <tr><td><code>connStringPrefix</code></td><td><code>jdbc:sqlite:</code></td></tr>
	 * <tr><td><code>lockStripes</code></td><td><code>5</code></td></tr>
	 * <tr><td><code>initialCapacity</code></td><td><code>16</code></td></tr>
	 * <tr><td><code>loadFactor</code></td><td><code>0.75</code></td></tr>
	 * <tr><td><code>concurrencyLevel</code></td><td><code>16</code></td></tr>
	 * </tbody>
	 * </table>
	 * <p>
	 * To get a default-configuration {@link SQLiteConnector}, just immediately call {@link #build()}.
	 * @return
	 */
	public static SQLiteConnectorBuilder newBuilder() {
		return new SQLiteConnectorBuilder();
	}
	
	public SQLiteConnectorBuilder withConnStringPrefix(String connStringPrefix) {
		this.connStringPrefix = connStringPrefix;
		return this;
	}
	
	public SQLiteConnectorBuilder withLockStripes(int lockStripes) {
		this.lockStripes = lockStripes;
		return this;
	}
	
	public SQLiteConnectorBuilder withInitialCapacity(int initialCapacity) {
		this.initialCapacity = initialCapacity;
		return this;
	}
	
	public SQLiteConnectorBuilder withLoadFactor(float loadFactor) {
		this.loadFactor = loadFactor;
		return this;
	}
	
	public SQLiteConnectorBuilder withConcurrencyLevel(int concurrencyLevel) {
		this.concurrencyLevel = concurrencyLevel;
		return this;
	}
	
	public SQLiteConnector build() {
		return new SQLiteConnector(
			connStringPrefix,
			lockStripes,
			initialCapacity,
			loadFactor,
			concurrencyLevel
		);
	}
}
