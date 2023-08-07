package com.chibbol.wtz.global.stomp.service;

import com.chibbol.wtz.global.stomp.dto.dataDTO;

import com.chibbol.wtz.domain.job.entity.RoomUserJob;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.messaging.simp.SimpMessageSendingOperations;
import org.springframework.stereotype.Service;

@Slf4j
@RequiredArgsConstructor
@Service
public class RedisSubscriberAll implements MessageListener {
    private final ObjectMapper objectMapper;
    private final RedisTemplate redisTemplate;
    private final SimpMessageSendingOperations messagingTemplate;

    @Override
    public void onMessage(Message message, byte[] pattern) {
        try {
            String publishMessage = (String) redisTemplate.getStringSerializer().deserialize(message.getBody());
            dataDTO data = objectMapper.readValue(publishMessage, dataDTO.class);
            log.info("message: "+publishMessage);
            messagingTemplate.convertAndSend("/sub/"+data.getRoomSeq()+"/all", data);
        } catch (Exception e) {
            log.error(e.getMessage());
        }
    }
}