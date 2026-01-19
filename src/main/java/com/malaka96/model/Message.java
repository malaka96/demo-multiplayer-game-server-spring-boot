package com.malaka96.model;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@ToString
public class Message {
    public String type;     // CREATE_ROOM, JOIN_ROOM, RESPONSE
    public String roomId;
    public String playerId;
    public String content;
}