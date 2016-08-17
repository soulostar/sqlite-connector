#SQLiteConnector

## Intro

A simple library to make using SQLite's default serialized threading mode more convenient, by automatically sharing connections to the same database. That is, if a new connection to database X is requested while there is already an open connection to database X from somewhere else, that existing open connection will be returned to the requester.

This very small project was inspired by [a Stack Overflow question](http://stackoverflow.com/questions/10707434/sqlite-in-a-multithreaded-java-application), in particular:
> SQLite uses filesystem-based locks for concurrent access synchronization among processes, since as an embedded database it does not have a dedicated process (server) to schedule operations. Since each thread in your code creates its own connection to the database, it is treated as a separate process, with synchronization happening via file-based locks, which are significantly slower than any other synchronization method.

and:
> The core SQLite library by default allows multiple threads to use the same connection concurrently with no problem.

Keep in mind that this library is written for use with SQLite's serialized mode. For more information on SQLite threading modes, see: https://www.sqlite.org/threadsafe.html

## License
This library is MIT licensed.

