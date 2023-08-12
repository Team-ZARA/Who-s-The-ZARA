package com.chibbol.wtz.domain.room.controller;

import com.chibbol.wtz.domain.room.dto.ChatMessageDTO;
import com.chibbol.wtz.domain.room.dto.CurrentSeatsDTO;
import com.chibbol.wtz.domain.room.dto.JobSettingDTO;
import com.chibbol.wtz.domain.room.dto.RoomSettingDTO;
import com.chibbol.wtz.domain.room.entity.Room;
import com.chibbol.wtz.domain.room.service.RedisTopicService;
import com.chibbol.wtz.domain.room.service.RoomEnterInfoRedisService;
import com.chibbol.wtz.domain.room.service.RoomJobSettingRedisService;
import com.chibbol.wtz.domain.room.service.RoomService;
import com.chibbol.wtz.domain.user.entity.User;
import com.chibbol.wtz.domain.user.repository.UserRepository;
import com.chibbol.wtz.global.security.service.TokenService;
import com.chibbol.wtz.global.stomp.dto.DataDTO;
import com.chibbol.wtz.global.stomp.service.RedisPublisher;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.stereotype.Controller;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Controller
@RequiredArgsConstructor
public class StompRoomController {

    private final RedisPublisher redisPublisher;
    private final RedisTopicService redisTopicService;
    private final TokenService tokenService;
    private final RoomService roomService;
    private final RoomEnterInfoRedisService roomEnterInfoRedisService;
    private final RoomJobSettingRedisService roomJobSettingRedisService;
    private final UserRepository userRepository;


    // ws 프로토콜로 사용한 "/pub/chat/enter"과 매칭
    @Operation(summary = "[ENTER] 방 입장")
    @MessageMapping(value = "/room/{roomCode}/enter")
    public void enter(@DestinationVariable String roomCode, @Header("Authorization") String token) {
        log.info("ENTER 시작");
        // room, user 추출
        String processedToken = token.replace("Bearer ", "");
        User user = tokenService.getUserFromToken(processedToken);
        Room room = roomService.findRoomByCode(roomCode);
        log.info("user : " + user.toString());
        // ENTER 메세지 보내기
        DataDTO dataDTO = DataDTO.builder()
                .type("ROOM_ENTER_MESSAGE")
                .code(roomCode)
                .data(user.getNickname() +"님이 채팅방에 입장하셨습니다.")
                .build();
        redisPublisher.stompPublish(redisTopicService.getTopic(roomCode), dataDTO);
        log.info("ROOM_ENTER_MESSAGE 완료");
        // 유저 정보, 착용 item 저장
        roomEnterInfoRedisService.enterUser(roomCode, user);
        log.info("item 저장 완료");
        // CurrentSeatDTO 추출
        List<CurrentSeatsDTO> currentSeatsDTOs = roomEnterInfoRedisService.getUserEnterInfo(roomCode);
        for (CurrentSeatsDTO c : currentSeatsDTOs) {
            log.info("요기"+c.toString());
        }
        log.info("현재 유저 수 : " + roomEnterInfoRedisService.getUsingSeats(roomCode));
        // jobSetting 추출
        List<Long> excludeJobSetting = roomJobSettingRedisService.findExcludeJobSeqByGameCode(roomCode); // todo : gameCode, roomCode 둘 다로 jobSetting key 생성
        Map<Long, Boolean> jobSetting = new HashMap<>();
        for (long i = 1; i <= 7; i++) {
            jobSetting.put(i, true);
        }
        for (Long ex : excludeJobSetting) {
            jobSetting.put(ex, false);
        }
        log.info("jobSetting 완료");
        // INITIAL_ROOM_SETTING 보내기
        RoomSettingDTO roomSettingDTO = RoomSettingDTO
                .builder()
                .title(room.getTitle())
                .ownerSeq(room.getOwner().getUserSeq())
                .jobSetting(jobSetting)
                .curSeats(currentSeatsDTOs)
                .build();
        dataDTO.setType("ROOM_ENTER_ROOM_SETTING");
        dataDTO.setData(roomSettingDTO);
        redisPublisher.stompPublish(redisTopicService.getTopic(roomCode), dataDTO);
        log.info("ENTER 끝");
    }

    @Operation(summary = "[CHAT] 채팅 메세지")
    @MessageMapping(value = "/room/{roomCode}/chat")
    public void chat(@DestinationVariable String roomCode, ChatMessageDTO chatMessageDTO) {
        log.info("CHAT 시작");
        chatMessageDTO.setNickname(userRepository.findNicknameByUserSeq(chatMessageDTO.getSenderSeq()));
        DataDTO dataDTO = DataDTO.builder()
                .type("ROOM_CHAT")
                .code(roomCode)
                .data(chatMessageDTO)
                .build();
        redisPublisher.stompPublish(redisTopicService.getTopic(roomCode), dataDTO);
        log.info("CHAT 끝");
    }

    @Operation(summary = "[EXIT] 방 퇴장")
    @MessageMapping(value = "/room/{roomCode}/exit")
    public void exit(@DestinationVariable String roomCode, @Header("Authorization") String token) {
        log.info("EXIT 시작");
        String processedToken = token.replace("Bearer ", "");
        User user = tokenService.getUserFromToken(processedToken);
        log.info("seq" + user.getUserSeq());
        // 메세지 보내기
        DataDTO dataDTO = DataDTO.builder()
                .type("ROOM_EXIT")
                .code(roomCode)
                .data(user.getNickname() +"님이 채팅방에 퇴장하셨습니다.")
                .build();
        redisPublisher.stompPublish(redisTopicService.getTopic(roomCode), dataDTO);
        // 유저 관리
        roomEnterInfoRedisService.setUserExitInfo(roomCode, user.getUserSeq());
        // 남은 사람 없을 경우
        boolean emptyRoom = false;
        if (roomEnterInfoRedisService.getUsingSeats(roomCode) == 0) {
            emptyRoom = true;
            roomService.deleteRoom(roomCode);
        }
        dataDTO.setType("ROOM_CUR_SEATS");
        dataDTO.setData(roomEnterInfoRedisService.getUserEnterInfo(roomCode));
        redisPublisher.stompPublish(redisTopicService.getTopic(roomCode), dataDTO);
        // 남은 사람이 존재하면서 & 방장이 나갔을 경우
        Room room = roomService.findRoomByCode(roomCode);
        if (!emptyRoom && user.getUserSeq() == room.getOwner().getUserSeq()) {
            long newOwnerSeq = roomService.changeRoomOwner(roomCode);
            dataDTO.setType("ROOM_CHANGE_OWNER");
            dataDTO.setData(newOwnerSeq);
            redisPublisher.stompPublish(redisTopicService.getTopic(roomCode), dataDTO);
        }
        log.info("EXIT 끝");
    }

    @Operation(summary = "[SET] 방 제목 세팅")
    @MessageMapping(value = "/room/{roomCode}/title")
    public void setTitle(@DestinationVariable String roomCode, RoomSettingDTO roomSettingDTO) {
        log.info("TITLE 시작");
        log.info("전 : " + roomService.findRoomByCode(roomCode).getTitle());
        log.info("후 : " + roomSettingDTO.getTitle());
        roomService.updateTitle(roomCode, roomSettingDTO.getTitle());
        DataDTO dataDTO = DataDTO.builder()
                .type("ROOM_TITLE")
                .code(roomCode)
                .data(roomSettingDTO.getTitle())
                .build();
        redisPublisher.stompPublish(redisTopicService.getTopic(roomCode), dataDTO);
        log.info("TITLE 끝");
    }

    @Operation(summary = "[JOB SETTING] 직업 세팅")
    @MessageMapping(value = "/room/{roomCode}/jobSetting")
    public void setTitle(@DestinationVariable String roomCode, JobSettingDTO jobSettingDTO) {
        log.info("JOB SETTING 시작");
        log.info(jobSettingDTO.toString());
        log.info(jobSettingDTO.getJobSetting().size()+"");
        roomJobSettingRedisService.setAllJobSetting(roomCode, jobSettingDTO);
        DataDTO dataDTO = DataDTO.builder()
                .type("ROOM_JOB_SETTING")
                .code(roomCode)
                .data(jobSettingDTO)
                .build();

        roomJobSettingRedisService.findRoomJobSettingByGameCode(roomCode);
        redisPublisher.stompPublish(redisTopicService.getTopic(roomCode), dataDTO);
        log.info("JOB SETTING 끝");
    }

    @Operation(summary = "[START] 게임 시작")
    @MessageMapping(value = "/room/{roomCode}/start")
    public void startGame(@DestinationVariable String roomCode) {
        log.info("START 시작");
        String gameCode = roomService.generateGameCode(roomCode);
        DataDTO dataDTO = DataDTO.builder()
                .type("ROOM_START")
                .code(roomCode)
                .data(gameCode)
                .build();
        redisPublisher.stompPublish(redisTopicService.getTopic(roomCode), dataDTO);
        log.info("START 끝");
    }
}
