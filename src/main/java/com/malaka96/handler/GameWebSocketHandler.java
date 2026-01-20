package com.malaka96.handler;

import com.malaka96.model.Message;
import com.malaka96.model.Room;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class GameWebSocketHandler extends TextWebSocketHandler {

    private final tools.jackson.databind.ObjectMapper mapper = new ObjectMapper();

    // roomId → Room
    private final Map<String, Room> rooms = new ConcurrentHashMap<>();

    // sessionId → playerId
    private final Map<String, String> sessionToPlayer = new ConcurrentHashMap<>();

    // playerId → session (so we can find session when needed)
    private final Map<String, WebSocketSession> playerToSession = new ConcurrentHashMap<>();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        System.out.println("New connection: " + session.getId());
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage textMessage) throws Exception {
        try {
            Message msg = mapper.readValue(textMessage.getPayload(), Message.class);

            if (msg.type == null) {
                sendError(session, "Missing type field");
                return;
            }

            switch (msg.type.toUpperCase()) {
                case "CREATE_ROOM":
                    handleCreateRoom(session, msg);
                    break;

                case "JOIN_ROOM":
                    handleJoinRoom(session, msg);
                    break;

                default:
                    sendError(session, "Unknown message type: " + msg.type);
            }
        } catch (Exception e) {
            sendError(session, "Invalid message format");
            e.printStackTrace();
        }
    }

    private void handleCreateRoom(WebSocketSession session, Message msg) throws IOException {
        String playerId = msg.playerId;
        if (playerId == null || playerId.trim().isEmpty()) {
            sendError(session, "playerId is required");
            return;
        }

        String roomId = UUID.randomUUID().toString().substring(0, 8);
        while (rooms.containsKey(roomId)) {
            roomId = UUID.randomUUID().toString().substring(0, 8);
        }

        Room room = new Room(roomId);
        room.players.add(playerId);
        rooms.put(roomId, room);

        // Store mappings
        sessionToPlayer.put(session.getId(), playerId);
        playerToSession.put(playerId, session);

        // Send response to creator
        Message response = new Message();
        response.type = "ROOM_CREATED";
        response.roomId = roomId;
        response.playerId = playerId;
        response.message = "Room created successfully";

        session.sendMessage(new TextMessage(mapper.writeValueAsString(response)));

        System.out.println("Room created: " + roomId + " by player " + playerId);
    }

    private void handleJoinRoom(WebSocketSession session, Message msg) throws IOException {
        String roomId = msg.roomId;
        String playerId = msg.playerId;
        String playerName = (msg.playerName != null && !msg.playerName.trim().isEmpty())
                ? msg.playerName : "Guest-" + playerId.substring(0, 4);

        if (roomId == null || playerId == null || playerId.trim().isEmpty()) {
            sendError(session, "roomId and playerId are required");
            return;
        }

        Room room = rooms.get(roomId);
        if (room == null) {
            sendError(session, "Room not found");
            return;
        }

        if (room.players.contains(playerId)) {
            sendError(session, "You are already in this room");
            return;
        }

        // Add player
        room.players.add(playerId);
        sessionToPlayer.put(session.getId(), playerId);
        playerToSession.put(playerId, session);

        // Send success to the joining player
        Message joinSuccess = new Message();
        joinSuccess.type = "JOIN_SUCCESS";
        joinSuccess.roomId = roomId;
        joinSuccess.playerId = playerId;
        joinSuccess.message = "Joined room successfully";
        session.sendMessage(new TextMessage(mapper.writeValueAsString(joinSuccess)));

        // Broadcast to everyone in the room
        Message broadcast = new Message();
        broadcast.type = "PLAYER_JOINED";
        broadcast.roomId = roomId;
        broadcast.playerId = playerId;
        broadcast.playerName = playerName;
        broadcast.message = playerName + " joined the room";

        broadcastToRoom(roomId, broadcast, session);

        System.out.println(playerId + " (" + playerName + ") joined room " + roomId);
    }

    private void broadcastToRoom(String roomId, Message message, WebSocketSession exclude) throws IOException {
        Room room = rooms.get(roomId);
        if (room == null) return;

        String json = mapper.writeValueAsString(message);

        for (String playerId : room.players) {
            WebSocketSession targetSession = playerToSession.get(playerId);
            if (targetSession != null && targetSession.isOpen()
                    && (exclude == null || !targetSession.getId().equals(exclude.getId()))) {
                targetSession.sendMessage(new TextMessage(json));
            }
        }
    }

    private void sendError(WebSocketSession session, String errorMsg) throws IOException {
        Message err = new Message();
        err.type = "ERROR";
        err.message = errorMsg;
        session.sendMessage(new TextMessage(mapper.writeValueAsString(err)));
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        String playerId = sessionToPlayer.remove(session.getId());
        if (playerId != null) {
            playerToSession.remove(playerId);

            // Remove from all rooms (simple version)
            for (Room room : rooms.values()) {
                if (room.players.remove(playerId)) {
                    // Optional: broadcast leave message
                    Message leaveMsg = new Message();
                    leaveMsg.type = "PLAYER_LEFT";
                    leaveMsg.roomId = room.roomId;
                    leaveMsg.playerId = playerId;
                    leaveMsg.message = "Player left the room";

                    broadcastToRoom(room.roomId, leaveMsg, null);
                }
            }

            System.out.println("Player disconnected: " + playerId);
        }
    }
}