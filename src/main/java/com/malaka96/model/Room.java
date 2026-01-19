package com.malaka96.model;

import java.util.HashSet;
import java.util.Set;


import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@ToString
public class Room {
    public String roomId;
    public Set<String> players = new HashSet<>();

    public Room(String roomId) {
        this.roomId = roomId;
    }
}