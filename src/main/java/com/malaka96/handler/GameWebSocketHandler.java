package com.malaka96.handler;

import com.malaka96.model.Message;
import com.malaka96.model.Room;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.*;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
public class GameWebSocketHandler extends TextWebSocketHandler {

    private final tools.jackson.databind.ObjectMapper objectMapper = new ObjectMapper();
    private final Map<String, Room> rooms = new ConcurrentHashMap<>();
    private final Map<String, Set<WebSocketSession>> roomSessions = new ConcurrentHashMap<>();
    private final Map<String, String> sessionToRoom = new ConcurrentHashMap<>();

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        try {
            String payload = message.getPayload();
            Map<String, Object> map = objectMapper.readValue(payload, Map.class);
            String type = (String) map.get("type");

            if ("PLAYER_MOVE".equals(type)) {
                broadcastRawToRoom((String) map.get("roomId"), payload, session);
                return;
            }

            Message msg = objectMapper.readValue(payload, Message.class);
            switch (msg.getType()) {
                case "CREATE_ROOM" -> handleCreateRoom(session, msg);
                case "JOIN_ROOM" -> handleJoinRoom(session, msg);
                case "START_GAME" -> handleStartGame(session, msg);
            }
        } catch (Exception e) {
            log.error("Error: {}", e.getMessage());
        }
    }

    private void handleCreateRoom(WebSocketSession session, Message msg) throws IOException {
        String roomId = UUID.randomUUID().toString().substring(0, 5).toUpperCase();
        Room room = new Room(roomId, msg.getPlayerId());
        rooms.put(roomId, room);

        addUserToRoom(roomId, session);

        msg.setType("ROOM_CREATED");
        msg.setRoomId(roomId);
        session.sendMessage(new TextMessage(objectMapper.writeValueAsString(msg)));
    }

    private void handleJoinRoom(WebSocketSession session, Message msg) throws IOException {
        String roomId = msg.getRoomId();
        Room room = rooms.get(roomId);
        if (room == null) return;

        room.getPlayers().add(msg.getPlayerId());
        addUserToRoom(roomId, session);

        msg.setType("JOIN_SUCCESS");
        session.sendMessage(new TextMessage(objectMapper.writeValueAsString(msg)));

        Message joinNotify = new Message("PLAYER_JOINED", roomId, msg.getPlayerId(), msg.getPlayerName(), null);
        broadcastToRoom(roomId, joinNotify, null);
    }

    private void handleStartGame(WebSocketSession session, Message msg) throws IOException {
        broadcastToRoom(msg.getRoomId(), new Message("GAME_START", msg.getRoomId(), null, null, null), null);
    }

    private void addUserToRoom(String roomId, WebSocketSession session) {
        roomSessions.computeIfAbsent(roomId, k -> ConcurrentHashMap.newKeySet()).add(session);
        sessionToRoom.put(session.getId(), roomId);
    }

    private void broadcastRawToRoom(String roomId, String json, WebSocketSession sender) throws IOException {
        Set<WebSocketSession> sessions = roomSessions.get(roomId);
        if (sessions == null) return;
        for (WebSocketSession s : sessions) {
            if (s.isOpen() && !s.getId().equals(sender.getId())) {
                s.sendMessage(new TextMessage(json));
            }
        }
    }

    private void broadcastToRoom(String roomId, Message msg, WebSocketSession sender) throws IOException {
        broadcastRawToRoom(roomId, objectMapper.writeValueAsString(msg), sender != null ? sender : new DummySession());
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        String roomId = sessionToRoom.remove(session.getId());
        if (roomId != null && roomSessions.containsKey(roomId)) {
            roomSessions.get(roomId).remove(session);
            if (roomSessions.get(roomId).isEmpty()) {
                rooms.remove(roomId);
                roomSessions.remove(roomId);
            }
        }
    }

    private static class DummySession extends org.springframework.web.socket.adapter.standard.StandardWebSocketSession {
        public DummySession() { super(null, null, null, null); }
        @Override public String getId() { return "DUMMY"; }
    }
}