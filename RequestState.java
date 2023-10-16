import java.io.*;
import java.net.*;
import java.util.*;
import java.nio.*;
import java.nio.channels.*;


public class RequestState {
    public HttpRequest request;
    public HttpResponse response;
    public ByteBuffer in;
    public long connectionTime;

    public RequestState() {
        in = ByteBuffer.allocate(1024);
        connectionTime = System.currentTimeMillis();
    }

    public ByteBuffer getInBytes() {
        return in;
    }

    public void appendInBytes(ByteBuffer bytes, int length) {
        in.put(bytes.array(), 0, length);
    }

    public boolean endsInDoubleCRLF() {
        return in.get(in.position() - 4) == '\r' &&
               in.get(in.position() - 3) == '\n' &&
               in.get(in.position() - 2) == '\r' &&
               in.get(in.position() - 1) == '\n';
    }

    public int getDoubleCRLFCount() {
        int count = 0;
        int bytesRead = in.position();

        for (int i = 0; i < bytesRead - 3; i++) {
            if (in.get(i) == '\r' && in.get(i + 1) == '\n' &&
                in.get(i + 2) == '\r' && in.get(i + 3) == '\n') {
                count++;
                // Skip ahead to avoid overlapping matches
                i += 3;
            }
        }

        return count;
    }
}