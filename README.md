# SputnikN Chat Server
A SputnikN chat server written with Kotlin and Akka

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