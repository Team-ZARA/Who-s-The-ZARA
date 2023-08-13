import { RoomHeader } from "../components/room/RoomHeader";
import { RoomHeaderBtn } from "../components/room/RoomHeaderBtn";
import { RoomChat } from "../components/room/RoomChat";
import { RoomUserList } from "../components/room/RoomUserList";
import { RoomLayout } from "../layouts/RoomLayout";
import { useFetchAccessToken } from "../hooks/useFetchAccessToken";
import { useEffect } from "react";
import { useState } from "react";
import { CurSeats, JobSetting } from "../types/RoomSettingType";
import { useWebSocket } from "../context/socketContext";
import { defaultJobSetting, defaultCurSeats } from "../constants/room/defaultRoomInfo";
import { useNavigate, useParams, useLocation } from "react-router-dom";
import {
  SubChangeOwner,
  SubCurSeats,
  SubInitialRoomSetting,
  SubJobSetting,
  SubStart,
  SubTitle,
  SubChat,
  SubEnterChat,
  SubExitMessage,
  ChatInfo,
} from "../types/StompRoomSubType";
import { useAccessTokenState } from "../context/accessTokenContext";

export const Room = () => {
  useFetchAccessToken();
  const { roomCode } = useParams();
  const navigate = useNavigate();
  const { accessToken, userSeq } = useAccessTokenState();
  const [title, setTitle] = useState<string>("");
  const [amIOwner, setAmIOwner] = useState(false);
  const [jobSetting, setJobSetting] = useState<JobSetting>(defaultJobSetting);
  const [curSeats, setCurSeats] = useState<CurSeats>(defaultCurSeats);
  const [chatList, setChatList] = useState<ChatInfo[]>([]);
  const [ownerSeq, setOwnerSeq] = useState<number>(0);
  const { client } = useWebSocket();
  const location = useLocation();
  const isComeFromGame = location.state?.isComeFromGame;

  const subRoom = (roomCode: string) => {
    client?.subscribe(`/sub/room/${roomCode}`, (subData) => {
      const subDataBody = JSON.parse(subData.body);
      console.log("SUBSCRIBE ROOM");
      console.log(subDataBody);
      switch (subDataBody.type) {
        case "ROOM_ENTER_ROOM_SETTING":
          const initialRoomSettingData: SubInitialRoomSetting = subDataBody;
          setTitle(initialRoomSettingData.data.title);
          setAmIOwner(initialRoomSettingData.data.ownerSeq === userSeq);
          setOwnerSeq(initialRoomSettingData.data.ownerSeq);

          const { "1": _, "2": __, ...initJobSetting } = initialRoomSettingData.data.jobSetting;
          setJobSetting(initJobSetting);

          setCurSeats(initialRoomSettingData.data.curSeats.sort((a, b) => a.order - b.order));
          break;
        case "ROOM_ENTER_MESSAGE":
          const enterChatData: SubEnterChat = subDataBody;
          setChatList((prev) => [
            ...prev,
            {
              nickname: "",
              message: enterChatData.data,
              order: -1,
            },
          ]);
          break;
        case "ROOM_START":
          const startData: SubStart = subDataBody;
          startGame(startData.data);
          break;
        case "ROOM_CHAT":
          const chatData: SubChat = subDataBody;
          const newChat: ChatInfo = {
            nickname: chatData.data.nickname,
            message: chatData.data.message,
          };
          setChatList((prev) => [...prev, newChat]);
          break;
        case "ROOM_TITLE":
          const titleData: SubTitle = subDataBody;
          setTitle(titleData.data);
          break;
        case "ROOM_JOB_SETTING":
          const jobSettingData: SubJobSetting = subDataBody;
          setJobSetting(jobSettingData.data.jobSetting);
          break;
        case "ROOM_CHANGE_OWNER":
          const ownerData: SubChangeOwner = subDataBody;
          setAmIOwner(ownerData.data === userSeq);
          setOwnerSeq(ownerData.data);
          break;
        case "ROOM_CUR_SEATS":
          const curSeatsData: SubCurSeats = subDataBody;
          setCurSeats(curSeatsData.data.sort((a, b) => a.order - b.order));
          break;
        case "ROOM_EXIT":
          const exitData: SubExitMessage = subDataBody;
          setChatList((prev) => [
            ...prev,
            {
              nickname: "",
              message: exitData.data,
              order: -1,
            },
          ]);
          break;
        default:
          console.log("잘못된 타입의 데이터가 왔습니다.");
          break;
      }
    });
  };

  const pubEnterRoom = (roomCode: string) => {
    client?.publish({
      destination: `/pub/room/${roomCode}/enter`,
      headers: {
        Authorization: `Bearer ${accessToken}`,
      },
    });
  };

  const startGame = (gameCode: string) => {
    const userSeqOrderMap: { [userSeq: number]: number } = {};
    const userSeqListSortedByOrder: number[] = new Array(8).fill(0); // 길이 8의 배열을 0으로 채움

    curSeats.forEach((seat) => {
      if (seat.state === 1) {
        userSeqOrderMap[seat.userSeq] = seat.order;
      }
    });

    for (const userSeq in userSeqOrderMap) {
      const order = userSeqOrderMap[Number(userSeq)];
      if (order < userSeqListSortedByOrder.length) {
        userSeqListSortedByOrder[order] = Number(userSeq);
      }
    }

    navigate(`/game/${gameCode}`, {
      state: {
        userSeqOrderMap,
        roomCode,
        gameCode,
        userSeqListSortedByOrder,
      },
    });
  };

  useEffect(() => {
    if (!roomCode) return;
    if (isComeFromGame) return;
    subRoom(roomCode);
    pubEnterRoom(roomCode);

    return () => {
      setChatList([]);
    };
  }, [roomCode, isComeFromGame]);

  return (
    <RoomLayout>
      <div className="relative flex flex-wrap w-full justify-center items-center 3xl:px-[40px] px-[36px]">
        <div className="flex items-center w-full">
          <RoomHeader
            amIOwner={amIOwner}
            setTitle={setTitle}
            title={title}
            jobSetting={jobSetting}
            setJobSetting={setJobSetting}
          />
          <RoomHeaderBtn amIOwner={amIOwner} curSeats={curSeats} />
        </div>
        <div className="flex items-center w-full">
          <RoomChat chatList={chatList} curSeats={curSeats} />
          <RoomUserList curSeats={curSeats} setCurSeats={setCurSeats} ownerSeq={ownerSeq} amIOwner={amIOwner} />
        </div>
      </div>
    </RoomLayout>
  );
};
