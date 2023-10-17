compile: javac \*.java -d out
run server: java -classpath out HttpServer -config server.conf
run client: java -classpath out HttpClient

Only supports DocumentRoot and ServerName for virtual hosts in config file, and only one port is supported

CODE STRUCTURE

- HttpServer.java: Main thread to start select loops + UI monitor thread
- HttpClient.java: Test client
- HttpDispatch.java: Worker threads running select loops
- HttpRequest.java: HttpRequest object, also handles parsing of query parameters and headers
- HttpResponse.java: HttpResponse object
- Utils.java: Utility functions + constants
- server.conf: configuration file
- web: contains all files for web host
- mobile: contains all files for mobile host
- out: compiled class files
- HttpMethod.java: Enum class with HttpMethods
- ApacheConfigParser.java + ConfigNode.java: used for parsing configuration

SKIPPING

- origin-form, absolute-form, authority-form, asterisk-form
- qvalues + wildcard
- realistic file types: html file can also be plain text but we dont care
- close + keep alive
- general validation of header values
- transfer encoding
