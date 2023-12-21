import java.io.*;
import java.net.*;
import java.util.*;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.text.SimpleDateFormat;
import java.util.Locale;
import java.nio.file.Path;
import java.nio.file.Paths;

class Utils {
    public static int MaxConnectionsPerThread = 2;
    public static String ServerName = "Austin's Really Cool HTTP Server";
    public static int TimeoutLength = 3000;

    public static List<Map<String, String>> loadConfiguration(String path) throws Exception {
        List<Map<String, String>> result = new ArrayList<>();

        Map<String, String> config =  new HashMap<String, String>();
        Map<String, String> virtualHosts = new HashMap<String, String>();
        
        InputStream inputStream = new FileInputStream(new File(path));

        ApacheConfigParser parser = new ApacheConfigParser();
        ConfigNode root = parser.parse(inputStream);

        // Create queue
        Stack<ConfigNode> queue = new Stack<ConfigNode>();
        for (ConfigNode child : root.getChildren()) {
            queue.push(child);
        }

        // Populate config
        while (!queue.empty()) {
            ConfigNode node = queue.remove(0);

            if (node.getName().equals("VirtualHost")) {
                List<ConfigNode> children = node.getChildren();
                String serverName = children.get(0).getName().equals("ServerName") ? children.get(0).getContent() : children.get(1).getContent();
                String documentRoot = children.get(0).getName().equals("ServerName") ? children.get(1).getContent() : children.get(0).getContent();
                
                // Convert path to absolutePath and check if its inside CWD
                Path absolutePath = Paths.get(documentRoot).toAbsolutePath().normalize();
                if (!absolutePath.startsWith(Paths.get(System.getProperty("user.dir")))) {
                    throw new Exception("DocumentRoot must be inside current directory");
                }

                // Remove CWD part of path
                documentRoot = absolutePath.toString().substring(System.getProperty("user.dir").length());
                if (virtualHosts.size() == 0) {
                    virtualHosts.put("__DEFAULT__", documentRoot);
                }
                virtualHosts.put(serverName, documentRoot);
                continue;
            } else {
                config.put(node.getName(), node.getContent());
            }
        }

        result.add(config);
        result.add(virtualHosts);
        return result;
    }

    public static String getFormattedDate(Date date) {
        SimpleDateFormat dateFormat = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss 'GMT'", Locale.ENGLISH);
        return dateFormat.format(date);
    }

    public static Date parseFormattedDate(String date) throws Exception {
        SimpleDateFormat dateFormat = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss 'GMT'", Locale.ENGLISH);
        return dateFormat.parse(date);
    }
}