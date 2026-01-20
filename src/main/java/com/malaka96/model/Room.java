package com.malaka96.model;

import lombok.*;

import java.util.HashSet;
import java.util.Set;

@Getter
@Setter
@NoArgsConstructor
public class Room {

    private String roomId;
    private String hostPlayerId;           // ← NEW: who created the room
    private Set<String> players = new HashSet<>();

    public Room(String roomId, String hostPlayerId) {
        this.roomId = roomId;
        this.hostPlayerId = hostPlayerId;
        this.players.add(hostPlayerId);    // host is also a player
    }

    public boolean isHost(String playerId) {
        return hostPlayerId != null && hostPlayerId.equals(playerId);
    }

    public boolean canStartGame() {
        return players.size() >= 2;
    }
}