import java.io.*;
import java.net.*;
import java.util.*;

public class TCPChatProxy {
    // Cache of request -> response pairs
    private static final ArrayList<CacheEntry> cache = new ArrayList<>();

    private static final int LISTEN_PORT = 6000;   // Clients connect here
    private static final String SERVER_HOST = "localhost";
    private static final int SERVER_PORT = 5000;   // Real chat server

    public static void main(String[] args) throws IOException {
        ServerSocket proxySocket = new ServerSocket(LISTEN_PORT);
        System.out.println("Chat Proxy started on port " + LISTEN_PORT +
                ", forwarding to " + SERVER_HOST + ":" + SERVER_PORT);

        while (true) {
            Socket clientSocket = proxySocket.accept();
            System.out.println("New client connected to proxy: " + clientSocket.getInetAddress());
            new Thread(new ClientHandler(clientSocket)).start();
        }
    }

    // Look for a cached response for this request
    static String checkCache(String request) {
        synchronized (cache) {
            for (CacheEntry entry : cache) {
                if (entry.request.equals(request)) {
                    return entry.response;
                }
            }
        }
        return null;
    }

    // Add a new request/response pair to the cache
    static void addToCache(String request, String response) {
        synchronized (cache) {
            cache.add(new CacheEntry(request, response));
        }
    }

    // Simple holder for one cached request/response pair
    static class CacheEntry {
        String request;
        String response;

        CacheEntry(String request, String response) {
            this.request = request;
            this.response = response;
        }
    }

    static class ClientHandler implements Runnable {
        private final Socket clientSocket;

        ClientHandler(Socket clientSocket) {
            this.clientSocket = clientSocket;
        }

        @Override
        public void run() {
            try {
                BufferedReader clientIn = new BufferedReader(
                        new InputStreamReader(clientSocket.getInputStream()));
                PrintWriter clientOut = new PrintWriter(clientSocket.getOutputStream(), true);

                String request;
                while ((request = clientIn.readLine()) != null) {
                    String cachedResponse = checkCache(request);

                    if (cachedResponse != null) {
                        System.out.println("Cache HIT: " + request);
                        clientOut.println(cachedResponse);
                    } else {
                        System.out.println("Cache MISS: " + request);
                        String response = forwardToServer(request);
                        if (response != null) {
                            addToCache(request, response);
                            clientOut.println(response);
                        }
                    }
                }
            } catch (IOException e) {
                System.out.println("Client disconnected from proxy.");
            } finally {
                try { clientSocket.close(); } catch (IOException ignored) {}
            }
        }

        // Opens a fresh connection to the real server for a cache miss
        private String forwardToServer(String request) {
            try (Socket serverSocket = new Socket(SERVER_HOST, SERVER_PORT)) {
                BufferedReader serverIn = new BufferedReader(
                        new InputStreamReader(serverSocket.getInputStream()));
                PrintWriter serverOut = new PrintWriter(serverSocket.getOutputStream(), true);

                serverOut.println(request);
                return serverIn.readLine();
            } catch (IOException e) {
                System.out.println("Could not reach server for request: " + request);
                return null;
            }
        }
    }
}