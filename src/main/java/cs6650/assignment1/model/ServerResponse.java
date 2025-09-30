package cs6650.assignment1.model;

import java.time.Instant;

public class ServerResponse {
    public String status;
    public String serverTimestamp;
    public ChatMessage data;

    public ServerResponse(String status, ChatMessage data) {
        this.status = status;
        this.serverTimestamp = Instant.now().toString();
        this.data = data;
    }
}
