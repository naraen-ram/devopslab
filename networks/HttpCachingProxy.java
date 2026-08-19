import java.io.*;
import java.net.*;
import java.security.*;

// ─────────────────────────────────────────────────────────────────────────────
//  HttpCachingProxy.java
//  A single-file HTTP caching proxy server using TCP sockets.
//
//  HOW TO RUN:
//    javac HttpCachingProxy.java
//    java  HttpCachingProxy
//
//  TEST (in a second terminal):
//    java  HttpCachingProxy client http://example.com/
//    (run twice to see Cache Miss → Cache Hit)
//
//  OR configure your browser's HTTP proxy to: localhost:8080
// ─────────────────────────────────────────────────────────────────────────────

public class HttpCachingProxy {

    public static void main(String[] args) throws IOException {
        // If called with "client <url>", run as test client instead
        if (args.length >= 2 && args[0].equals("client")) {
            ProxyTestClient.run(args[1]);
            return;
        }
        ProxyServer.start();
    }
}


// =============================================================================
//  1. ProxyServer
//     Binds a ServerSocket to port 8080 and handles client requests
//     sequentially in an infinite loop.
// =============================================================================
class ProxyServer {

    static final int PORT = 8080;

    static void start() throws IOException {
        // Create the cache directory once at startup
        File cacheDir = new File("cache");
        if (!cacheDir.exists()) {
            cacheDir.mkdir();
            System.out.println("[INIT] Cache directory created: ./cache/");
        } else {
            System.out.println("[INIT] Using existing cache directory: ./cache/");
        }

        ServerSocket serverSocket = new ServerSocket(PORT);
        System.out.println("╔══════════════════════════════════════════╗");
        System.out.println("║      HTTP Caching Proxy Server           ║");
        System.out.println("║      Listening on port " + PORT + "              ║");
        System.out.println("╚══════════════════════════════════════════╝");
        System.out.println("[INIT] Proxy ready. Set browser proxy to localhost:" + PORT);
        System.out.println("─".repeat(60));

        // Sequential request loop — one client at a time
        while (true) {
            Socket clientSocket = serverSocket.accept();
            System.out.println("\n[CONNECTION] Client: "
                    + clientSocket.getInetAddress().getHostAddress());
            new RequestHandler(clientSocket).handle();
        }
    }
}


// =============================================================================
//  2. RequestHandler
//     Reads the HTTP request, checks cache, serves or fetches accordingly,
//     and always closes the client socket when done.
// =============================================================================
class RequestHandler {

    private final Socket clientSocket;

    RequestHandler(Socket clientSocket) {
        this.clientSocket = clientSocket;
    }

    void handle() {
        // try-with-resources guarantees the client socket is always closed
        try (Socket cs = clientSocket) {
            InputStream  clientIn  = cs.getInputStream();
            OutputStream clientOut = cs.getOutputStream();

            // Step 1 – Parse the incoming HTTP request
            HttpRequest request = HttpRequest.parse(clientIn);
            if (request == null) {
                System.out.println("[HANDLER] Empty request. Skipping.");
                return;
            }
            if (!request.getMethod().equalsIgnoreCase("GET")) {
                sendError(clientOut, 405, "Method Not Allowed",
                          "This proxy supports GET only.");
                return;
            }

            String url = request.getUrl();
            System.out.println("[HANDLER] Processing: " + url);

            byte[] responseData;

            // Step 2 – Cache check: Hit or Miss?
            if (CacheManager.isCached(url)) {

                // ── CACHE HIT ──────────────────────────────────────────────
                System.out.println();
                System.out.println("┌─────────────────────────────────────────────┐");
                System.out.println("│  ✔  Cache Hit – Serving file from local cache│");
                System.out.println("└─────────────────────────────────────────────┘");
                System.out.println("[HIT]  URL  : " + url);
                System.out.println("[HIT]  File : " + CacheManager.getCacheFileName(url));

                responseData = CacheManager.readFromCache(url);

            } else {

                // ── CACHE MISS ─────────────────────────────────────────────
                System.out.println();
                System.out.println("┌──────────────────────────────────────────────────────┐");
                System.out.println("│  ✘  Cache Miss – Downloading file from web server     │");
                System.out.println("└──────────────────────────────────────────────────────┘");
                System.out.println("[MISS] URL  : " + url);
                System.out.println("[MISS] Host : " + request.getHost() + ":" + request.getPort());

                // Fetch from the real web server over TCP
                responseData = RemoteFetcher.fetch(
                        request.getHost(),
                        request.getPort(),
                        request.buildForwardRequest());

                // Save to disk for future requests
                CacheManager.saveToCache(url, responseData);
            }

            // Step 3 – Send the response back to the client
            clientOut.write(responseData);
            clientOut.flush();
            System.out.println("[HANDLER] Sent " + responseData.length + " bytes to client.");

        } catch (IOException e) {
            System.out.println("[ERROR] " + e.getMessage());
        }
        // clientSocket closed here by try-with-resources
        System.out.println("[HANDLER] Client socket closed.");
    }

    private void sendError(OutputStream out, int code, String status, String body)
            throws IOException {
        String response =
                "HTTP/1.0 " + code + " " + status + "\r\n" +
                "Content-Type: text/plain\r\n" +
                "Connection: close\r\n\r\n" + body;
        out.write(response.getBytes());
        out.flush();
    }
}


// =============================================================================
//  3. HttpRequest
//     Parses the raw HTTP GET request line + headers from the client socket.
//     Rebuilds a clean forwarding request to send upstream.
// =============================================================================
class HttpRequest {

    private String   method;
    private String   url;
    private String   host;
    private int      port;
    private String   path;
    private String[] rawLines;

    /** Read headers from the stream and return a parsed HttpRequest, or null. */
    static HttpRequest parse(InputStream in) throws IOException {
        BufferedReader reader = new BufferedReader(new InputStreamReader(in));

        String requestLine = reader.readLine();
        if (requestLine == null || requestLine.isEmpty()) return null;

        System.out.println("[REQUEST] " + requestLine);

        java.util.List<String> lines = new java.util.ArrayList<>();
        lines.add(requestLine);

        // Read headers until the blank line
        String line;
        while ((line = reader.readLine()) != null && !line.isEmpty()) {
            lines.add(line);
        }

        // Parse: METHOD URL HTTP-VERSION
        String[] parts = requestLine.split(" ");
        if (parts.length < 2) throw new IOException("Bad request line: " + requestLine);

        HttpRequest req = new HttpRequest();
        req.rawLines = lines.toArray(new String[0]);
        req.method   = parts[0];
        req.url      = parts[1];

        // Decompose URL into host / port / path
        URL parsed  = new URL(req.url);
        req.host    = parsed.getHost();
        req.port    = parsed.getPort() == -1 ? 80 : parsed.getPort();
        req.path    = parsed.getFile().isEmpty() ? "/" : parsed.getFile();

        return req;
    }

    /**
     * Rebuilds the request for forwarding:
     *   - Uses relative path (not full URL) on the first line
     *   - Downgrades to HTTP/1.0 so the server closes the connection when done
     *   - Strips hop-by-hop headers; adds Connection: close
     */
    String buildForwardRequest() {
        StringBuilder sb = new StringBuilder();
        sb.append(method).append(" ").append(path).append(" HTTP/1.0\r\n");
        for (int i = 1; i < rawLines.length; i++) {
            String lower = rawLines[i].toLowerCase();
            if (!lower.startsWith("connection:") &&
                !lower.startsWith("proxy-connection:")) {
                sb.append(rawLines[i]).append("\r\n");
            }
        }
        sb.append("Connection: close\r\n\r\n");
        return sb.toString();
    }

    String   getMethod() { return method; }
    String   getUrl()    { return url;    }
    String   getHost()   { return host;   }
    int      getPort()   { return port;   }
    String   getPath()   { return path;   }
}


// =============================================================================
//  4. CacheManager
//     Maps each URL to a file under ./cache/ using an MD5 hash as the name.
//     Provides isCached(), readFromCache(), and saveToCache().
// =============================================================================
class CacheManager {

    private static final String CACHE_DIR = "cache";

    /** Converts a URL to a safe, unique filename via MD5. */
    static String getCacheFileName(String url) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] hash = md.digest(url.getBytes());
            StringBuilder hex = new StringBuilder();
            for (byte b : hash) hex.append(String.format("%02x", b));
            return CACHE_DIR + File.separator + hex;
        } catch (NoSuchAlgorithmException e) {
            // Fallback: sanitize URL chars
            return CACHE_DIR + File.separator + url.replaceAll("[^a-zA-Z0-9]", "_");
        }
    }

    /** Returns true if a non-empty cache file exists for this URL. */
    static boolean isCached(String url) {
        File f = new File(getCacheFileName(url));
        return f.exists() && f.length() > 0;
    }

    /** Reads the cached response bytes from disk. */
    static byte[] readFromCache(String url) throws IOException {
        File f = new File(getCacheFileName(url));
        try (FileInputStream fis = new FileInputStream(f)) {
            byte[] data = fis.readAllBytes();
            System.out.println("[CACHE] Read " + data.length + " bytes ← " + f.getName());
            return data;
        }
    }

    /** Writes the raw HTTP response bytes (headers + body) to disk. */
    static void saveToCache(String url, byte[] data) throws IOException {
        File f = new File(getCacheFileName(url));
        try (FileOutputStream fos = new FileOutputStream(f)) {
            fos.write(data);
            System.out.println("[CACHE] Saved " + data.length + " bytes → " + f.getName());
        }
    }
}


// =============================================================================
//  5. RemoteFetcher
//     Opens a TCP socket to the destination web server, sends the forwarded
//     HTTP GET request, and returns the raw response as a byte array.
// =============================================================================
class RemoteFetcher {

    private static final int CONNECT_TIMEOUT = 10_000; // ms
    private static final int READ_TIMEOUT    = 15_000; // ms

    /**
     * Connects to host:port, sends the forwarded request string, reads all
     * response bytes (headers + body), and returns them.
     */
    static byte[] fetch(String host, int port, String forwardRequest) throws IOException {
        System.out.println("[FETCH] Connecting to " + host + ":" + port);

        Socket serverSocket = new Socket();
        serverSocket.connect(new InetSocketAddress(host, port), CONNECT_TIMEOUT);
        serverSocket.setSoTimeout(READ_TIMEOUT);

        try {
            // Send the HTTP GET upstream
            OutputStream out = serverSocket.getOutputStream();
            out.write(forwardRequest.getBytes());
            out.flush();
            System.out.println("[FETCH] Request forwarded.");

            // Read the complete response (Connection: close signals the end)
            InputStream          in  = serverSocket.getInputStream();
            ByteArrayOutputStream buf = new ByteArrayOutputStream();
            byte[] chunk = new byte[4096];
            int n;
            while ((n = in.read(chunk)) != -1) buf.write(chunk, 0, n);

            byte[] response = buf.toByteArray();
            System.out.println("[FETCH] Received " + response.length + " bytes.");
            return response;

        } finally {
            serverSocket.close();
            System.out.println("[FETCH] Connection to " + host + " closed.");
        }
    }
}


// =============================================================================
//  6. ProxyTestClient  (optional — simulates a browser for quick testing)
//     Usage:  java HttpCachingProxy client http://example.com/
//     Run twice with the same URL: 1st = Cache Miss, 2nd = Cache Hit.
// =============================================================================
class ProxyTestClient {

    static void run(String url) throws IOException {
        System.out.println("TestClient → Proxy at localhost:" + ProxyServer.PORT);
        System.out.println("Requesting : " + url);
        System.out.println("─".repeat(50));

        try (Socket socket = new Socket("localhost", ProxyServer.PORT)) {
            // Send HTTP GET to the proxy
            OutputStream out = socket.getOutputStream();
            String request =
                    "GET " + url + " HTTP/1.0\r\n" +
                    "Host: " + new URL(url).getHost() + "\r\n" +
                    "Connection: close\r\n\r\n";
            out.write(request.getBytes());
            out.flush();

            // Read full response
            ByteArrayOutputStream buf = new ByteArrayOutputStream();
            InputStream in = socket.getInputStream();
            byte[] chunk = new byte[4096];
            int n;
            while ((n = in.read(chunk)) != -1) buf.write(chunk, 0, n);

            byte[] response = buf.toByteArray();
            String text     = new String(response);

            System.out.println("Status  : " + text.split("\r\n")[0]);
            System.out.println("Size    : " + response.length + " bytes");

            int headerEnd = text.indexOf("\r\n\r\n");
            if (headerEnd != -1 && headerEnd + 4 < text.length()) {
                String body = text.substring(headerEnd + 4);
                System.out.println("\n── Body preview ──");
                System.out.println(body.substring(0, Math.min(300, body.length())));
            }
        }
    }
}