import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;

public class TCPFileServer {
    private static final int PORT = 8080;
    private static final String FILE_DIR = "server_files"; // directory to check files against

    // cookieId -> list of files requested by that cookie (thread-safe map + thread-safe lists)
    private static final ConcurrentHashMap<String, List<String>> cookieHistory = new ConcurrentHashMap<>();

    public static void main(String[] args) throws IOException {
        new File(FILE_DIR).mkdirs(); // ensure directory exists

        ServerSocket serverSocket = new ServerSocket(PORT);
        System.out.println("File Server started on port " + PORT + "...");

        while (true) {
            Socket clientSocket = serverSocket.accept();
            System.out.println("New client connected: " + clientSocket.getInetAddress());
            new Thread(new ClientHandler(clientSocket)).start();
        }
    }

    static class ClientHandler implements Runnable {
        private final Socket socket;

        ClientHandler(Socket socket) {
            this.socket = socket;
        }

        @Override
        public void run() {
            try (
                BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                PrintWriter out = new PrintWriter(socket.getOutputStream(), true)
            ) {
                String line;
                String requestedFile = null;
                String cookieId = null;

                while ((line = in.readLine()) != null) {
                    if (line.startsWith("GET")) {
                        // Example: GET /file.txt HTTP/1.1
                        String[] parts = line.split(" ");
                        requestedFile = parts[1].replaceFirst("^/", "");
                    } else if (line.startsWith("Cookie:")) {
                        cookieId = line.substring("Cookie:".length()).trim();
                        if (cookieId.startsWith("id=")) {
                            cookieId = cookieId.substring(3);
                        }
                    } else if (line.isEmpty()) {
                        // End of request -> process it
                        handleRequest(requestedFile, cookieId, out);
                        requestedFile = null;
                        cookieId = null;
                    }
                }
            } catch (IOException e) {
                System.out.println("Client disconnected: " + socket.getInetAddress());
            } finally {
                try { socket.close(); } catch (IOException ignored) {}
            }
        }

        private void handleRequest(String requestedFile, String cookieId, PrintWriter out) {
            // Step 1: resolve cookie
            boolean newCookie = false;
            if (cookieId == null || !cookieHistory.containsKey(cookieId)) {
                cookieId = UUID.randomUUID().toString();
                cookieHistory.put(cookieId, Collections.synchronizedList(new ArrayList<>()));
                newCookie = true;
            }

            List<String> history = cookieHistory.get(cookieId);

            // Step 2: check file existence
            File file = new File(FILE_DIR, requestedFile);
            boolean exists = file.exists() && file.isFile();

            // Step 3: update history (log every requested file, found or not)
            synchronized (history) {
                history.add(requestedFile);
            }

            // Step 4: build HTTP-style response
            String statusLine = exists ? "HTTP/1.1 200 OK" : "HTTP/1.1 404 Not Found";

            out.println(statusLine);
            out.println("Set-Cookie: id=" + cookieId);
            out.println("Content-Type: text/plain");
            out.println();
            out.println("File-Status: " + (exists ? "FOUND" : "NOT FOUND"));
            out.println("Cookie-ID: " + cookieId + (newCookie ? " (new)" : ""));

            synchronized (history) {
                out.println("History: " + history);
            }
            out.println(); // blank line terminates the response
        }
    }
}