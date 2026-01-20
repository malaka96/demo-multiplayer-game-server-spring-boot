package com.malaka96.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.*;

@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class Message {
    private String type;
    private String roomId;
    private String playerId;
    private String playerName;
    private String message;
}