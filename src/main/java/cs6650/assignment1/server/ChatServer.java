package cs6650.assignment1.server;

import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.WebSocketServer;

import java.net.InetSocketAddress;
import java.time.Instant;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;


public class ChatServer extends WebSocketServer{

    // roomId -> set of clients
    private final ConcurrentHashMap<String, Set<WebSocket>> rooms = new ConcurrentHashMap<>();
    // track which room each client belongs to
    private final ConcurrentHashMap<WebSocket, String> clientRooms = new ConcurrentHashMap<>();


    public ChatServer(int port) {
        super(new InetSocketAddress(port));
        setConnectionLostTimeout(30); //ping/keepalive 30sec
    }

    @Override
    public void onOpen(WebSocket conn, ClientHandshake handshake) {
        String path = handshake.getResourceDescriptor(); // "/chat/1"
        String[] parts = path.split("/");
        if (parts.length >= 3 && parts[1].equals("chat")) {
            String roomId = parts[2];

            // Add client to room
            rooms.computeIfAbsent(roomId, k -> ConcurrentHashMap.newKeySet()).add(conn);
            clientRooms.put(conn, roomId);

            conn.send("{\"status\":\"CONNECTED\",\"roomId\":\"" + roomId + "\",\"serverTimestamp\":\"" + Instant.now() + "\"}");
            System.out.println("Client joined room " + roomId + ": " + conn.getRemoteSocketAddress());
        } else {
            conn.close(1008, "Invalid path. Use /chat/{roomId}");
        }
    }

    @Override
    public void onMessage(WebSocket conn, String message) {
        String roomId = clientRooms.get(conn);
        if (roomId != null) {
            // broadcast to all in the same room
            for (WebSocket client : rooms.get(roomId)) {
                client.send(message);
            }
        }
    }

    @Override
    public void onClose(WebSocket conn, int code, String reason, boolean remote) {
        String roomId = clientRooms.remove(conn);
        if (roomId != null) {
            rooms.get(roomId).remove(conn);
            System.out.println("Client left room " + roomId);
        }
    }

    @Override
    public void onError(WebSocket conn, Exception ex) {
        ex.printStackTrace();
    }

    @Override
    public void onStart() {
        System.out.println("ChatServer started");
    }

    public static void main(String[] args) throws Exception {
        int port = 9090;
        ChatServer server = new ChatServer(port);
        server.start();
        System.out.println("Listening on ws://localhost:" + port + "/chat/{roomId}");
    }
}
