import java.io.*;
import java.net.*;
import java.util.*;

public class HttpClient {
    public Map<String, String> configuration = new HashMap<String, String>();

    public static void main(String[] args) {
        try {
            Socket socket = new Socket("localhost", 6789);

            // Send request
            DataOutputStream out = new DataOutputStream(socket.getOutputStream());
            out.writeBytes("GET / HTTP/1.0\r\n");
            // out.writeBytes("POST / HTTP/1.0\r\n");
            // out.writeBytes("Content-Type: application/x-www-form-urlencoded\r\n");
            // out.writeBytes("Content-Length: 11\r\n");
            out.writeBytes("Host: cicada.cs.yale.edu\r\n");
            out.writeBytes("Accept: text/plain, text/html, text/*\r\n");
            out.writeBytes("User-Agent: MyHttpClient/1.0\r\n");
            out.writeBytes("If-Modified-Since: " + new Date() + "\r\n");
            out.writeBytes("Connection: close\r\n");
            out.writeBytes("Authorization: Basic " + Base64.getEncoder().encodeToString("username:password".getBytes()) + "\r\n");
            out.writeBytes("\r\n");

            // Write body
            // out.writeBytes("item1=A&item2=B\r\n");
            // out.writeBytes("\r\n");

            // Get response
            BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            String line;
            while ((line = in.readLine()) != null) {
                System.out.println(line);
            }

            // Close
            socket.close();
        } catch(IOException e) {
            System.out.println("Socket error: " + e.getMessage());
        }    
    }
}