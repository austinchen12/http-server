import java.io.*;
import java.net.*;
import java.util.*;
import java.nio.*;
import java.nio.channels.*;


public class RequestState {
    public HttpRequest request;
    public HttpResponse response;
    public ByteBuffer in;
    public ByteBuffer body;
    public long connectionTime;
    public boolean doneReading;

    public RequestState() {
        in = ByteBuffer.allocate(1024);
        connectionTime = System.currentTimeMillis();
        doneReading = false;
    }

    public ByteBuffer getInBytes() {
        return in;
    }

    public void appendInBytes(ByteBuffer bytes, int length) throws Exception {
        byte[] array = bytes.array();

        if (request == null) {
            // Still reading headers
            in.put(array, 0, length);

            // If we find doubleCRLF then its end of headers
            int doubleCRLFIndex = getDoubleCRLFIndex(array);
            if (doubleCRLFIndex != -1) {
                request = new HttpRequest(in);
                
                if (request.method == HttpMethod.GET) {
                    doneReading = true;
                } else {
                    // Start reading body
                    body = ByteBuffer.allocate(Integer.parseInt(request.headers.get("Content-Length")));
                    
                    // Read bytes from array into body
                    readBodyBytes(Arrays.copyOfRange(array, doubleCRLFIndex + 4, length));
                }
            }
        } else if (body != null) {
            // Still reading body
            readBodyBytes(array);
        } else {
            assert false;
        }
        
    }

    private void readBodyBytes(byte[] array) {
        for (int i = 0; i < array.length; i++) {
            body.put(array[i]);
            if (i == body.capacity() - 1) {
                doneReading = true;
            }
        }
    }

    private int getDoubleCRLFIndex(byte[] array) {
        for (int i = 0; i < array.length - 3; i++) {
            if (array[i] == '\r' && array[i + 1] == '\n' && array[i + 2] == '\r' && array[i + 3] == '\n') {
                return i;
            }
        }
        return -1;
    }
}