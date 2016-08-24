#SQLiteConnector

## Intro

A simple library to make using SQLite's default serialized threading mode more convenient, by automatically sharing connections to the same database. That is, if a new connection to database X is requested while there is already an open connection to database X from somewhere else, that existing open connection will be returned to the requester.

This very small project was inspired by [a Stack Overflow question](http://stackoverflow.com/questions/10707434/sqlite-in-a-multithreaded-java-application), in particular:
> SQLite uses filesystem-based locks for concurrent access synchronization among processes, since as an embedded database it does not have a dedicated process (server) to schedule operations. Since each thread in your code creates its own connection to the database, it is treated as a separate process, with synchronization happening via file-based locks, which are significantly slower than any other synchronization method.

and:
> The core SQLite library by default allows multiple threads to use the same connection concurrently with no problem.

Keep in mind that this library is written for use with SQLite's serialized mode. For more information on SQLite threading modes, see: https://www.sqlite.org/threadsafe.html

## Download

The jar can be downloaded from the releases page. Ideally, this project will be available in a central repository sometime soon for use with Gradle, Maven, etc.

## Requirements

JDK 1.7+

## Usage

### What this library does
The primary function of this library is illustrated in the following code snippet (albeit in an impractical way):
```java
try (Connection conn1 = connector.getConnection('C:\\mydatabase.db')) {
    try (Connection conn2 = connector.getConnection('C:\\mydatabase.db')) {
        // conn1 == conn2. The same connection was returned in both places.
        // This example is mostly useless, but the functionality proves more
        // useful when the connections are being requested from separate threads.
    }
}
```
Closing a shared connection (that is, a connection returned by `getConnection` and not `getUnsharedConnection`) will decrement its user count by 1. Only when the user count reaches zero will the underlying actual connection to the database be closed.


### Obtaining an instance

You can obtain an instance of `SQLiteConnector` by using the `SQLiteConnectorBuilder` class.
The builder contains a number of configuration methods to allow customization, but in some cases
the default configuration will work fine and you may simply write:
```java
SQLiteConnector connector = SQLiteConnectorBuilder.newBuilder().build();
```
In most cases, you will at least want to enable foreign key constraints on connections the connector creates.
How to accomplish this may vary by driver. Assuming usage of [Xerial's SQLite JDBC driver](https://github.com/xerial/sqlite-jdbc), this can be done as follows:
```java
SQLiteConfig config = new SQLiteConfig(); // provided by driver
config.enforceForeignKeys(true);
SQLiteConnector connector = SQLiteConnectorBuilder
    .newBuilder()
    .withConnectionProperties(config.toProperties())
    .build();
```

### Getting connections

To get connections with the connector, call `getConnection`:
```java
try (Connection conn = connector.getConnection("mydatabase.db")) {
	// This connection is shared, and any other getConnection
	// calls to mydatabase.db before this connection is closed
	// will return this same connection object.
}
```
All connections obtained this way will be shared if multiple threads want access to the same database concurrently. They will also all use the properties or user/password the connector was configured with during its construction, such as in the foreign key example above.

Occasionally, you may want to get a connection to a database without using the properties or user/password the connector was configured with. For example, after initially building the connector to enforce foreign key constraints, you may want to get a connection with foreign key constraints not enforced, in order to drop and recreate a table. To do so, call `getUnsharedConnection`:
```java
try (Connection conn = connector.getUnsharedConnection("example.db")) {
	// This connection is unique, unshared, and subject
	// to SQLite file-based locks.
}
```
This returns an unshared connection, as using a shared connection for this could lead to unexpected and probably incorrect behavior. It is a thin convenience wrapper around `DriverManager.getConnection`.

Connections are not pooled since SQLite operates on the local file system and creating connections has very little overhead.

### Loading drivers

Note that this library does not handle loading drivers. Typically, you would call
```java
Class.forName("org.sqlite.JDBC");
```
at some point in your code first, before using this connector.

## Todo
- ~~Write tests~~ (Done)
- Add support for associating different user/password credentials with different databases,
instead of using the same credentials for all shared connections.
- Expire old entries in the connection map so it cannot grow unbounded; may convert the map to a cache
