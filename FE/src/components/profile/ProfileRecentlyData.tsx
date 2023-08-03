import { ProfileRecentlyDataItem } from "./ProfileRecentlyDataItem";

export const ProfileRecentlyData = () => {
  const recentlyData = [
    {
      gameNo: 0,
      result: "승",
      role: "경찰",
      date: "2023-07-20",
      playtime: "04:20",
    },
    {
      gameNo: 1,
      result: "패",
      role: "의사",
      date: "2023-07-19",
      playtime: "14:20",
    },
    {
      gameNo: 2,
      result: "승",
      role: "자라",
      date: "2023-07-17",
      playtime: "11:20",
    },
    {
      gameNo: 3,
      result: "패",
      role: "자라",
      date: "2023-06-17",
      playtime: "10:20",
    },
    {
      gameNo: 4,
      result: "승",
      role: "토끼",
      date: "2023-07-15",
      playtime: "11:20",
    },
  ];

  return (
    <>
      <div className="3xl:p-[20px] p-[16px] 3xl:text-[36px] text-[28.8px] font-bold text-white">
        <ul className="flex text-center ">
          <li className="3xl:w-[200px] w-[160px]">결과</li>
          <li className="3xl:w-[240px] w-[192px]">내 역할</li>
          <li className="3xl:w-[240px] w-[192px]">진행 시간</li>
          <li className="3xl:w-[340px] w-[272px]">게임 일자</li>
        </ul>
        <hr className="3xl:my-[20px] my-[16px] w-full 3xl:border-[2px] border-[1.6px]" />
        {recentlyData.map((item) => (
          <ProfileRecentlyDataItem item={item} key={item.gameNo} />
        ))}
      </div>
    </>
  );
};
