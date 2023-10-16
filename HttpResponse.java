import java.util.*;
import java.nio.*;
import java.nio.charset.StandardCharsets;


public class HttpResponse {
    public int statusCode;
    public String statusMessage;
    public String contentType;
    public Date lastModifiedDate;
    public byte[] contentBytes;

    public ByteBuffer outputBuffer;

    public HttpResponse(int statusCode, String statusMessage) {
        this.statusCode = statusCode;
        this.statusMessage = statusMessage;

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
        sb.append("HTTP/1.1 " + statusCode + " " + statusMessage + "\r\n");
        sb.append("Date: " + Utils.getFormattedDate(new Date()) + "\r\n");
        sb.append("Server: Austin's Really Cool HTTP Server\r\n");
        if (lastModifiedDate != null) {
            sb.append("Last-Modified: " + Utils.getFormattedDate(lastModifiedDate) + "\r\n");
        }
        if (contentBytes != null) {
            sb.append("Content-Type: " + contentType + "\r\n");
            sb.append("Content-Length: " + contentBytes.length + "\r\n");
        }
        sb.append("\r\n");

        // Add string builder to buffer
        buffer.put(sb.toString().getBytes(StandardCharsets.UTF_8));

        // Append content to buffer
        if (contentBytes != null) {
            buffer.put(contentBytes);
        }

        // Flip buffer
        buffer.flip();

        return buffer;
    }
}