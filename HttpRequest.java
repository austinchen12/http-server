import java.io.*;
import java.net.*;
import java.util.*;
import java.nio.*;
import java.util.regex.Pattern;
import java.util.regex.Matcher;
import java.text.SimpleDateFormat;
import java.util.Locale;


public class HttpRequest {
    public HttpMethod method;
    public String path;
    public String version;
    public Map<String, String> headers = new HashMap<String, String>();
    public String body;
    public List<String> acceptTypes;
    public boolean isMobileUserAgent;
    public Date ifModifiedSinceDate;
    public boolean keepAlive = false;
    public String[] credentials;
    public Map<String, String> queryParams = new HashMap<String, String>();

    public static List<String> PossibleAcceptTypes = Arrays.asList("text/html", "text/plain", "application/json", "image/jpeg", "image/png", "image/gif", "application/pdf", "application/zip", "application/gzip", "application/x-tar", "application/x-bzip2", "application/x-rar-compressed", "application/x-7z-compressed", "audio/mpeg", "audio/ogg", "audio/wav", "audio/webm", "video/mp4", "video/ogg", "video/webm", "application/octet-stream");

    public HttpRequest(ByteBuffer in) throws Exception {
        String request = new String(in.array());
        String[] lines = request.split("\r\n");
        String[] sections = request.split("\r\n\r\n");
        int count = 0;
        
        // Extract method, path, version from request line
        String requestLine = lines[count++];
        String[] requestLineParts = requestLine.split(" ");
        this.method = HttpMethod.valueOf(requestLineParts[0]);
        this.path = extractQueryParams(requestLineParts[1]);
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

        try {
            processHeaders();
        } catch (Exception e) {
            System.out.println("[ERROR] Malformed headers: " + e.getMessage());
        }
    }

    private String extractQueryParams(String line) {
        int index = line.indexOf("?");
        if (index != -1) {
            String[] parts = line.substring(index + 1).split("&");
            for (String part : parts) {
                String[] keyValue = part.split("=");
                queryParams.put(keyValue[0], keyValue[1]);
            }
        }
        System.out.println(index + ", " + queryParams.size() + ", " + line + ", " + (index == -1 ? line : line.substring(0, index)));
        return index == -1 ? line : line.substring(0, index);
    }
    
    private void processHeaders() throws Exception {
        if (headers.containsKey("Accept")) {
            String[] parts = headers.get("Accept").split(", ");

            this.acceptTypes = new ArrayList<String>();

            int i = 0;
            while (i < parts.length) {
                if (PossibleAcceptTypes.contains(parts[i])) {
                    this.acceptTypes.add(parts[i++]);
                } else {
                    throw new Exception("Invalid accept type");
                }
            }
        }

        if (headers.containsKey("User-Agent")) {
            String[] mobileKeywords = {"Android", "iPhone", "Windows Phone", "Mobile"};

            // Create a regular expression pattern to match mobile keywords.
            String pattern = String.join("|", mobileKeywords);
            Pattern mobilePattern = Pattern.compile(pattern, Pattern.CASE_INSENSITIVE);

            // Use a Matcher to find a match in the User-Agent string.
            Matcher matcher = mobilePattern.matcher(headers.get("User-Agent"));

            this.isMobileUserAgent = matcher.find();
        }

        if (headers.containsKey("If-Modified-Since")) {
            this.ifModifiedSinceDate = Utils.parseFormattedDate(headers.get("If-Modified-Since"));
        }

        if (headers.containsKey("Connection")) {
            if (headers.get("Connection").equals("keep-alive")) {
                this.keepAlive = true;
            } else if (headers.get("Connection").equals("close")) {
                this.keepAlive = false;
            } else {
                throw new Exception("Invalid connection type");
            }
        }

        if (headers.containsKey("Authorization")) {
            String[] parts = headers.get("Authorization").split(" ");

            if (parts[0].equals("Basic")) {
                String decoded = new String(Base64.getDecoder().decode(parts[1]));
                this.credentials = decoded.split(":");
            } else {
                throw new Exception("Invalid authorization type");
            }
        }
    }
}