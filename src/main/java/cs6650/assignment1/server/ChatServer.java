package cs6650.assignment1.server;

import cs6650.assignment1.model.ChatMessage;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;

import cs6650.assignment1.model.ServerResponse;
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

    private final Gson gson = new Gson();

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
        try {
            ChatMessage msg = gson.fromJson(message, ChatMessage.class);

            // Validate
            if (!isValid(msg)) {
                conn.send("{\"status\":\"ERROR\",\"error\":\"Invalid message format\"}");
                return;
            }

            ServerResponse res = new ServerResponse("OK", msg);
            String response = gson.toJson(res);

            // Broadcast to all in room
            String roomId = clientRooms.get(conn);
            if (roomId != null) {
                for (WebSocket client : rooms.get(roomId)) {
                    client.send(response);
                }
            }
        } catch (JsonSyntaxException e) {
            conn.send("{\"status\":\"ERROR\",\"error\":\"Malformed JSON\"}");
        }
    }

    private boolean isValid(ChatMessage msg) {
        if (msg == null) return false;

        // userId must be numeric string 1–100000
        try {
            int uid = Integer.parseInt(msg.userId);
            if (uid < 1 || uid > 100000) return false;
        } catch (NumberFormatException e) {
            return false;
        }

        // username 3–20 chars alphanumeric
        if (msg.username == null || !msg.username.matches("^[a-zA-Z0-9]{3,20}$")) {
            return false;
        }

        // message 1–500 chars
        if (msg.message == null || msg.message.length() < 1 || msg.message.length() > 500) {
            return false;
        }

        // timestamp must be ISO-8601
        try {
            Instant.parse(msg.timestamp);
        } catch (Exception e) {
            return false;
        }

        // messageType must be valid
        if (!(msg.messageType.equals("TEXT") || msg.messageType.equals("JOIN") || msg.messageType.equals("LEAVE"))) {
            return false;
        }

        return true;
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
        int wsPort = 9090;
        int httpPort = 8081;

        ChatServer wsServer = new ChatServer(wsPort);
        wsServer.start();
        System.out.println("WebSocket listening on ws://localhost:" + wsPort + "/chat/{roomId}");

        HealthServer.start(httpPort);
    }
}
