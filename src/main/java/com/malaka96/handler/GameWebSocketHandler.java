package com.malaka96.handler;


import com.malaka96.model.Message;
import com.malaka96.model.Room;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.*;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import tools.jackson.databind.ObjectMapper;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class GameWebSocketHandler extends TextWebSocketHandler {

    private final tools.jackson.databind.ObjectMapper mapper = new ObjectMapper();

    // roomId -> Room
    private final Map<String, Room> rooms = new ConcurrentHashMap<>();

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage textMessage) throws Exception {

        Message msg = mapper.readValue(textMessage.getPayload(), Message.class);

        switch (msg.type) {
            case "CREATE_ROOM":
                handleCreateRoom(session, msg);
                break;

            case "JOIN_ROOM":
                handleJoinRoom(session, msg);
                break;
        }
    }

    private void handleCreateRoom(WebSocketSession session, Message msg) throws Exception {
        if (rooms.containsKey(msg.roomId)) {
            send(session, "ERROR", msg.roomId, "Room already exists");
            return;
        }

        Room room = new Room(msg.roomId);
        room.players.add(msg.playerId);
        rooms.put(msg.roomId, room);

        send(session, "SUCCESS", msg.roomId, "Room created");
    }

    private void handleJoinRoom(WebSocketSession session, Message msg) throws Exception {
        Room room = rooms.get(msg.roomId);

        if (room == null) {
            send(session, "ERROR", msg.roomId, "Room not found");
            return;
        }

        room.players.add(msg.playerId);
        send(session, "SUCCESS", msg.roomId, "Joined room");
    }

    private void send(WebSocketSession session, String type, String roomId, String content) throws Exception {
        Message response = new Message();
        response.type = type;
        response.roomId = roomId;
        response.content = content;

        session.sendMessage(new TextMessage(mapper.writeValueAsString(response)));
    }
}
