export JDBC_DRIVER="org.postgresql.Driver"
export JDBC_DATABASE_URL="jdbc:postgresql://localhost:5432/sputniknchat?user=postgres&password=ok"
export JDBC_POOL_SIZE=10

java -jar ./build/libs/sputnikn_chat_server-1.0-SNAPSHOT-all.jar mediaPath=./media