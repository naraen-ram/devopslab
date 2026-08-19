import java.io.*;
import java.net.*;
import java.util.*;

public class BareServer {
    // Unsafe standard HashMap (stripped of thread-safety for simplicity)
    static Map<String, List<String>> histories = new HashMap<>();

    public static void main(String[] args) throws Exception {
        ServerSocket server = new ServerSocket(8080);
        System.out.println("Bare server running on 8080...");

        while (true) {
            Socket socket = server.accept();
            // Multi-threading reduced to a 1-line lambda
            new Thread(() -> handle(socket)).start();
        }
    }

    static void handle(Socket socket) {
        try (BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
             PrintWriter out = new PrintWriter(socket.getOutputStream(), true)) {

            // Read simple input: "filename" OR "filename cookieId"
            String[] input = in.readLine().split(" ");
            String filename = input[0];
           
            // Check for cookie; if absent, assign a simple sequential ID (ID_1, ID_2, etc.)
            String cookie = (input.length > 1) ? input[1] : "ID_" + (histories.size() + 1);

            // Update history
            histories.putIfAbsent(cookie, new ArrayList<>());
            histories.get(cookie).add(filename);

            // Check file
            File file = new File(filename);
            String status = file.exists() ? "Exists (" + file.length() + " bytes)" : "Not Found";

            // Send bare-minimum HTTP/1.1 response (header followed by blank line, then body)
            out.print("HTTP/1.1 200 OK\r\n\r\n");
            out.println("Cookie: " + cookie);
            out.println("File Status: " + status);
            out.println("History: " + histories.get(cookie));

        } catch (Exception ignored) {}
    }
}

