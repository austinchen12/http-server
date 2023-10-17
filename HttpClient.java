import java.io.*;
import java.net.*;
import java.util.*;


public class HttpClient {
    public Map<String, String> configuration = new HashMap<String, String>();

    public static void main(String[] args) {
        if (args.length == 0) {
            System.out.println("Usage: java HttpClient <method> <url>");
            return;
        }

        String method = args[0];
        String url = args[1];

        try {
            if (method.equals("GET")) {
                sendGetRequest(url);
            } else if (method.equals("POST")) {
                sendPostRequest(url);
            } else if (method.equals("DELETE")) {
                sendDeleteRequest(url);
            } else {
                System.out.println("Unsupported method: " + method);
                return;
            }
        } catch(IOException e) {
            System.out.println("Socket error: " + e.getMessage());
        } catch (Exception e) {
            System.out.println("Error: " + e.getMessage());
        }
    }

    private static void sendGetRequest(String url) throws Exception {
        Socket socket = new Socket("localhost", 6789);

        Thread.sleep(1000);
        
        // Send request
        try {
            DataOutputStream out = new DataOutputStream(socket.getOutputStream());
            out.writeBytes("GET " + url + " HTTP/1.0\r\n");
            out.writeBytes("Host: cicada.cs.yale.edu\r\n");
            out.writeBytes("Accept: text/plain, text/html\r\n");
            out.writeBytes("User-Agent: iPhone/1.0\r\n");
            // out.writeBytes("If-Modified-Since: " + Utils.getFormattedDate(new Date()) + "\r\n");
            out.writeBytes("Connection: close\r\n");
            out.writeBytes("Authorization: Basic " + Base64.getEncoder().encodeToString("username:password".getBytes()) + "\r\n");
            out.writeBytes("\r\n");
        } catch (Exception e) {
            System.out.println("Can't write: " + e.getMessage());
        }
        
        // Get response
        BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
        String line;
        while ((line = in.readLine()) != null) {
            System.out.println(line);
        }

        // Close
        socket.close();
    }

    private static void sendPostRequest(String url) throws Exception {
        Socket socket = new Socket("localhost", 6789);
        String message = "super secret";

        // Send request
        DataOutputStream out = new DataOutputStream(socket.getOutputStream());
        out.writeBytes("POST " + url + " HTTP/1.0\r\n");
        out.writeBytes("Content-Type: application/x-www-form-urlencoded\r\n");
        out.writeBytes("Content-Length: " + message.length() + "\r\n");
        out.writeBytes("Host: cicada.cs.yale.edu\r\n");
        out.writeBytes("Accept: text/plain, text/html\r\n");
        out.writeBytes("User-Agent: MyHttpClient/1.0\r\n");
        out.writeBytes("If-Modified-Since: " + Utils.getFormattedDate(new Date()) + "\r\n");
        out.writeBytes("Connection: close\r\n");
        out.writeBytes("Authorization: Basic " + Base64.getEncoder().encodeToString("username:password".getBytes()) + "\r\n");
        out.writeBytes("\r\n");

        // Write body
        out.writeBytes(message + "\r\n");
        out.writeBytes("\r\n");

        // Get response
        BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
        String line;
        while ((line = in.readLine()) != null) {
            System.out.println(line);
        }

        // Close
        socket.close();
    }

    private static void sendDeleteRequest(String url) throws Exception {
        Socket socket = new Socket("localhost", 6789);

        // Send request
        DataOutputStream out = new DataOutputStream(socket.getOutputStream());
        out.writeBytes("DELETE " + url + " HTTP/1.0\r\n");
        out.writeBytes("Host: cicada.cs.yale.edu\r\n");
        out.writeBytes("Accept: text/plain, text/html\r\n");
        out.writeBytes("User-Agent: MyHttpClient/1.0\r\n");
        out.writeBytes("If-Modified-Since: " + Utils.getFormattedDate(new Date()) + "\r\n");
        out.writeBytes("Connection: close\r\n");
        out.writeBytes("Authorization: Basic " + Base64.getEncoder().encodeToString("username:password".getBytes()) + "\r\n");
        out.writeBytes("\r\n");

        // Get response
        BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
        String line;
        while ((line = in.readLine()) != null) {
            System.out.println(line);
        }

        // Close
        socket.close();
    }
}