import React, { useState } from 'react';
import { Task } from '../types';
import { TaskItem } from '../components/TaskItem';
import { TimeBoxGrid } from '../components/TimeBoxGrid';
import { TimeAxis } from '../components/TimeAxis';
import { Bell, ChevronDown, ChevronUp, Plus, Star } from 'lucide-react';
import { format } from 'date-fns';

interface HomeViewProps {
  tasks: Task[];
  onAddTask: (content: string) => void;
  onToggleBig3: (id: string) => void;
  onToggleComplete: (id: string) => void;
  onScheduleTask: (id: string, startHour: number, duration?: number) => void;
  onUnscheduleTask: (id: string) => void;
  onUpdateDuration: (id: string, duration: number) => void;
  onUpdateTask: (id: string, updates: Partial<Task>) => void;
  onEditTask: (task: Task) => void;
  onUpdateStartTime: (id: string, startHour: number) => void;
  onOpenNotifications: () => void;
  unreadCount?: number;
}

export const HomeView: React.FC<HomeViewProps> = ({
  tasks,
  onAddTask,
  onToggleBig3,
  onToggleComplete,
  onScheduleTask,
  onUnscheduleTask,
  onUpdateDuration,
  onUpdateTask,
  onEditTask,
  onUpdateStartTime,
  onOpenNotifications,
  unreadCount = 0,
}) => {
  const [isTodoExpanded, setIsTodoExpanded] = useState(true);
  const [newTaskInput, setNewTaskInput] = useState('');
  
  const today = new Date();
  const todayDay = today.getDay();

  // Helper to check if a recurring task is for today
  const isTaskForToday = (task: Task) => {
    if (!task.recurrence) return true; // Non-recurring tasks are always "for now"
    if (typeof task.recurrence === 'string') return true; // Legacy
    
    const { type, repeatDays } = task.recurrence;
    
    if (type === 'daily') return true;
    
    // For weekly or custom, check specific days if defined
    if (repeatDays && repeatDays.length > 0) {
      return repeatDays.includes(todayDay);
    }
    
    // Fallback for weekly without specific days (assume always valid or needs migration)
    if (type === 'weekly') return true; 
    
    return false;
  };
  
  // Big 3 must be for today
  const big3Tasks = tasks.filter(t => t.isBig3 && isTaskForToday(t));
  
  // Brain Dump in Home should exclude recurring tasks (they belong to habits/schedule)
  const brainDumpTasks = tasks.filter(t => !t.isBig3 && !t.recurrence);
  
  // Tasks to show in TimeBoxGrid (All non-recurring + Today's recurring)
  const scheduleTasks = tasks.filter(t => isTaskForToday(t));


  const handleAddTask = (e: React.FormEvent) => {
    e.preventDefault();
    if (newTaskInput.trim()) {
      onAddTask(newTaskInput.trim());
      setNewTaskInput('');
    }
  };

  const PortraitLayout = () => (
    <div className="flex flex-col h-full overflow-hidden bg-[#121212]">
      {/* Top Bar */}
      <header className="px-6 py-5 bg-[#121212] flex justify-between items-center z-10">
        <div>
          <h1 className="text-2xl font-bold text-white tracking-tight">{format(today, 'yyyy.MM.dd')}</h1>
          <p className="text-gray-400 text-sm font-medium mt-0.5">{format(today, 'EEEE')}</p>
        </div>
        <button 
          onClick={onOpenNotifications}
          className="w-10 h-10 bg-[#363636] rounded-full flex items-center justify-center text-white relative hover:bg-[#4a4a4a] transition-colors"
        >
          <Bell size={20} className="text-[#8687E7]" />
          {unreadCount > 0 && (
            <span className="absolute top-2.5 right-2.5 w-2 h-2 bg-red-500 rounded-full border border-[#363636]"></span>
          )}
        </button>
      </header>

      <div className="flex-1 overflow-y-auto no-scrollbar">
        <div className="px-6 space-y-8 pb-24">
          
          {/* BIG 3 Section */}
          <section className="bg-[#1E1E1E] p-4 rounded-xl border border-[#333]">
            <h2 className="text-sm font-bold text-[#FF9680] mb-3 uppercase tracking-wider flex items-center gap-2">
              <Star size={14} fill="#FF9680" /> Top Priorities
            </h2>
            <div className="space-y-3">
              {big3Tasks.length === 0 ? (
                <div className="text-gray-500 text-sm text-center py-6 bg-[#2a2a2a] rounded-lg border border-dashed border-[#444]">
                  Tap stars to add Priority Tasks
                </div>
              ) : (
                big3Tasks.map(task => (
                  <TaskItem 
                    key={task.id} 
                    task={task} 
                    onToggleComplete={onToggleComplete}
                    isMinimal={true}
                    onEdit={() => onEditTask(task)}
                  />
                ))
              )}
            </div>
          </section>

          {/* Brain Dump Section */}
          <section>
            <div className="flex items-center justify-between mb-4">
              <h2 className="text-sm font-medium text-gray-400 uppercase tracking-wider flex items-center gap-2">
                 Brain Dump <span className="text-[#8687E7] ml-1">({brainDumpTasks.length})</span>
              </h2>
              <button 
                onClick={() => setIsTodoExpanded(!isTodoExpanded)}
                className="text-gray-500 p-1 hover:bg-[#363636] rounded transition-colors"
              >
                {isTodoExpanded ? <ChevronUp size={18} /> : <ChevronDown size={18} />}
              </button>
            </div>
            
            {isTodoExpanded && (
              <div className="space-y-3">
                 {brainDumpTasks.length === 0 ? (
                  <div className="text-gray-600 text-sm text-center py-4 italic opacity-50">
                    Empty mind, peaceful life.
                  </div>
                ) : (
                  brainDumpTasks.map(task => (
                    <TaskItem 
                      key={task.id} 
                      task={task} 
                      onToggleComplete={onToggleComplete}
                      onEdit={() => onEditTask(task)}
                    />
                  ))
                )}
              </div>
            )}
          </section>

          {/* Time Blocks Section (Preview) */}
          <section>
            <h2 className="text-sm font-medium text-gray-400 mb-4 uppercase tracking-wider">Schedule Preview</h2>
            <div className="bg-[#363636] rounded-xl overflow-y-auto no-scrollbar h-[500px] relative border border-[#444]">
               <div className="flex min-h-full">
                 <div className="bg-[#1E1E1E]">
                   <TimeAxis />
                 </div>
                 <div className="flex-1 bg-[#121212]">
                   <TimeBoxGrid 
                     tasks={scheduleTasks}
                     onScheduleTask={onScheduleTask}
                     onUnscheduleTask={onUnscheduleTask}
                     onUpdateDuration={onUpdateDuration}
                     onEditTask={onEditTask}
                     onUpdateStartTime={onUpdateStartTime}
                   />
                 </div>
               </div>
            </div>
          </section>
        </div>
      </div>
    </div>
  );

  const LandscapeLayout = () => (
    <div className="flex h-full bg-[#121212] overflow-hidden text-white">
      {/* Left Panel (30%) */}
      <div className="w-[30%] min-w-[320px] bg-[#1E1E1E] border-r border-[#333] flex flex-col p-6 overflow-y-auto no-scrollbar z-10">
        <header className="mb-6">
          <h1 className="text-2xl font-bold text-white mb-1">{format(today, 'yyyy.MM.dd')}</h1>
          <p className="text-gray-400 text-sm">{format(today, 'EEEE')}</p>
        </header>
        
        <div className="space-y-8 flex-1">
           {/* BIG 3 */}
           <section className="bg-[#121212] p-4 rounded-xl border border-[#333] shadow-inner">
            <h2 className="text-xs font-bold text-[#FF9680] uppercase tracking-wider mb-4 flex items-center gap-2">
               <Star size={14} fill="#FF9680" /> Top Priorities
            </h2>
            <div className="space-y-2">
              {big3Tasks.map(task => (
                <TaskItem 
                  key={task.id} 
                  task={task} 
                  onToggleComplete={onToggleComplete} 
                  isMinimal={true} 
                  onEdit={() => onEditTask(task)}
                />
              ))}
              {big3Tasks.length === 0 && (
                <p className="text-xs text-gray-600 text-center py-4 border border-dashed border-[#333] rounded-lg">
                  Select up to 3 priority tasks
                </p>
              )}
            </div>
           </section>

           {/* Brain Dump */}
           <section className="flex flex-col h-full min-h-0">
             <div className="flex justify-between items-center mb-3">
               <h2 className="text-xs font-bold text-gray-500 uppercase tracking-wider flex items-center gap-2">
                 Brain Dump <span className="bg-[#333] text-gray-300 px-1.5 py-0.5 rounded-full text-[10px]">{brainDumpTasks.length}</span>
               </h2>
               <button onClick={() => setIsTodoExpanded(!isTodoExpanded)} className="text-gray-500 hover:text-white transition-colors">
                 {isTodoExpanded ? <ChevronUp size={16} /> : <ChevronDown size={16} />}
               </button>
             </div>
             
             {isTodoExpanded && (
               <div className="space-y-2 overflow-y-auto pr-1 -mr-1 flex-1 min-h-[100px]">
                 {brainDumpTasks.map(task => (
                   <TaskItem 
                    key={task.id} 
                    task={task} 
                    onToggleComplete={onToggleComplete} 
                    onEdit={() => onEditTask(task)}
                   />
                 ))}
                 {brainDumpTasks.length === 0 && (
                   <div className="text-gray-700 text-xs text-center py-10">
                     Your mind is clear.
                   </div>
                 )}
               </div>
             )}
           </section>
        </div>
      </div>

      {/* Center & Right */}
      <div className="flex-1 flex flex-col bg-[#121212] relative overflow-hidden">
         <div className="absolute inset-0 pointer-events-none opacity-[0.05] z-0" style={{ backgroundImage: 'linear-gradient(#fff 1px, transparent 1px)', backgroundSize: '100% 40px' }}></div>
         
         {/* Shared Scroll Container for Axis and Grid */}
         <div className="flex-1 overflow-y-auto no-scrollbar relative z-0">
            <div className="flex min-h-full">
              {/* Time Axis Column */}
              <div className="w-16 flex-shrink-0 bg-[#1E1E1E] border-r border-[#333]">
                  <TimeAxis />
              </div>

              {/* Grid Column */}
              <div className="flex-1 relative">
                  <TimeBoxGrid 
                     tasks={scheduleTasks}
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
    </div>
  );

  return (
    <>
      <div className="landscape:hidden h-full">
        <PortraitLayout />
      </div>
      <div className="hidden landscape:flex h-full w-full">
        <LandscapeLayout />
      </div>
    </>
  );
};
