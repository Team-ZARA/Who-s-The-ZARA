package com.chibbol.wtz.domain.room.repository;


import com.chibbol.wtz.domain.room.dto.CurrentSeatsDTO;
import com.chibbol.wtz.domain.room.dto.CurrentSeatsDTOList;
import com.chibbol.wtz.domain.shop.dto.EquippedItemsDTO;
import com.chibbol.wtz.domain.shop.dto.ItemDTO;
import com.chibbol.wtz.domain.shop.repository.ItemRepository;
import com.chibbol.wtz.domain.user.entity.User;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Repository
@AllArgsConstructor
public class RoomEnterInfoRedisRepository {

    private final RedisTemplate<String, String> redisTemplate;
    private final ObjectMapper objectMapper;
    private final ItemRepository itemRepository;
    private static String KEY_PREFIX = "EnterInfo:";

    // 방 생성
    public void createCurrentSeat(String roomCode, int maxUserNum) {
        String key = generateKey(roomCode);
        for (int i = 0; i < maxUserNum; i++) {
            CurrentSeatsDTO currentSeatsDTO = new CurrentSeatsDTO();
            currentSeatsDTO.setOrder(i);
            currentSeatsDTO.setState(0);
            save(roomCode, currentSeatsDTO);
        }
        for(int i = maxUserNum; i < 8; i++) {
            CurrentSeatsDTO currentSeatsDTO = new CurrentSeatsDTO();
            currentSeatsDTO.setOrder(i);
            currentSeatsDTO.setState(-1);
            save(roomCode, currentSeatsDTO);
        }
    }

    public void updateCurrentSeat(String roomCode, CurrentSeatsDTOList currentSeatsDTOList) {
        String key = generateKey(roomCode);

        Map<Object, Object> currentSeatJsonMap = redisTemplate.opsForHash().entries(key);
        Map<Integer, CurrentSeatsDTO> currentSeatMap = new HashMap<>();
        for (Map.Entry<Object, Object> entry : currentSeatJsonMap.entrySet()) {
            Integer seatKey = (Integer) entry.getKey();
            CurrentSeatsDTO currentSeatsDTO = objectMapper.convertValue(entry.getValue(), CurrentSeatsDTO.class);
            currentSeatMap.put(seatKey, currentSeatsDTO);
        }


        for (CurrentSeatsDTO curSeats : currentSeatsDTOList.getCurSeats()) {
            save(roomCode, currentSeatMap.get(curSeats.getOrder()).update(curSeats));
        }
    }

    public void deleteCurrentSeat(String roomCode) {
        String key = generateKey(roomCode);
        redisTemplate.delete(key);
    }

    public CurrentSeatsDTO enterUser(String roomCode, User user, List<ItemDTO> items) {
        String key = generateKey(roomCode);
        List<CurrentSeatsDTO> currentSeatsDTOs = getUserEnterInfo(roomCode);

        currentSeatsDTOs.sort(CurrentSeatsDTO::compareTo);

        log.info(currentSeatsDTOs.toString());
        for(CurrentSeatsDTO currentSeatsDTO : currentSeatsDTOs) {
            log.info(currentSeatsDTO.getState()+"");
            if(currentSeatsDTO.getState() == 0) {
                currentSeatsDTO.setUserSeq(user.getUserSeq());
                currentSeatsDTO.setNickname(user.getNickname());
                currentSeatsDTO.setState(1);
                // equippedItems 저장
                currentSeatsDTO.setEquippedItems(setEquippedItems(items));
                save(roomCode, currentSeatsDTO);
                return currentSeatsDTO;
            }
        }
        return null; // 저장 못했을 때 (빈 자리가 없을 때)
    }

    private EquippedItemsDTO setEquippedItems(List<ItemDTO> items) {
        EquippedItemsDTO equippedItemsDTO = new EquippedItemsDTO();
        for (ItemDTO item : items) {
            String itemType = itemRepository.findByItemSeq(item.getItemSeq()).getType();
            switch (itemType) {
                case "face" :
                    equippedItemsDTO.setFace(item.getImage());
                    break;
                case "cap" :
                    equippedItemsDTO.setCap(item.getImage());
                    break;
                case "clothing" :
                    equippedItemsDTO.setClothing(item.getImage());
            }
        }
        return equippedItemsDTO;
    }

    // 저장
    private void save(String roomCode, CurrentSeatsDTO currentSeatsDTO) {
        String key = generateKey(roomCode);
        try {
            String jsonData = objectMapper.writeValueAsString(currentSeatsDTO); // 객체 -> 스트링형식의 jsons
            redisTemplate.opsForHash().put(key, Integer.toString(currentSeatsDTO.getOrder()), jsonData);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // 방에서 나갈때
    public void setUserExitInfo(String roomCode, Long userSeq) {
        String key = generateKey(roomCode);
        List<CurrentSeatsDTO> currentSeatsDTOList = getUserEnterInfo(roomCode);
        for (CurrentSeatsDTO currentSeatsDTO : currentSeatsDTOList) {
            if (currentSeatsDTO.getUserSeq() == userSeq) {
                currentSeatsDTO.setState(0);
                currentSeatsDTO.setUserSeq(0L);
                currentSeatsDTO.setNickname("");
                save(roomCode, currentSeatsDTO);
            }
        }
    }

    // [최대인원 설정] 해당 roomCode 방의 최대 인원 1 증가
    public boolean increaseUserCount(String roomCode) {
        String key = generateKey(roomCode);
        List<CurrentSeatsDTO> currentSeatsDTOList = getUserEnterInfo(roomCode);
        for (CurrentSeatsDTO currentSeatsDTO : currentSeatsDTOList) {
            if (currentSeatsDTO.getState() == -1) {
                currentSeatsDTO.setState(0);
                save(roomCode, currentSeatsDTO);
                return true;
            }
        }
        return false;
    }

    // [최대인원 설정] 해당 roomCode 방의 최대 인원 1 감소
    public boolean decreaseUserCount(String roomCode) {
        String key = generateKey(roomCode);
        List<CurrentSeatsDTO> currentSeatsDTOList = getUserEnterInfo(roomCode);
        for (CurrentSeatsDTO currentSeatsDTO : currentSeatsDTOList) {
            if (currentSeatsDTO.getState() == 0) {
                currentSeatsDTO.setState(-1);
                save(roomCode, currentSeatsDTO);
                return true;
            }
        }
        return false;
    }

    public int getMaxUserNum(String roomCode) {
        String key = generateKey(roomCode);
        List<CurrentSeatsDTO> currentSeatsDTOList = getUserEnterInfo(roomCode);
        int maxUserNum = 0;
        for(CurrentSeatsDTO currentSeatsDTO : currentSeatsDTOList) {
            if(currentSeatsDTO.getState() == 0 || currentSeatsDTO.getState() == 1) {
                maxUserNum++;
            }
        }
        return maxUserNum;
    }

    public List<CurrentSeatsDTO> getUserEnterInfo(String roomCode) {
        String key = generateKey(roomCode);
        List<Object> list = redisTemplate.opsForHash().values(key);
        return toCurrentSeatsDTO(list);
    }

    // 해당 roomCode 방에 몇명의 유저가 있는지
    public int getUsingSeats(String roomCode) {
        List<CurrentSeatsDTO> currentSeatsDTOList = getUserEnterInfo(roomCode);
        int usingSeats = 0;
        for (CurrentSeatsDTO currentSeatsDTO : currentSeatsDTOList) {
            if (currentSeatsDTO.getState() == 1) {
                usingSeats++;
            }
        }
        return usingSeats;
    }

    public String generateKey(String roomCode) {
        return KEY_PREFIX + "roomCode:" + roomCode;
    }

    public List<CurrentSeatsDTO> toCurrentSeatsDTO(List<Object> jsonList) {
        List<CurrentSeatsDTO> currentSeatsDTOList = new ArrayList<>();
        for(Object jsonData : jsonList) {
            try {
                CurrentSeatsDTO currentSeatsDTO = objectMapper.readValue(jsonData.toString(), CurrentSeatsDTO.class);
                currentSeatsDTOList.add(currentSeatsDTO);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return currentSeatsDTOList;
    }
}
