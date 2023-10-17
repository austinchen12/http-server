import java.io.*;
import java.net.*;
import java.util.*;
import java.nio.*;
import java.nio.channels.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;


public class HttpServer {
    public static Map<String, String> config;
    public static Map<String, String> virtualHosts;

    public static void main(String[] args) {
        // Accept one argument -config <path to config file>
        if (args.length != 2 || !args[0].equals("-config")) {
            System.out.println("[ERROR] Usage: java HttpServer -config <path to config file>");
            return;
        }
        
        // Load configuration
        try {
            List<Map<String, String>> result = Utils.loadConfiguration(args[1]);
            config = result.get(0);
            virtualHosts = result.get(1);
        } catch (Exception e) {
            System.out.println("[ERROR] Failed to load configuration: " + e.getMessage());
        }
        
        int serverPort = Integer.parseInt(config.get("Listen"));
        int nSelectLoops = Integer.parseInt(config.get("nSelectLoops"));
        Thread[] threads = new Thread[nSelectLoops];

        // Create welcome socket
        ServerSocketChannel listenChannel;
        try {
            listenChannel = ServerSocketChannel.open();
            ServerSocket listenSocket = listenChannel.socket();
            InetSocketAddress address = new InetSocketAddress(serverPort);
            listenSocket.bind(address);
            listenChannel.configureBlocking(false);
        } catch (IOException ex) {
            System.out.println("[ERROR] Could not listen on port " + serverPort);
            return;
        }

        System.out.println("[DEBUG] Server listening on port " + serverPort);

        try {
            for (int i = 0; i < nSelectLoops; i++) {
                threads[i] = new Thread(new HttpDispatch(i + 1, listenChannel, config, virtualHosts));
                threads[i].start();
            }
        } catch (Exception e) {
            System.out.println("[ERROR] Could not start worker threads: " + e.getMessage());
            return;
        }

        System.out.println("[DEBUG] Worker threads started. Enter shutdown command to finish existing requests and stop the server.");
        
        // Monitor thread for shutdown command
        Scanner scanner = new Scanner(System.in);
        while (true) {
            String input = scanner.nextLine();
            if (input.equals("shutdown")) {
                System.out.println("[DEBUG] Shutting down server...");
                HttpDispatch.requestShutdown();
                break;
            }
        }
    }
}