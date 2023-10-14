import java.io.*;
import java.net.*;
import java.util.*;


public class HttpServer {
    public static Map<String, String> config = new HashMap<String, String>();
    public static Map<String, String> virtualHosts = new HashMap<String, String>();

    public static void main(String[] args) {
        // Accept one argument -config <path to config file>
        if (args.length != 2 || !args[0].equals("-config")) {
            System.out.println("[ERROR] Usage: java HttpServer -config <path to config file>");
            return;
        }
        
        try {
            loadConfiguration(args[1]);
        } catch (Exception e) {
            System.out.println("[ERROR] Failed to load configuration: " + e.getMessage());
        }
        
        int serverPort = Integer.parseInt(config.get("Listen"));

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
                        String virtualHostPath = virtualHosts.get(request.headers.get("Host"));
                        if (virtualHostPath == null) {
                            System.out.println("[ERROR] No virtual host found for " + request.headers.get("Host"));
                            continue;
                        }

                        System.out.println("[DEBUG] GET " + virtualHostPath + request.path);
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

    private static void loadConfiguration(String path) throws Exception {
        InputStream inputStream = new FileInputStream(new File(path));

        ApacheConfigParser parser = new ApacheConfigParser();
        ConfigNode root = parser.parse(inputStream);

        // Create stack
        Stack<ConfigNode> stack = new Stack<ConfigNode>();
        for (ConfigNode child : root.getChildren()) {
            stack.push(child);
        }

        // Populate config
        while (!stack.empty()) {
            ConfigNode node = stack.pop();

            if (node.getName().equals("VirtualHost")) {
                List<ConfigNode> children = node.getChildren();
                String serverName = children.get(0).getName().equals("ServerName") ? children.get(0).getContent() : children.get(1).getContent();
                String documentRoot = children.get(0).getName().equals("ServerName") ? children.get(1).getContent() : children.get(0).getContent();
                virtualHosts.put(serverName, documentRoot);
                continue;
            } else {
                config.put(node.getName(), node.getContent());
            }
        }
    }
}