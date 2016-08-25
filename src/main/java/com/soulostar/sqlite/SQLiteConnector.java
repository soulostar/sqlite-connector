package com.soulostar.sqlite;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.soulostar.sqlite.Utils.checkString;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.sql.Array;
import java.sql.Blob;
import java.sql.CallableStatement;
import java.sql.Clob;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.sql.NClob;
import java.sql.PreparedStatement;
import java.sql.SQLClientInfoException;
import java.sql.SQLException;
import java.sql.SQLWarning;
import java.sql.SQLXML;
import java.sql.Savepoint;
import java.sql.Statement;
import java.sql.Struct;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.locks.Lock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.util.concurrent.Striped;


/**
 * A connector for SQLite connections. It maintains at most one connection per
 * canonical file path. If multiple threads attempt to access the same database
 * simultaneously, the same {@link SQLiteConnection} will be returned to all of
 * them. This usually performs better than returning separate connections to
 * each thread, avoids file locking exceptions, and is safe to do in SQLite's
 * default threading mode (Serialized).
 * 
 * @author Sidney Tang
 */
public class SQLiteConnector {

	static final String IN_MEMORY_SUBNAME = ":memory:";

	final Logger logger;

	final String connectionStringPrefix;
	
	final Properties properties;
	final String user;
	final String password;
	
	/**
	 * A map that maps canonical paths to SQLite connections. Paths of any other
	 * kind (relative, absolute) should not be used as keys. Also note that the
	 * canonical paths should always be of <b>existing files</b>, since canonical
	 * paths for the same file may differ depending on if it exists, doesn't exist,
	 * or was deleted.
	 * @see {@link File#getCanonicalPath()}
	 */
	private final Map<String, SQLiteConnection> connectionMap;
	final Striped<Lock> locks;
	
	final boolean canCreateDatabases;
	
	SQLiteConnector(
		String subprotocol,
		Properties properties,
		String user,
		String password,
		int lockStripes,
		int initialCapacity,
		float loadFactor,
		int concurrencyLevel,
		boolean canCreateDatabases,
		Class<?> clazz
	) {
		connectionStringPrefix = "jdbc:" + subprotocol + ":";
		this.properties = properties;
		this.user = user;
		this.password = password;
		locks = Striped.lock(lockStripes);
		connectionMap = new ConcurrentHashMap<>(initialCapacity, loadFactor, concurrencyLevel);
		this.canCreateDatabases = canCreateDatabases;
		logger = clazz == null ? null : LoggerFactory.getLogger(clazz);
	}
	
	/**
	 * Gets a connection to the database at the specified path. If the database
	 * does not exist and {@link SQLiteConnectorBuilder#cannotCreateDatabases()} was
	 * <b>not</b> called during the building of this connector, the database
	 * will be created and a connection to the newly created database will be
	 * returned.
	 * <p>
	 * This connector will only ever use one connection at a time for a given
	 * database. If multiple threads want to connect to the same database
	 * concurrently, the same connection will be returned to all of them. This
	 * is safe to do in SQLite's default threading mode, <code>Serialized</code>
	 * , and performs better than granting separate connections to each thread.
	 * 
	 * @param dbPath
	 *            - the path of the target database, or <code>:memory:</code> to
	 *            connect to an in-memory database
	 * @return a connection to the database at the specified path.
	 * @throws SQLException
	 *             if a database access error occurs
	 * @throws FileNotFoundException
	 *             if <code>cannotCreate</code> was called during the building
	 *             of this connector and the target database does not exist
	 * @throws IOException
	 *             if an I/O error occurs while constructing canonical paths
	 */
	public Connection getConnection(String dbPath) throws SQLException, IOException {
		return getConnection(dbPath, canCreateDatabases);
	}
	
	/**
	 * Gets a connection to the database at the specified path.
	 * <p>
	 * This connector will only ever use one connection at a time for a given
	 * database. If multiple threads want to connect to the same database
	 * concurrently, the same connection will be returned to all of them. This
	 * is safe to do in SQLite's default threading mode, <code>Serialized</code>
	 * , and performs better than granting separate connections to each thread.
	 * 
	 * @param dbPath
	 *            - the path of the target database, or <code>:memory:</code> to
	 *            connect to an in-memory database
	 * @param createIfDoesNotExist
	 *            - whether or not to create the target database if it doesn't
	 *            exist. If this is false and the database does not exist, an
	 *            exception will be thrown.
	 * @return a connection to the database at the specified path.
	 * @throws SQLException
	 *             if a database access error occurs
	 * @throws FileNotFoundException
	 *             if <code>createIfDoesNotExist</code> is false and the target
	 *             database does not exist
	 * @throws IOException
	 *             if an I/O error occurs while constructing canonical paths
	 */
	public Connection getConnection(String dbPath, boolean createIfDoesNotExist) throws SQLException, IOException {
		checkString(dbPath, "Database path");
		
		if (IN_MEMORY_SUBNAME.equals(dbPath)) {
			Lock lock = locks.get(IN_MEMORY_SUBNAME);
			lock.lock();
			try {				
				return getConnectionFromMap(IN_MEMORY_SUBNAME);
			} finally {
				lock.unlock();
			}
		}
	
		File dbFile = new File(dbPath);
		String canonicalPath = dbFile.getCanonicalPath();
		Lock lock = locks.get(canonicalPath);
		lock.lock();
		try {
			if (dbFile.exists()) {
				return getConnectionFromMap(canonicalPath);
			} else if (createIfDoesNotExist) {
				SQLiteConnection conn = new SQLiteConnection(dbPath, false);
				// Canonical path may be different between existing and
				// non-existing files, so we resolve canonical path again
				// here before putting connection into map.
				connectionMap.put(dbFile.getCanonicalPath(), conn);
				return conn;
			}
			throw new FileNotFoundException("Can't get SQLite database connection. File doesn't exist: " + dbPath);
		} finally {
			lock.unlock();
		}
	}
	
	/**
	 * Gets an <b>unshared</b> connection to a database, without using any
	 * properties or credentials.
	 * <p>
	 * This method is provided for convenience, facilitating opening of
	 * connections that do not use the properties/credentials this connector was
	 * configured with. Such connections can't be shared, as sharing them would
	 * lead to unexpected and most likely incorrect behavior. See the overloads
	 * for this method for more details.
	 * 
	 * @param dbPath
	 *            - the path of the database to connect to
	 * @return an <b>unshared</b> connection to the database at the specified
	 *         path.
	 * @throws SQLException
	 *             if a database access error occurs
	 * @see {@link #getUnsharedConnection(String, Properties)}
	 * @see {@link #getUnsharedConnection(String, String, String)}
	 */
	public Connection getUnsharedConnection(String dbPath) throws SQLException {
		checkString(dbPath, "Database path");
		
		return DriverManager.getConnection(connectionStringPrefix + dbPath);
	}
	
	/**
	 * Gets an <b>unshared</b> connection to a database using the specified
	 * properties.
	 * <p>
	 * This method does not use this connector's connection sharing
	 * functionality. It is just for convenience. Connection sharing can't be
	 * done because if there is an existing shared connection to the specified
	 * database, it may not have the same properties as the ones passed into
	 * this method, and the caller expects to get a connection using the
	 * properties it provided.
	 * 
	 * @param dbPath
	 *            - the path of the database to connect to
	 * @param info
	 *            - a properties object containing connection properties such as
	 *            whether or not to enforce foreign key constraints. If null,
	 *            the returned connection will not use any properties.
	 * @return an <b>unshared</b> connection to the database at the specified
	 *         path.
	 * @throws SQLException
	 *             if a database access error occurs
	 */
	public Connection getUnsharedConnection(String dbPath, Properties info) throws SQLException {
		checkString(dbPath, "Database path");
		checkNotNull(info, "Properties argument is null.");
		
		return DriverManager.getConnection(connectionStringPrefix + dbPath, info);
	}
	
	/**
	 * Gets an <b>unshared</b> connection to the database using the provided
	 * credentials.
	 * <p>
	 * This method does not use this connector's connection sharing
	 * functionality. It is just for convenience. Connection sharing can't be
	 * done because if there is an existing shared connection to the specified
	 * database, it would have to be returned without validating the
	 * user/password credentials provided. We <i>could</i> validate the
	 * credentials by attempting to establish a test connection and then
	 * returning the existing connection if valid, but at that point we are
	 * going to have to deal with file locks anyways, which negates the
	 * performance benefit of sharing connections.
	 * 
	 * @param dbPath
	 *            - the path of the database to connect to
	 * @param user
	 *            - the database user on whose behalf the connection is being
	 *            made
	 * @param password
	 *            - the user's password
	 * @return an <b>unshared</b> connection to the database at the specified
	 *         path.
	 * @throws SQLException
	 *             if a database access error occurs
	 */
	public Connection getUnsharedConnection(String dbPath, String user, String password) throws SQLException {
		checkString(dbPath, "Database path");
		checkString(user, "User");
		checkNotNull(password, "Password");
		
		return DriverManager.getConnection(connectionStringPrefix + dbPath, user, password);
	}
	

	/**
	 * Attempts to retrieve an existing connection for the specified path from
	 * the connection map. If no existing connection is present or the existing
	 * connection is closed, a new connection will be created and returned
	 * instead.
	 * <p>
	 * <b>Note</b>: This method should only be called from within a
	 * lock-protected try block. It can produce incorrect behavior if called
	 * outside of locked sections with the same argument from multiple threads.
	 * 
	 * @param dbPath
	 *            - the path of the database to connect to
	 * @return a connection to the database.
	 * @throws SQLException
	 *             if a database access error occurs
	 * @throws IOException if an I/O error occurs
	 */
	private SQLiteConnection getConnectionFromMap(String dbPath) throws SQLException, IOException {
		SQLiteConnection existingConn = connectionMap.get(dbPath);
		if (existingConn == null || existingConn.isClosed()) {
			SQLiteConnection conn = new SQLiteConnection(dbPath, true);
			connectionMap.put(dbPath, conn);
			return conn;
		} else {
			existingConn.incrementUsers();
			return existingConn;
		}
	}
	
	/**
	 * A <code>Connection</code> wrapper/replacement class that stores the
	 * canonical path of the database the connection is using and tracks the
	 * number of users currently using the connection. Intended for use with
	 * SQLite connections, since multiple threads can use the same connection
	 * for SQLite.
	 * 
	 * @see SQLiteConnector
	 *
	 */
	private class SQLiteConnection implements Connection {
		
		/** The connection being wrapped by this object. */
		private final Connection conn;
		
		/** The canonical path of the database file this object is connected to. */
		private final String canonicalPath;
		
		/** The number of users (threads) currently using this connection. */
		private int numUsers = 1;
		
		/**
		 * Creates a new <code>SQLiteConnection</code> to the database indicated
		 * by the given subname, typically a file path. In-memory databases may
		 * also be supported depending on the driver being used.
		 * 
		 * @param dbPath
		 *            - the path of the database
		 * @param canonical
		 *            - true if <code>dbPath</code> is already a canonical path;
		 *            false otherwise
		 * @throws SQLException
		 *             if a database access error occurs
		 * @throws IOException
		 *             if an I/O error occurs
		 */
		private SQLiteConnection(String dbPath, boolean canonical) throws SQLException, IOException
		{			
			String url = connectionStringPrefix + dbPath;
			if (properties != null) {
				conn = DriverManager.getConnection(url, properties);
			} else if (user != null && password != null) {
				conn = DriverManager.getConnection(url, user, password);
			} else {
				conn = DriverManager.getConnection(url);		
			}
			
			canonicalPath = canonical ? dbPath : new File(dbPath).getCanonicalPath();
			
			if (logger != null) logger.trace("New {} created.", this);
		}

		/**
		 * Increments the user count for this <code>SQLiteConnection</code> by
		 * 1.
		 * <p>
		 * <b>Note</b>: This method should only be called from within a
		 * lock-protected try block. It can produce incorrect behavior if called
		 * outside of locked sections from multiple threads.
		 */
		private void incrementUsers()
		{
			numUsers++;
			if (logger != null) logger.trace("{} user count incremented to {}.", this, numUsers);
		}

		@Override
		public boolean isWrapperFor(Class<?> iface) throws SQLException 
		{
			return conn.isWrapperFor(iface);
		}

		@Override
		public <T> T unwrap(Class<T> iface) throws SQLException 
		{
			return conn.unwrap(iface);
		}

		@Override
		public void abort(Executor executor) throws SQLException 
		{
			conn.abort(executor);
		}

		@Override
		public void clearWarnings() throws SQLException 
		{
			conn.clearWarnings();
		}

		@Override
		public void close() throws SQLException
		{
			if (isClosed()) return;
			
			Lock lock = locks.get(canonicalPath);
			lock.lock();
			try {
				numUsers--;
				if (numUsers == 0) {
					conn.close();
					if (logger != null) logger.trace("{} underlying Connection closed.", this);
				} else {					
					if (logger != null) logger.trace("{} user count decremented to {}.", this, numUsers);
				}
			} catch (Exception ex) {				
				if (logger != null) logger.warn("Error closing connection: {}", ex.getMessage());
			} finally {
				lock.unlock();
			}
		}

		@Override
		public void commit() throws SQLException
		{
			conn.commit();
		}

		@Override
		public Array createArrayOf(String typeName, Object[] elements) throws SQLException 
		{
			return conn.createArrayOf(typeName, elements);
		}

		@Override
		public Blob createBlob() throws SQLException 
		{
			return conn.createBlob();
		}

		@Override
		public Clob createClob() throws SQLException 
		{
			return conn.createClob();
		}

		@Override
		public NClob createNClob() throws SQLException 
		{
			return conn.createNClob();
		}

		@Override
		public SQLXML createSQLXML() throws SQLException 
		{
			return conn.createSQLXML();
		}

		@Override
		public Statement createStatement() throws SQLException
		{
			return conn.createStatement();
		}

		@Override
		public Statement createStatement(int resultSetType, int resultSetConcurrency) throws SQLException 
		{
			return conn.createStatement(resultSetType, resultSetConcurrency);
		}

		@Override
		public Statement createStatement(int resultSetType, int resultSetConcurrency, int resultSetHoldability)
				throws SQLException 
		{
			return conn.createStatement(resultSetType, resultSetConcurrency, resultSetHoldability);
		}

		@Override
		public Struct createStruct(String typeName, Object[] attributes) throws SQLException 
		{
			return conn.createStruct(typeName, attributes);
		}

		@Override
		public boolean getAutoCommit() throws SQLException 
		{
			return conn.getAutoCommit();
		}

		@Override
		public String getCatalog() throws SQLException 
		{
			return conn.getCatalog();
		}

		@Override
		public Properties getClientInfo() throws SQLException 
		{
			return conn.getClientInfo();
		}

		@Override
		public String getClientInfo(String name) throws SQLException 
		{
			return conn.getClientInfo(name);
		}

		@Override
		public int getHoldability() throws SQLException 
		{
			return conn.getHoldability();
		}

		@Override
		public DatabaseMetaData getMetaData() throws SQLException 
		{
			return conn.getMetaData();
		}

		@Override
		public int getNetworkTimeout() throws SQLException 
		{
			return conn.getNetworkTimeout();
		}

		@Override
		public String getSchema() throws SQLException 
		{
			return conn.getSchema();
		}

		@Override
		public int getTransactionIsolation() throws SQLException 
		{
			return conn.getTransactionIsolation();
		}

		@Override
		public Map<String, Class<?>> getTypeMap() throws SQLException 
		{
			return conn.getTypeMap();
		}

		@Override
		public SQLWarning getWarnings() throws SQLException 
		{
			return conn.getWarnings();
		}

		@Override
		public boolean isClosed() throws SQLException 
		{
			return conn.isClosed();
		}

		@Override
		public boolean isReadOnly() throws SQLException 
		{
			return conn.isReadOnly();
		}

		@Override
		public boolean isValid(int timeout) throws SQLException 
		{
			return conn.isValid(timeout);
		}

		@Override
		public String nativeSQL(String sql) throws SQLException 
		{
			return conn.nativeSQL(sql);
		}

		@Override
		public CallableStatement prepareCall(String sql) throws SQLException 
		{
			return conn.prepareCall(sql);
		}

		@Override
		public CallableStatement prepareCall(String sql, int resultSetType, int resultSetConcurrency)
				throws SQLException 
		{
			return conn.prepareCall(sql, resultSetType, resultSetConcurrency);
		}

		@Override
		public CallableStatement prepareCall(String sql, int resultSetType, int resultSetConcurrency,
				int resultSetHoldability) throws SQLException 
		{
			return conn.prepareCall(sql, resultSetType, resultSetConcurrency, resultSetHoldability);
		}

		@Override
		public PreparedStatement prepareStatement(String sql) throws SQLException
		{
			return conn.prepareStatement(sql);
		}

		@Override
		public PreparedStatement prepareStatement(String sql, int autoGeneratedKeys) throws SQLException 
		{
			return conn.prepareStatement(sql, autoGeneratedKeys);
		}

		@Override
		public PreparedStatement prepareStatement(String sql, int[] columnIndexes) throws SQLException 
		{
			return conn.prepareStatement(sql, columnIndexes);
		}

		@Override
		public PreparedStatement prepareStatement(String sql, String[] columnNames) throws SQLException 
		{
			return conn.prepareStatement(sql, columnNames);
		}

		@Override
		public PreparedStatement prepareStatement(String sql, int resultSetType, int resultSetConcurrency) 
				throws SQLException
		{
			return conn.prepareStatement(sql, resultSetType, resultSetConcurrency);
		}

		@Override
		public PreparedStatement prepareStatement(String sql, int resultSetType, int resultSetConcurrency,
				int resultSetHoldability) throws SQLException 
		{
			return conn.prepareStatement(sql, resultSetType, resultSetConcurrency, resultSetHoldability);
		}

		@Override
		public void releaseSavepoint(Savepoint savepoint) throws SQLException 
		{
			conn.releaseSavepoint(savepoint);
		}

		@Override
		public void rollback() throws SQLException
		{
			conn.rollback();
		}

		@Override
		public void rollback(Savepoint savepoint) throws SQLException 
		{
			conn.rollback(savepoint);
		}

		@Override
		public void setAutoCommit(boolean autoCommit) throws SQLException
		{
			conn.setAutoCommit(autoCommit);
		}

		@Override
		public void setCatalog(String catalog) throws SQLException 
		{
			conn.setCatalog(catalog);
		}

		@Override
		public void setClientInfo(Properties properties) throws SQLClientInfoException 
		{
			conn.setClientInfo(properties);
		}

		@Override
		public void setClientInfo(String name, String value) throws SQLClientInfoException 
		{
			conn.setClientInfo(name, value);
		}

		@Override
		public void setHoldability(int holdability) throws SQLException 
		{
			conn.setHoldability(holdability);
		}

		@Override
		public void setNetworkTimeout(Executor executor, int milliseconds) throws SQLException 
		{
			conn.setNetworkTimeout(executor, milliseconds);
		}

		@Override
		public void setReadOnly(boolean readOnly) throws SQLException 
		{
			conn.setReadOnly(readOnly);
		}

		@Override
		public Savepoint setSavepoint() throws SQLException 
		{
			return conn.setSavepoint();
		}

		@Override
		public Savepoint setSavepoint(String name) throws SQLException 
		{
			return conn.setSavepoint(name);
		}

		@Override
		public void setSchema(String schema) throws SQLException 
		{
			conn.setSchema(schema);
		}

		@Override
		public void setTransactionIsolation(int level) throws SQLException 
		{
			conn.setTransactionIsolation(level);
		}

		@Override
		public void setTypeMap(Map<String, Class<?>> map) throws SQLException 
		{
			conn.setTypeMap(map);
		}
		
		@Override
		public String toString()
		{
			return "[SQLiteConnection (" + canonicalPath + ")]";
		}
		
	}


}