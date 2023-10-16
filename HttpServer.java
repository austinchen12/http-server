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

        // Create welcome socket and selector
        ServerSocketChannel listenChannel;
        Selector selector;
        try {
            listenChannel = ServerSocketChannel.open();
            ServerSocket listenSocket = listenChannel.socket();
            InetSocketAddress address = new InetSocketAddress(serverPort);
            listenSocket.bind(address);
            listenChannel.configureBlocking(false);
            selector = Selector.open();
            listenChannel.register(selector, SelectionKey.OP_ACCEPT);
        } catch (IOException ex) {
            System.out.println("[ERROR] Could not listen on port " + serverPort);
            return;
        }

        System.out.println("[DEBUG] Server listening on port " + serverPort);

        while (true) {
            // Block until at least one channel is ready
            try {
                selector.select();
            } catch (IOException ex) {
                System.out.println("[ERROR] Selector error: " + ex.getMessage());
                break;
            }

            // Handle all ready keys
            Set readyKeys = selector.selectedKeys();
            Iterator iterator = readyKeys.iterator();
            while (iterator.hasNext()) {
                SelectionKey key = (SelectionKey) iterator.next();
                iterator.remove();
                try {
                    if (key.isAcceptable()) {
                        ServerSocketChannel serverChannel = (ServerSocketChannel) key.channel();

                        // Create client channel
                        SocketChannel clientChannel = serverChannel.accept();
                        clientChannel.configureBlocking(false);

                        // Register client channel with selector
                        SelectionKey clientKey = clientChannel.register(selector, SelectionKey.OP_READ);

                        // Attach channel state
                        RequestState state = new RequestState();
                        clientKey.attach(state);
                    } else if ((key.readyOps() & SelectionKey.OP_READ) != 0) {
                        SocketChannel clientChannel = (SocketChannel) key.channel();
                        RequestState state = (RequestState) key.attachment();

                        // If connection timed out, send 408
                        if (System.currentTimeMillis() - state.connectionTime > 3000) {
                            System.out.println("[DEBUG] Connection timed out");
                            state.response = new HttpResponse(408, "Request Timeout");
                            key.interestOps(SelectionKey.OP_WRITE);
                            continue;
                        }

                        // Assuming 1024 bytes is enough
                        ByteBuffer bytes = ByteBuffer.allocate(1024);
                        int bytesRead = clientChannel.read(bytes);
                        state.appendInBytes(bytes, bytesRead);

                        // State.endsInDoubleCRLF is true if the request ends in \r\n\r\n, which should happen at most twice.
                        // Might happen twice if its a POST request and buffer happens to end right before the body starts.
                        // But not necessarily, so I need to process as if the body is included, and just ignore if we
                        // didn't catch double CRLF in a POST request.
                        if (state.endsInDoubleCRLF()) {
                            try {
                                HttpRequest request = new HttpRequest(state.in);
                                int numOfDoubleCRLF = state.getDoubleCRLFCount();

                                if (request.method == HttpMethod.POST && numOfDoubleCRLF == 1) {
                                    continue;
                                }

                                state.request = request;

                                // Prepare input buffer to read and handle request
                                state.in.flip();
                                state.response = handleRequest(clientChannel.socket(), request);
                            } catch(Exception e) {
                                System.out.println("[DEBUG] Failed to process request: " + e.getMessage());
                                state.response = new HttpResponse(500, "Internal Server Error");
                            }

                            // Prepare output buffer to read and switch to write
                            key.interestOps(SelectionKey.OP_WRITE);
                        }
                    } else if ((key.readyOps() & SelectionKey.OP_WRITE) != 0) {
                        SocketChannel clientChannel = (SocketChannel) key.channel();
                        RequestState state = (RequestState) key.attachment();

                        // Write response until empty, then close channel if timeout or not keep-alive
                        if (state.response.outputBuffer.hasRemaining()) {
                            clientChannel.write(state.response.outputBuffer);
                        } else {
                            if (state.response.statusCode == 408 || !state.request.keepAlive) {
                                clientChannel.close();
                            }
                        }
                    }
                } catch (IOException ex) {
                    System.out.println("[ERROR] Failed to process request: " + ex.getMessage());
                    key.cancel();
                    try {
                        key.channel().close();
                    } catch (IOException cex) {
                        System.out.println("[ERROR] Failed to close channel: " + cex.getMessage());
                    }
                }
            }
        }
    }

    private static HttpResponse handleRequest(Socket connectionSocket, HttpRequest request) throws Exception {
        int statusCode;
        String statusMessage;

        // Check virtual host is valid
        String virtualHostPath = virtualHosts.get(request.headers.get("Host"));
        if (virtualHostPath == null) {
            System.out.println("[DEBUG] No virtual host found for " + request.headers.get("Host"));
            return new HttpResponse(400, "Malformed Request");
        }

        // Make sure no relative path
        String[] parts = request.path.split("/");
        for (String part : parts) {
            if (part.equals("..")) {
                System.out.println("[DEBUG] Relative path not allowed");
                return new HttpResponse(400, "Malformed Request");
            }
        }

        // Add default file if path ends in /
        String path = System.getProperty("user.dir") + virtualHostPath + request.path;
        if (path.endsWith("/")) {
            File mobileIndex = new File(path + "index_m.html");
            if (request.isMobileUserAgent && mobileIndex.exists()) {
                path += "index_m.html";
            } else {
                path += "index.html";
            }
        }

        // Check if file exists and isn't a directory
        File file = new File(path);
        if (!file.exists() || file.isDirectory()) {
            System.out.println("[DEBUG] File not found: " + path);
            return new HttpResponse(404, "Not Found");
        }

        // Get content type
        Path filePath = Path.of(path);
        String contentType;
        try {
            contentType = file.canExecute() ? "text/plain" : Files.probeContentType(filePath);
        } catch (Exception e) {
            System.out.println("[DEBUG] Failed to get content type: " + e.getMessage());
            return new HttpResponse(400, "Malformed Request");
        }

        // Check if content type is accepted
        if (!request.acceptTypes.contains(contentType)) {
            System.out.println("[DEBUG] Content type not accepted by user: " + path + ", " + contentType);
            return new HttpResponse(406, "Not Acceptable");
        }

        // Check if file is modified since If-Modified-Since
        long lastModifiedMillis = file.lastModified();
        Date lastModifiedDate = new Date(lastModifiedMillis);
        if (!file.canExecute() && request.ifModifiedSinceDate != null && lastModifiedDate.before(request.ifModifiedSinceDate)) {
            System.out.println("[DEBUG] File not modified since " + request.ifModifiedSinceDate + ": " + path);
            return new HttpResponse(304, "Not Modified");
        }

        // Check if file has authentication requirements
        File htaccess = new File(file.getParent() + "/.htaccess");
        if (htaccess.exists()) {
            BufferedReader reader = new BufferedReader(new FileReader(htaccess));
            String line;
            String authName = null, user = null, password = null;
            while ((line = reader.readLine()) != null) {
                String[] keyValuePair = line.split(" ");
                if (keyValuePair[0].equals("AuthName")) {
                    authName = keyValuePair[1];
                } else if (keyValuePair[0].equals("User")) {
                    user = keyValuePair[1];
                } else if (keyValuePair[0].equals("Password")) {
                    password = keyValuePair[1];
                }
            }

            // If .htaccess file is invalidly formatted, ignore
            boolean validAuthConfig = authName != null && user != null && password != null;
            if (validAuthConfig && (request.credentials == null || !request.credentials[0].equals(user) || !request.credentials[1].equals(password))) {
                System.out.println("[DEBUG] Invalid credentials");
                return new HttpResponse(401, "Unauthorized", authName);
            }
        }
        
        int contentLength = 0;
        byte[] contentBytes = null;
        switch (request.method) {
            case HttpMethod.GET:
                if (file.canExecute()) {
                    contentBytes = executeDynamicFile(request, connectionSocket, path);
                    if (contentBytes == null) {
                        return new HttpResponse(400, "Bad Request");
                    }
                    
                    contentLength = contentBytes.length;

                    return new HttpResponse(200, "OK", lastModifiedDate, contentType, contentBytes);
                } else {
                    contentLength = (int) file.length();
                    FileInputStream fileIn = new FileInputStream(file);
                    contentBytes = new byte[contentLength];
                    fileIn.read(contentBytes);

                    return new HttpResponse(200, "OK", lastModifiedDate, contentType, contentBytes);
                }
            case HttpMethod.POST:
                if (!file.canExecute()) {
                    System.out.println("[DEBUG] File not executable: " + path);
                    return new HttpResponse(500, "Internal Server Error");
                }
                
                contentBytes = executeDynamicFile(request, connectionSocket, path, request.body);
                if (contentBytes == null) {
                    return new HttpResponse(400, "Bad Request");
                }

                contentLength = contentBytes.length;

                return new HttpResponse(201, "Created");
            case HttpMethod.DELETE:
                if (!file.canExecute()) {
                    System.out.println("[DEBUG] File not executable: " + path);
                    return new HttpResponse(500, "Internal Server Error");
                }

                contentBytes = executeDynamicFile(request, connectionSocket, path);
                if (contentBytes == null) {
                    return new HttpResponse(400, "Bad Request");
                }

                contentLength = contentBytes.length;
                
                return new HttpResponse(204, "No Content");
            default:
                System.out.println("[DEBUG] Unsupported method: " + request.method);
                return new HttpResponse(501, "Not Implemented");
        }
    }

    private static byte[] executeDynamicFile(HttpRequest request, Socket connectionSocket, String... args) throws Exception {
        ProcessBuilder pb = new ProcessBuilder(args);
        Map<String, String> environment = pb.environment();
        environment.put("REQUEST_METHOD", request.method.toString());
        environment.put("REMOTE_ADDR", connectionSocket.getInetAddress().getHostAddress());
        environment.put("REMOTE_PORT", Integer.toString(connectionSocket.getPort()));
        environment.put("SERVER_NAME", Utils.ServerName);

        Process p = pb.start();
        InputStream is = p.getInputStream();
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        int nRead;
        byte[] data = new byte[1024];
        while ((nRead = is.read(data, 0, data.length)) != -1) {
            buffer.write(data, 0, nRead);
        }
        buffer.flush();

        int exitCode = p.waitFor();

        return exitCode == 0 ? buffer.toByteArray() : null;
    }
}