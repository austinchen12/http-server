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
        
        try {
            List<Map<String, String>> result = Utils.loadConfiguration(args[1]);
            config = result.get(0);
            virtualHosts = result.get(1);
        } catch (Exception e) {
            System.out.println("[ERROR] Failed to load configuration: " + e.getMessage());
        }
        
        int serverPort = Integer.parseInt(config.get("Listen"));

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
            try {
                selector.select();
            } catch (IOException ex) {
                ex.printStackTrace();
                break;
            }

            Set readyKeys = selector.selectedKeys();
            Iterator iterator = readyKeys.iterator();

            while (iterator.hasNext()) {
                SelectionKey key = (SelectionKey) iterator.next();
                iterator.remove();
                try {
                    if (key.isAcceptable()) {
                        ServerSocketChannel serverChannel = (ServerSocketChannel) key.channel();
                        SocketChannel clientChannel = serverChannel.accept();

                        clientChannel.configureBlocking(false);

                        SelectionKey clientKey = clientChannel.register(selector, SelectionKey.OP_READ);
                        RequestState state = new RequestState();
                        clientKey.attach(state);

                    } else if ((key.readyOps() & SelectionKey.OP_READ) != 0) {
                        SocketChannel clientChannel = (SocketChannel) key.channel();
                        RequestState state = (RequestState) key.attachment();

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

                                state.in.flip();
                                handleRequest(clientChannel.socket(), request, state.out);
                                state.request = request;
                            } catch(Exception e) {
                                System.out.println("[ERROR] Failed to process request: " + e.getMessage());
                            }

                            state.out.flip();
                            key.interestOps(SelectionKey.OP_WRITE);
                        }
                    } else if ((key.readyOps() & SelectionKey.OP_WRITE) != 0) {
                        SocketChannel clientChannel = (SocketChannel) key.channel();
                        RequestState state = (RequestState) key.attachment();

                        if (state.out.hasRemaining()) {
                            clientChannel.write(state.out);
                        } else if (!state.request.keepAlive) {
                            clientChannel.close();
                        }
                    }
                } catch (IOException ex) {
                    key.cancel();
                    try {
                        key.channel().close();
                    } catch (IOException cex) {}
                }
            }
        }
    }

    private static void handleRequest(Socket connectionSocket, HttpRequest request, ByteBuffer out) throws Exception {
        String virtualHostPath = virtualHosts.get(request.headers.get("Host"));
        if (virtualHostPath == null) {
            System.out.println("[ERROR] No virtual host found for " + request.headers.get("Host"));
            return;
        }

        // Make sure no relative path
        String[] parts = request.path.split("/");
        for (String part : parts) {
            if (part.equals("..")) {
                System.out.println("[ERROR] Relative path not allowed");
                return;
            }
        }

        String path = System.getProperty("user.dir") + virtualHostPath + request.path;
        if (path.endsWith("/")) {
            File mobileIndex = new File(path + "index_m.html");
            if (request.isMobileUserAgent && mobileIndex.exists()) {
                path += "index_m.html";
            } else {
                path += "index.html";
            }
        }

        File file = new File(path);
        if (!file.exists()) {
            System.out.println("[ERROR] File not found: " + path);
            return;
        }

        Path filePath = Path.of(path);
        String contentType;
        try {
            contentType = file.canExecute() ? "text/plain" : Files.probeContentType(filePath);
        } catch (Exception e) {
            System.out.println("[ERROR] Failed to get content type: " + e.getMessage());
            return;
        }

        if (!request.acceptTypes.contains(contentType)) {
            System.out.println("[ERROR] Content type not accepted by user: " + path + ", " + contentType);
            return;
        }

        long lastModifiedMillis = file.lastModified();
        Date lastModifiedDate = new Date(lastModifiedMillis);
        if (!file.canExecute() && request.ifModifiedSinceDate != null && lastModifiedDate.before(request.ifModifiedSinceDate)) {
            System.out.println("[DEBUG] File not modified since " + request.ifModifiedSinceDate + ": " + path);
            return; // TODO: 304 status code
        }

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
                System.out.println("[ERROR] Invalid credentials");
                return; // return authName
            }
        }
        
        int contentLength = 0;
        byte[] contentBytes = null;
        switch (request.method) {
            case HttpMethod.GET:
                if (file.canExecute()) {
                    ProcessBuilder pb = new ProcessBuilder(path);
                    Map<String, String> environment = pb.environment();
                    environment.put("REQUEST_METHOD", request.method.toString());
                    environment.put("REMOTE_ADDR", connectionSocket.getInetAddress().getHostAddress());
                    environment.put("REMOTE_PORT", Integer.toString(connectionSocket.getPort()));
                    environment.put("SERVER_NAME", "Austin's Really Cool HTTP Server");

                    Process p = pb.start();
                    InputStream is = p.getInputStream();
                    ByteArrayOutputStream buffer = new ByteArrayOutputStream();
                    int nRead;
                    byte[] data = new byte[1024];
                    while ((nRead = is.read(data, 0, data.length)) != -1) {
                        buffer.write(data, 0, nRead);
                    }
                    buffer.flush();

                    contentBytes = buffer.toByteArray();
                    contentLength = contentBytes.length;
                } else {
                    contentLength = (int) file.length();
                    FileInputStream fileIn = new FileInputStream(file);
                    contentBytes = new byte[contentLength];
                    fileIn.read(contentBytes);
                }

                break;
            case HttpMethod.POST:
                if (file.canExecute()) {
                    ProcessBuilder pb = new ProcessBuilder(path, request.body);
                    Map<String, String> environment = pb.environment();
                    environment.put("REQUEST_METHOD", request.method.toString());
                    environment.put("REMOTE_ADDR", connectionSocket.getInetAddress().getHostAddress());
                    environment.put("REMOTE_PORT", Integer.toString(connectionSocket.getPort()));
                    environment.put("SERVER_NAME", "Austin's Really Cool HTTP Server");

                    Process p = pb.start();
                    InputStream is = p.getInputStream();
                    ByteArrayOutputStream buffer = new ByteArrayOutputStream();
                    int nRead;
                    byte[] data = new byte[1024];
                    while ((nRead = is.read(data, 0, data.length)) != -1) {
                        buffer.write(data, 0, nRead);
                    }
                    buffer.flush();

                    contentBytes = buffer.toByteArray();
                    contentLength = contentBytes.length;
                } else {
                    // TODO: do something
                }

                break;
            case HttpMethod.DELETE:
                ProcessBuilder pb = new ProcessBuilder(path);
                Map<String, String> environment = pb.environment();
                environment.put("REQUEST_METHOD", request.method.toString());
                environment.put("REMOTE_ADDR", connectionSocket.getInetAddress().getHostAddress());
                environment.put("REMOTE_PORT", Integer.toString(connectionSocket.getPort()));
                environment.put("SERVER_NAME", "Austin's Really Cool HTTP Server");

                Process p = pb.start();
                InputStream is = p.getInputStream();
                ByteArrayOutputStream buffer = new ByteArrayOutputStream();
                int nRead;
                byte[] data = new byte[1024];
                while ((nRead = is.read(data, 0, data.length)) != -1) {
                    buffer.write(data, 0, nRead);
                }
                buffer.flush();

                contentBytes = buffer.toByteArray();
                contentLength = contentBytes.length;
                break;
            default:
                System.out.println("[ERROR] Unsupported method: " + request.method);
                return;
        }

        // Send response
        out.put("HTTP/1.0 200 OK\r\n".getBytes(StandardCharsets.UTF_8));
        out.put(("Date: " + Utils.getFormattedDate(new Date()) + "\r\n").getBytes(StandardCharsets.UTF_8));
        out.put(("Server: Austin's Really Cool HTTP Server\r\n").getBytes(StandardCharsets.UTF_8));
        out.put(("Last-Modified: " + Utils.getFormattedDate(lastModifiedDate) + "\r\n").getBytes(StandardCharsets.UTF_8));
        out.put(("Content-Type: "+ contentType + "\r\n").getBytes(StandardCharsets.UTF_8));
        out.put(("Content-Length: " + contentLength + "\r\n").getBytes(StandardCharsets.UTF_8));
        out.put("\r\n".getBytes(StandardCharsets.UTF_8));

        out.put(contentBytes);
    }
}