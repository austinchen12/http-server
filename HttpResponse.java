import java.util.*;
import java.nio.*;
import java.nio.charset.StandardCharsets;


public class HttpResponse {
    public int statusCode;
    public String statusMessage;
    public String contentType;
    public Date lastModifiedDate;
    public byte[] contentBytes;
    public String unauthorizedRealm = null;

    public ByteBuffer outputBuffer;

    public HttpResponse(int statusCode, String statusMessage) {
        this.statusCode = statusCode;
        this.statusMessage = statusMessage;

        this.outputBuffer = toByteBuffer();
    }

    public HttpResponse(int statusCode, String statusMessage, String unauthorizedRealm) {
        this.statusCode = statusCode;
        this.statusMessage = statusMessage;
        this.unauthorizedRealm = unauthorizedRealm;

        this.outputBuffer = toByteBuffer();
    }

    public HttpResponse(int statusCode, String statusMessage, Date lastModifiedDate, String contentType, byte[] contentBytes) {
        this.statusCode = statusCode;
        this.statusMessage = statusMessage;
        this.lastModifiedDate = lastModifiedDate;
        this.contentType = contentType;
        this.contentBytes = contentBytes;

        this.outputBuffer = toByteBuffer();
    }

    public HttpResponse(int statusCode, String statusMessage, String contentType, byte[] contentBytes) {
        this.statusCode = statusCode;
        this.statusMessage = statusMessage;
        this.contentType = contentType;
        this.contentBytes = contentBytes;

        this.outputBuffer = toByteBuffer();
    }

    private ByteBuffer toByteBuffer() {
        ByteBuffer buffer = ByteBuffer.allocate(1024);

        // Add headers in string builder
        StringBuilder sb = new StringBuilder();
        sb.append("HTTP/1.1 " + this.statusCode + " " + this.statusMessage + "\r\n");
        sb.append("Date: " + Utils.getFormattedDate(new Date()) + "\r\n");
        sb.append("Server: Austin's Really Cool HTTP Server\r\n");
        if (this.lastModifiedDate != null) {
            sb.append("Last-Modified: " + Utils.getFormattedDate(this.lastModifiedDate) + "\r\n");
        }
        if (this.contentBytes != null) {
            sb.append("Content-Type: " + this.contentType + "\r\n");
            sb.append("Content-Length: " + this.contentBytes.length + "\r\n");
        }
        if (this.unauthorizedRealm != null) {
            sb.append("WWW-Authenticate: Basic realm=" + this.unauthorizedRealm + "\r\n");
        }
        sb.append("\r\n");

        // Add string builder to buffer
        buffer.put(sb.toString().getBytes(StandardCharsets.UTF_8));

        // Append content to buffer
        if (this.contentBytes != null) {
            buffer.put(this.contentBytes);
        }

        // Flip buffer
        buffer.flip();

        return buffer;
    }
}