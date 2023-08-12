package com.chibbol.wtz.global.stomp.dto;

import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;

@Getter
@ToString
@NoArgsConstructor
public class DataDTO {
    private String type;
    private String code;
    private Object data;

    @Builder
    public DataDTO(String type, String gameCode, Object data){
        this.type = type;
        this.code = gameCode;
        this.data = data;
    }

}
