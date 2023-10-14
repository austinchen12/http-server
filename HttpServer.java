import java.io.*;
import java.net.*;
import java.util.*;


public class HttpServer {
    public static void main(String[] args) {
        int serverPort = 8080;

        ServerSocket listenSocket;
        try {
            listenSocket = new ServerSocket(serverPort);
        } catch (IOException e) {
            System.out.println("[ERROR] Could not listen on port " + serverPort);
            return;
        }

        System.out.println("[DEBUG] Server listening on port " + serverPort);

        while (true) {
            try
            {
                Socket connectionSocket = listenSocket.accept();
                HttpRequest request = new HttpRequest(connectionSocket);
                
                switch (request.method) {
                    case HttpMethod.GET:
                        System.out.println("[DEBUG] GET " + request.path);
                        break;
                    case HttpMethod.POST:
                        System.out.println("[DEBUG] POST " + request.path);
                        break;
                    default:
                        System.out.println("[ERROR] Unsupported method: " + request.method);
                        continue;
                }

                // Send response
                DataOutputStream out = new DataOutputStream(connectionSocket.getOutputStream());
                out.writeBytes("HTTP/1.0 200 OK\r\n");
                out.writeBytes("Date: " + new Date() + "\r\n");
                out.writeBytes("Server: Austin's Really Cool HTTP Server\r\n");
                out.writeBytes("Last-Modified: " + new Date() + "\r\n");
                out.writeBytes("Content-Type: text/plain\r\n");
                out.writeBytes("Content-Length: 5\r\n");
                // out.writeBytes("Transfer-Encoding: chunked\r\n")
                out.writeBytes("\r\n");
                out.writeBytes("Hello!\r\n");

                connectionSocket.close();
            } catch (Exception e) {
                System.out.println("[ERROR] Failed to process request: " + e.getMessage());
                continue;
            }
        }
    }
}