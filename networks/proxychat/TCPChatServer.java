import java.io.*;
import java.net.*;
import java.util.*;

public class TCPChatServer {
    private static final List<PrintWriter> clients = Collections.synchronizedList(new ArrayList<>());

    public static void main(String[] args) throws IOException {
        ServerSocket serverSocket = new ServerSocket(5000);
        System.out.println("Chat Server started on port 5000...");

        while (true) {
            Socket clientSocket = serverSocket.accept();
            System.out.println("New client connected: " + clientSocket.getInetAddress());
            new Thread(new ClientHandler(clientSocket)).start();
        }
    }

    static void broadcast(String message, PrintWriter sender) {
        synchronized (clients) {
            for (PrintWriter writer : clients) {
                if (writer != sender) {
                    writer.println(message);
                }
            }
        }
    }

    static void addClient(PrintWriter writer) { clients.add(writer); }
    static void removeClient(PrintWriter writer) { clients.remove(writer); }

    static class ClientHandler implements Runnable {
        private final Socket socket;

        ClientHandler(Socket socket) { this.socket = socket; }

        @Override
        public void run() {
            PrintWriter out = null;
            try {
                BufferedReader in = new BufferedReader(
                        new InputStreamReader(socket.getInputStream()));
                out = new PrintWriter(socket.getOutputStream(), true);
                addClient(out);

                out.println("Enter your name:");
                String name = in.readLine();
                broadcast("[" + name + " joined the chat]", out);
                System.out.println(name + " joined.");

                String message;
                while ((message = in.readLine()) != null) {
                    System.out.println(name + ": " + message);
                    broadcast(name + ": " + message, out);
                }
            } catch (IOException e) {
                System.out.println("Client disconnected");
            } finally {
                if (out != null) removeClient(out);
                try { socket.close(); } catch (IOException ignored) {}
            }
        }
    }
}