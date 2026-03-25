import React, { useState } from 'react';
import { Task } from '../types';
import { TimeAxis } from '../components/TimeAxis';
import { TimeBoxGrid } from '../components/TimeBoxGrid';
import { format, addDays, subDays } from 'date-fns';
import { ChevronLeft, ChevronRight, Calendar, LayoutList } from 'lucide-react';

interface TimeTableViewProps {
  tasks: Task[];
  onScheduleTask: (id: string, startHour: number, duration?: number) => void;
  onUnscheduleTask: (id: string) => void;
  onUpdateDuration: (id: string, duration: number) => void;
  onEditTask: (task: Task) => void;
  onUpdateStartTime: (id: string, startHour: number) => void;
}

export const TimeTableView: React.FC<TimeTableViewProps> = ({
  tasks,
  onScheduleTask,
  onUnscheduleTask,
  onUpdateDuration,
  onEditTask,
  onUpdateStartTime
}) => {
  const [currentDate, setCurrentDate] = useState(new Date());
  const [viewMode, setViewMode] = useState<'daily' | 'weekly'>('daily');

  const handlePrevDay = () => setCurrentDate(subDays(currentDate, 1));
  const handleNextDay = () => setCurrentDate(addDays(currentDate, 1));
  const handleToday = () => setCurrentDate(new Date());

  const isToday = currentDate.toDateString() === new Date().toDateString();

  const visibleTasks = tasks.filter(task => {
    // 1. 반복 설정이 없는 할일은 단발성이므로 '오늘'에만 표시
    if (!task.recurrence) return isToday;

    const currentDayIndex = currentDate.getDay(); // 0(Sun) - 6(Sat)

    // 2. 구버전 데이터(string) 호환성 유지
    if (typeof task.recurrence === 'string') {
      if (task.recurrence === 'daily') return true;
      if (task.recurrence === 'weekdays') return currentDayIndex >= 1 && currentDayIndex <= 5;
      return true; // weekly 등의 경우 요일 정보가 없으므로 일단 표시
    }

    // 3. 신버전 데이터(object) 요일별 필터링
    const { type, repeatDays } = task.recurrence;
    
    if (type === 'daily') return true;
    
    if (type === 'weekdays') {
      return currentDayIndex >= 1 && currentDayIndex <= 5; // 1(월) ~ 5(금)
    }
    
    if ((type === 'weekly' || type === 'custom') && Array.isArray(repeatDays)) {
      return repeatDays.includes(currentDayIndex);
    }
    
    return false;
  });

  return (
    <div className="flex flex-col h-full bg-[#121212]">
      {/* Top Bar */}
      <header className="px-6 py-5 border-b border-[#333] flex justify-between items-center bg-[#121212] z-10">
        <h1 className="text-xl font-bold text-white tracking-tight">Time Blocks</h1>
      </header>

      {/* Date Navigation */}
      <div className="px-6 py-4 flex items-center justify-between border-b border-[#333]">
        <button onClick={handlePrevDay} className="p-2 hover:bg-[#363636] rounded-full text-gray-400 transition-colors">
          <ChevronLeft size={20} />
        </button>
        <div className="flex flex-col items-center cursor-pointer group" onClick={handleToday}>
          <span className="text-base font-bold text-white group-hover:text-[#8687E7] transition-colors">{format(currentDate, 'yyyy.MM.dd')}</span>
          <span className="text-xs text-gray-500 font-medium uppercase tracking-wide mt-1">{format(currentDate, 'EEEE')}</span>
        </div>
        <button onClick={handleNextDay} className="p-2 hover:bg-[#363636] rounded-full text-gray-400 transition-colors">
          <ChevronRight size={20} />
        </button>
      </div>

      {/* Timeline Content */}
      <div className="flex-1 overflow-hidden relative">
        <div className="absolute inset-0 overflow-y-auto no-scrollbar pb-24">
          <div className="flex px-4 py-8 min-h-[1200px]">
            <TimeAxis />
            <TimeBoxGrid 
              tasks={visibleTasks}
              onScheduleTask={onScheduleTask}
              onUnscheduleTask={onUnscheduleTask}
              onUpdateDuration={onUpdateDuration}
              onEditTask={onEditTask}
              onUpdateStartTime={onUpdateStartTime}
            />
          </div>
        </div>
      </div>
    </div>
  );
};
