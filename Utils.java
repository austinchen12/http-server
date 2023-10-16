import java.io.*;
import java.net.*;
import java.util.*;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.text.SimpleDateFormat;
import java.util.Locale;

class Utils {
    public static List<Map<String, String>> loadConfiguration(String path) throws Exception {
        List<Map<String, String>> result = new ArrayList<>();

        Map<String, String> config =  new HashMap<String, String>();
        Map<String, String> virtualHosts = new HashMap<String, String>();
        
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