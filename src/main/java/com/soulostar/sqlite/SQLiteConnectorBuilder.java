package com.soulostar.sqlite;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.soulostar.sqlite.Utils.checkString;

import java.io.FileNotFoundException;
import java.util.Properties;

public class SQLiteConnectorBuilder {
	
	String subprotocol = "sqlite";
	Properties properties;
	String user;
	String password;
	int lockStripes = 4;
	private int initialCapacity = 16;
	private float loadFactor = 0.75f;
	private int concurrencyLevel = 16;
	boolean canCreateDatabases = true;
	Class<?> loggerClass;
	
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
	 *             <td><code>properties</code></td>
	 *             <td><code>null (will be ignored)</code></td>
	 *         </tr>
	 *         <tr>
	 *             <td><code>user</code></td>
	 *             <td><code>null (will be ignored)</code></td>
	 *         </tr>
	 *         <tr>
	 *             <td><code>password</code></td>
	 *             <td><code>null (will be ignored)</code></td>
	 *         </tr>
	 *         <tr>
	 *             <td><code>lockStripes</code></td>
	 *             <td><code>4</code></td>
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
	 *             <td><code>canCreateDatabases</code></td>
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
	public SQLiteConnectorBuilder cannotCreateDatabases() {
		canCreateDatabases = false;
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
		checkString(subprotocol, "Subprotocol");
		
		this.subprotocol = subprotocol;
		return this;
	}
	
	/**
	 * Specifies the properties connections should be opened with. For example,
	 * in order to enforce foreign key constraints with any opened connections,
	 * call this method and provide a <code>Properties</code> object containing
	 * the appropriate key-value pair. How to do this varies by driver.
	 * <p>
	 * If this method is called, it will take precedence over
	 * {@link #withConnectionCredentials(String, String)}; the latter method
	 * call will have no effect on connectors created with this builder. If user
	 * credentials as well as other properties are both needed, the credentials
	 * should be supplied as properties in the <code>Properties</code> argument
	 * of this method.
	 * <p>
	 * The properties object of this builder is copied each time
	 * {@link #build()} is called, so connectors each have their own reference
	 * to a distinct properties object.
	 * 
	 * @param properties
	 *            - properties shared connections should be opened with
	 * @return this object, for chaining calls.
	 */
	public SQLiteConnectorBuilder withConnectionProperties(Properties properties) {
		checkNotNull(properties, "Properties");
		
		this.properties = properties;
		return this;
	}
	
	/**
	 * Specifies the credentials connections should be opened with, for
	 * databases that are configured to use authentication.
	 * <p>
	 * <b>Note</b>: This method has no effect on any connectors created with
	 * this builder if {@link #withConnectionProperties(Properties)} has also
	 * been called. Credentials can be supplied in the <code>Properties</code>
	 * object argument of that method if necessary.
	 * 
	 * @param user
	 *            - the database user on whose behalf connections will be made
	 * @param password
	 *            - the user's password
	 * @return this object, for chaining calls.
	 */
	public SQLiteConnectorBuilder withConnectionCredentials(String user, String password) {
		checkString(user, "User");
		checkNotNull(password, "Password");
		
		this.user = user;
		this.password = password;
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
	 * cost of decreased concurrency. The default stripe count is <code>4</code>
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
	
	/**
	 * Turns on logging for built connectors. Informational logging will be done
	 * at the <code>trace</code> level, while warnings will be logged at the
	 * <code>warn</code> level. Nothing will be logged by the connector at any
	 * other levels.
	 * <p>
	 * Note that the connector uses SLF4J to log, so an SLF4J binding is required
	 * to see the log output.
	 * 
	 * @param clazz - the class to name the logger after
	 * @return this object, for chaining calls.
	 */
	public SQLiteConnectorBuilder withLogging(Class<?> clazz) {
		loggerClass = clazz;
		return this;
	}
	
	/**
	 * Builds and returns a new {@link SQLiteConnector} instance that uses
	 * the configuration of this builder.
	 * 
	 * @return a new <code>SQLiteConnector</code> instance.
	 */
	public SQLiteConnector build() {
		return new SQLiteConnector(
			subprotocol,
			copyProperties(),
			user,
			password,
			lockStripes,
			initialCapacity,
			loadFactor,
			concurrencyLevel,
			canCreateDatabases,
			loggerClass
		);
	}
	
	private Properties copyProperties() {
		if (properties == null) return null;

		Properties copy = new Properties();
		copy.putAll(properties);
		return copy;
	}
}
