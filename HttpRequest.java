import java.io.*;
import java.net.*;
import java.util.*;


public class HttpRequest {
    private Socket connection;
    public HttpMethod method;
    public String path;
    public String version;
    public Map<String, String> headers = new HashMap<String, String>();
    public String body;

    public HttpRequest(Socket connection) throws Exception {
        this.connection = connection;

        BufferedReader in = new BufferedReader(new InputStreamReader(connection.getInputStream()));
        
        // Extract method, path, version from request line
        String requestLine = in.readLine();
        String[] requestLineParts = requestLine.split(" ");
        this.method = HttpMethod.valueOf(requestLineParts[0]);
        this.path = requestLineParts[1];
        this.version = requestLineParts[2];

        // Read headers
        String line;
        while ((line = in.readLine()) != null) {
            if (line.isEmpty()) {
                break;
            }
            String[] headerParts = line.split(": ");
            headers.put(headerParts[0], headerParts[1]);
        }

        // If Content-Length is present, read body
        if (headers.containsKey("Content-Length")) {
            int contentLength = Integer.parseInt(headers.get("Content-Length"));
            byte[] bytes = new byte[contentLength];
            for (int i = 0; i < contentLength; i++) {
                bytes[i] = (byte) in.read();
            }
            body = new String(bytes);
        }
    }
}