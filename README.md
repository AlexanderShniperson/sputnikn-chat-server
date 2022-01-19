# SputnikN Chat Server
A SputnikN chat server written with Kotlin and Akka

### Repositories overview
The chat ecosystem consists of several dependent repositories:<br>
- [Database code gen](https://github.com/AlexanderShniperson/sputnikn-chat-codegen-db) - Class generator according to the DB schema, the DB schema is attached;<br>
- [Transport code gen](https://github.com/AlexanderShniperson/sputnikn-chat-codegen-proto) - Transport message generator between Client and Server;<br>
- [Chat server](https://github.com/AlexanderShniperson/sputnikn-chat-server) - High loaded and scalable chat server written with Akka/Ktor/Rest/WebSocket/Protobuf/Jooq;<br>
- [Client chat SDK](https://github.com/AlexanderShniperson/sputnikn-chat-client) - SDK client chat library for embedding in third-party applications written in Flutter;<br>
- [Sample application](https://github.com/AlexanderShniperson/sputnikn-chat-sample) - An example of a chat application using the SDK client library written with Flutter;<br>

### Build and use
Project builds with `all-in-one` included dependency libraries as `fat jar` and after build can be copied and used as standalone console application.<br>
To build run:<br>
`<project_dir>/gradlew clean shadowJar`<br>
Before use define system variables:<br>
```
JDBC_DRIVER=org.postgresql.Driver
JDBC_DATABASE_URL=jdbc:postgresql://localhost:5432/sputniknchat?user=postgres&password=ok
JDBC_POOL_SIZE=10
```
To use run:<br>
`java -jar <project_dir>/build/libs/sputnikn_chat_server-1.0-SNAPSHOT-all.jar`

### The rules
Avoid to send same `responseId` to all recipients, this may cause a problem at client side!