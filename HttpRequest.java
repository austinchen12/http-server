import java.io.*;
import java.net.*;
import java.util.*;
import java.nio.*;


public class HttpRequest {
    public HttpMethod method;
    public String path;
    public String version;
    public Map<String, String> headers = new HashMap<String, String>();
    public String body;

    public HttpRequest(ByteBuffer in) throws Exception {
        String request = new String(in.array());
        String[] lines = request.split("\r\n");
        String[] sections = request.split("\r\n\r\n");
        int count = 0;
        
        // Extract method, path, version from request line
        String requestLine = lines[count++];
        String[] requestLineParts = requestLine.split(" ");
        this.method = HttpMethod.valueOf(requestLineParts[0]);
        this.path = requestLineParts[1];
        this.version = requestLineParts[2];

        // Read headers
        String line;
        while ((line = lines[count++]) != null) {
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
                bytes[i] = (byte) sections[1].charAt(i);
            }
            body = new String(bytes);
        }
    }
}