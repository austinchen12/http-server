compile: javac \*.java -d out
run server: java -classpath out HttpServer
run client: java -classpath out HttpClient
