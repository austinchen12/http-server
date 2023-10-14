compile: javac \*.java -d out
run server: java -classpath out HttpServer -config server.conf
run client: java -classpath out HttpClient

Only supports DocumentRoot and ServerName for virtual hosts in config file, and only one port is supported
