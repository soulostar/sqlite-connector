package com.soulostar.sqlite;

import java.util.Properties;

public class SQLiteConnectorBuilder {
	
	private String subprotocol = "sqlite";
	private Properties defaultProperties = null;
	private int lockStripes = 5;
	private int initialCapacity = 16;
	private float loadFactor = 0.75f;
	private int concurrencyLevel = 16;
	private boolean canCreate = true;
	private boolean logging = false;
	
	// prevent instantiation
	private SQLiteConnectorBuilder() {
	}
	
	/**
	 * Creates and returns a new <code>SQLiteConnectorBuilder</code> with default parameter values.
	 * <table>
	 *     <thead>
	 *         <tr>
	 *             <th>Parameter</th>
	 *             <th>Default Value</th>
	 *         </tr>
	 *     </thead>
	 *     <tbody>
	 *         <tr>
	 *             <td><code>subprotocol</code></td>
	 *             <td><code>sqlite</code></td>
	 *         </tr>
	 *         <tr>
	 *             <td><code>defaultProperties</code></td>
	 *             <td><code>null (will be ignored)</code></td>
	 *         </tr>
	 *         <tr>
	 *             <td><code>lockStripes</code></td>
	 *             <td><code>5</code></td>
	 *         </tr>
	 *         <tr>
	 *             <td><code>initialCapacity</code></td>
	 *             <td><code>16</code></td>
	 *         </tr>
	 *         <tr>
	 *             <td><code>loadFactor</code></td>
	 *             <td><code>0.75</code></td>
	 *         </tr>
	 *         <tr>
	 *             <td><code>concurrencyLevel</code></td>
	 *             <td><code>16</code></td>
	 *         </tr>
	 *         <tr>
	 *             <td><code>canCreate</code></td>
	 *             <td><code>true</code></td>
	 *         </tr>
	 *         <tr>
	 *             <td><code>logging</code></td>
	 *             <td><code>false</code></td>
	 *         </tr>
	 *     </tbody>
	 * </table>
	 * <p>
	 * To get a default-configuration {@link SQLiteConnector}, just immediately
	 * call {@link #build()}.
	 * <p>
	 * If you don't want to use default settings, this builder provides methods
	 * to configure each of the above listed parameters before building the
	 * connector.
	 * 
	 * @return a new <code>SQLiteConnectorBuilder</code> with default parameters.
	 */
	public static SQLiteConnectorBuilder newBuilder() {
		return new SQLiteConnectorBuilder();
	}
	
	/**
	 * Specifies that the connector <b>cannot</b> create database files with
	 * {@link SQLiteConnector#getConnection(String)} - if the target database
	 * does not exist, the method will throw a {@link FileNotFoundException}
	 * instead of creating the database. Normally, the latter behavior is the
	 * default.
	 * <p>
	 * Note that a connector created with a builder that has called this method
	 * can still create database files if missing, by calling the
	 * {@link SQLiteConnector#getConnection(String, boolean)} overload.
	 * 
	 * @return this object, for chaining calls.
	 */
	public SQLiteConnectorBuilder cannotCreate() {
		canCreate = false;
		return this;
	}
	
	/**
	 * Specifies a custom subprotocol for the connector. SQLite connection
	 * strings are constructed as
	 * <code>jdbc:<i>subprotocol</i>:<i>path</i></code>.
	 * <p>
	 * In the vast majority of cases, the default value of <code>sqlite</code>
	 * should be used, and it is unnecessary to call this method. This method is
	 * provided on the off chance that there is a SQLite JDBC driver out there
	 * that uses a different subprotocol.
	 * 
	 * @param subprotocol
	 *            - the subprotocol the connector should use to build connection
	 *            strings
	 * @return this object, for chaining calls.
	 */
	public SQLiteConnectorBuilder withSubprotocol(String subprotocol) {
		this.subprotocol = subprotocol;
		return this;
	}
	
	public SQLiteConnectorBuilder withDefaultProperties(Properties properties) {
		this.defaultProperties = properties;
		return this;
	}
	
	/**
	 * Specifies the number of stripes to use for the get/close connections
	 * lock.
	 * <p>
	 * The connector locks map read operations (getting and closing connections)
	 * on a canonical-path basis. Concurrent map read operations for the
	 * <b>same</b> canonical path are guaranteed to use locking, but a best
	 * effort will be made for concurrent map read operations for
	 * <b>different</b> canonical paths to proceed without locking. A higher
	 * number of stripes allows for increased concurrency at the cost of a
	 * bigger memory footprint, while a lower stripe count saves memory at the
	 * cost of decreased concurrency. The default stripe count is <code>5</code>
	 * .
	 * 
	 * @param lockStripes
	 *            - the number of stripes to use for the map read lock
	 * @return this object, for chaining calls.
	 */
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
	
	public SQLiteConnectorBuilder withLogging() {
		logging = true;
		return this;
	}
	
	public SQLiteConnector build() {
		return new SQLiteConnector(
			subprotocol,
			defaultProperties,
			"dummyuser",
			"dummypassword",
			lockStripes,
			initialCapacity,
			loadFactor,
			concurrencyLevel,
			canCreate,
			logging
		);
	}
}
