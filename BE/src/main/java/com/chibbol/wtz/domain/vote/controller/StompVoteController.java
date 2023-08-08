package com.chibbol.wtz.domain.vote.controller;

import com.chibbol.wtz.domain.job.dto.TargetUserDTO;
import com.chibbol.wtz.domain.vote.dto.VoteDTO;
import com.chibbol.wtz.domain.vote.service.VoteService;
import com.chibbol.wtz.global.stomp.dto.DataDTO;
import com.chibbol.wtz.global.stomp.service.RedisPublisherAll;
import com.chibbol.wtz.global.stomp.service.StompService;
import com.chibbol.wtz.global.timer.service.NewTimerService;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.stereotype.Controller;

@Slf4j
@Controller
@RequiredArgsConstructor
public class StompVoteController {

    private final VoteService voteService;
    private final StompService stompService;
    private final NewTimerService newTimerService;
    private final RedisPublisherAll publisher;

    // /pub/{roomSeq}/vote --> 각 roomSeq에서 turn마다 투표 정보 받아서 표수 카운트해서 저장, client에 투표 정보 전달
    @Operation(summary = "투표")
    @MessageMapping("/{gameCode}/vote")
    public void vote(@DestinationVariable String roomCode, TargetUserDTO targetUserDTO){
        // 투표 정보 저장
        VoteDTO voteData = VoteDTO.builder()
                .roomCode(roomCode)
                .userSeq(targetUserDTO.getUserSeq())
                .targetUserSeq(targetUserDTO.getTargetUserSeq())
                .turn(newTimerService.getTimerInfo(roomCode).getTurn())
                .build();

        voteService.vote(voteData);

        // 투표 현황 리스트로 만들어서 전달
        stompService.addTopic(roomCode);
        publisher.publish(stompService.getTopic(roomCode),
                DataDTO.builder()
                        .type("VOTE")
                        .roomCode(roomCode)
                        .data(voteService.getRealTimeVoteResult(roomCode, newTimerService.getTimerInfo(roomCode).getTurn()))
                        .build());
    }


}
