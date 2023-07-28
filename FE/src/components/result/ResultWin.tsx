import { RABBIT_MAP } from "../../constants/RabbitMap";
import { RABBIT_STATE_MAP } from "../../constants/RabbitStateMap";

interface ResultLoseProps {
  index: number;
}

export const ResultWin = ({ index }: ResultLoseProps) => {
  const nickname = "아주긴닉네임이야";
  const job = "테수투";
  return (
    <>
      <div className="flex flex-col justify-center w-[200px] h-250px]">
        <div className="w-[200px] flex flex-col items-center text-[22.5px]">
          <p className="w-auto">{nickname}</p>
          <p className="w-auto">{job}</p>
        </div>
        <div className="flex justify-center items-center w-[200px] h-[160px]">
          <img className="w-[200px] h-[200x]" src={RABBIT_MAP[index].IMG[RABBIT_STATE_MAP.STAND]} />
        </div>
      </div>
    </>
  );
};
