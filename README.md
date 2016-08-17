#SQLiteConnector

## Intro

A simple library to make using SQLite's default serialized threading mode more convenient, by automatically sharing connections to the same database. That is, if a new connection to database X is requested while there is already an open connection to database X from somewhere else, that existing open connection will be returned to the requester.

This very small project was inspired by [a Stack Overflow question](http://stackoverflow.com/questions/10707434/sqlite-in-a-multithreaded-java-application), in particular:
> SQLite uses filesystem-based locks for concurrent access synchronization among processes, since as an embedded database it does not have a dedicated process (server) to schedule operations. Since each thread in your code creates its own connection to the database, it is treated as a separate process, with synchronization happening via file-based locks, which are significantly slower than any other synchronization method.

and:
> The core SQLite library by default allows multiple threads to use the same connection concurrently with no problem.

Keep in mind that this library is written for use with SQLite's serialized mode. For more information on SQLite threading modes, see: https://www.sqlite.org/threadsafe.html

## Usage

You can obtain an instance of `SQLiteConnector` by using the `SQLiteConnectorBuilder` class.
The builder contains a number of configuration methods to allow customization, but in many cases
the default configuration will work fine and you may simply write:
```java
SQLiteConnector connector = SQLiteConnectorBuilder.newBuilder().build();
```

The primary function of this library is illustrated in the following code snippet (if in an impractical way):
```java
try (Connection conn1 = connector.getConnection('C:\\mydatabase.db')) {
    try (Connection conn2 = connector.getConnection('C:\\mydatabase.db')) {
        // conn1 == conn2. The same connection was returned in both places.
        // This example is mostly useless, but the functionality proves more
        // useful when the connections are being requested from separate threads.
    }
}
```

Note that this library does not handle loading drivers. Typically, you would call
```java
Class.forName("org.sqlite.JDBC");
```
at some point in your code first, before using this connector.

## Todo

Add support for the `DriverManager.getConnection` overloads that take a `Properties` object, and a `user` + `password`.

