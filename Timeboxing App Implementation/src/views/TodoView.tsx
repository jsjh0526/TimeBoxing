import React, { useState } from 'react';
import { Task } from '../types';
import { TaskItem } from '../components/TaskItem';
import { Plus, Star, ChevronDown, ChevronUp } from 'lucide-react';

interface TodoViewProps {
  tasks: Task[];
  overdueTasks?: Task[];
  onMoveOverdueToToday?: () => void;
  onClearOverdueTasks?: () => void;
  onAddTask: (content: string) => void;
  onOpenAddModal?: (initialContent?: string) => void;
  onToggleBig3: (id: string) => void;
  onToggleComplete: (id: string) => void;
  onDeleteTask: (id: string) => void;
  onMoveTask: (dragId: string, hoverId: string) => void;
  onEditTask: (task: Task) => void;
}

export const TodoView: React.FC<TodoViewProps> = ({
  tasks,
  overdueTasks = [],
  onMoveOverdueToToday,
  onClearOverdueTasks,
  onAddTask,
  onOpenAddModal,
  onToggleBig3,
  onToggleComplete,
  onDeleteTask,
  onMoveTask,
  onEditTask
}) => {
  const [inputValue, setInputValue] = useState('');
  const [isOtherHabitsExpanded, setIsOtherHabitsExpanded] = useState(false);
  const [showOverdue, setShowOverdue] = useState(true);

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    if (inputValue.trim()) {
      onAddTask(inputValue.trim());
      setInputValue('');
    }
  };

  const handleClearOverdue = () => {
    if (onClearOverdueTasks) onClearOverdueTasks();
    setShowOverdue(false);
  };

  const todayDay = new Date().getDay(); // 0(Sun) - 6(Sat)

  // Helper to check if a task is valid for today
  const isTaskForToday = (task: Task) => {
     if (!task.recurrence) return true; // Brain Dump items assume today/always visible
     
     if (typeof task.recurrence === 'string') return true; // Legacy
     
     const { type, repeatDays } = task.recurrence;
     if (type === 'daily') return true;
     
     // Check specific days if defined
     if (repeatDays && repeatDays.length > 0) {
       return repeatDays.includes(todayDay);
     }
     
     if (type === 'weekly') return true; // Fallback
     
     return false;
  };

  const big3Tasks = tasks.filter(t => t.isBig3 && isTaskForToday(t));
  
  // Split recurring tasks into Today vs Others
  const allRecurringTasks = tasks.filter(t => t.recurrence);
  
  const todayRecurringTasks: Task[] = [];
  const otherRecurringTasks: Task[] = [];
  
  allRecurringTasks.forEach(t => {
     if (isTaskForToday(t)) {
       todayRecurringTasks.push(t);
     } else {
       otherRecurringTasks.push(t);
     }
  });

  // Brain Dump: Not Big 3 AND Not Recurring
  const brainDumpTasks = tasks.filter(t => !t.isBig3 && !t.recurrence);

  return (
    <div className="flex flex-col h-full bg-[#121212]">
      {/* Top Bar */}
      <header className="px-6 py-5 bg-[#121212] flex justify-between items-center z-10">
        <h1 className="text-2xl font-bold text-white tracking-tight">Tasks & Habits</h1>
      </header>

      <div className="flex-1 overflow-y-auto no-scrollbar px-6 space-y-8 pb-24">
        
        {/* Input */}
        <form onSubmit={handleSubmit} className="relative mt-2 flex gap-2">
          <input
            id="task-input"
            type="text"
            value={inputValue}
            onChange={(e) => setInputValue(e.target.value)}
            placeholder="Add a new task..."
            className="flex-1 px-4 py-4 bg-[#363636] border border-transparent rounded-xl text-white placeholder-gray-500 focus:outline-none focus:border-[#8687E7] focus:ring-1 focus:ring-[#8687E7] transition-all"
          />
          <button
            type="button"
            onClick={() => {
               if (onOpenAddModal) onOpenAddModal(inputValue);
               setInputValue('');
            }}
            className="w-14 bg-[#8687E7] text-white rounded-xl flex items-center justify-center shadow-lg shadow-[#8687E7]/30 hover:bg-[#7a7be2] transition-colors"
          >
            <Plus size={24} />
          </button>
        </form>

        {/* Overdue Tasks Banner */}
        {showOverdue && overdueTasks.length > 0 && (
          <div className="bg-[#2a2a2a] border border-[#FF9680]/30 rounded-xl p-4 flex flex-col gap-3 animate-in fade-in slide-in-from-top-2">
            <div className="flex justify-between items-start">
              <div>
                <h3 className="text-[#FF9680] font-medium text-sm">Yesterday's Unfinished Tasks</h3>
                <p className="text-gray-400 text-xs mt-1">
                  You have {overdueTasks.length} tasks left from yesterday.
                </p>
              </div>
              <button onClick={handleClearOverdue} className="text-gray-500 hover:text-gray-300 p-1">
                <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                  <path d="M18 6L6 18M6 6l12 12" />
                </svg>
              </button>
            </div>
            <div className="flex gap-2 mt-1">
              <button 
                onClick={() => {
                  if (onMoveOverdueToToday) onMoveOverdueToToday();
                  setShowOverdue(false);
                }}
                className="flex-1 bg-[#FF9680]/20 text-[#FF9680] hover:bg-[#FF9680]/30 py-2 rounded-lg text-xs font-bold transition-colors"
              >
                Move to Today
              </button>
              <button 
                onClick={handleClearOverdue}
                className="px-4 bg-[#363636] text-gray-400 hover:text-gray-200 py-2 rounded-lg text-xs font-bold transition-colors"
              >
                Dismiss
              </button>
            </div>
          </div>
        )}

        {/* Today's BIG 3 */}
        <section>
          <h2 className="text-sm font-medium text-gray-400 mb-4 flex items-center gap-2 uppercase tracking-wider">
            Today's Big 3
          </h2>
          <div className="space-y-3">
             {big3Tasks.length === 0 && (
               <div className="text-sm text-gray-600 italic px-2">
                 Select tasks below to prioritize
               </div>
             )}
             {big3Tasks.map(task => (
                <TaskItem 
                  key={task.id} 
                  task={task} 
                  onToggleBig3={onToggleBig3}
                  onToggleComplete={onToggleComplete}
                  onDelete={onDeleteTask}
                  showStar={true}
                  onMove={onMoveTask}
                  onEdit={() => onEditTask(task)}
                />
              ))}
          </div>
        </section>

        {/* Brain Dump (Random Todo) */}
        <section>
          <div className="flex items-center gap-2 mb-4">
             <h2 className="text-sm font-medium text-gray-400 uppercase tracking-wider">Brain Dump</h2>
             <span className="text-xs bg-[#363636] text-gray-400 px-2 py-0.5 rounded-full font-medium">{brainDumpTasks.length}</span>
          </div>
          <div className="space-y-3">
             {brainDumpTasks.length === 0 && (
                <div className="text-sm text-gray-600 italic px-2 py-4 text-center border border-dashed border-[#333] rounded-lg">
                  No random tasks. Clear mind!
                </div>
             )}
             {brainDumpTasks.map(task => (
                <TaskItem 
                  key={task.id} 
                  task={task} 
                  onToggleBig3={onToggleBig3}
                  onToggleComplete={onToggleComplete}
                  onDelete={onDeleteTask}
                  showStar={true}
                  onMove={onMoveTask}
                  onEdit={() => onEditTask(task)}
                />
              ))}
          </div>
        </section>

        {/* Recurring Tasks */}
        <section>
          <h2 className="text-sm font-medium text-gray-400 mb-4 uppercase tracking-wider flex items-center gap-2">
            Recurring Habits
            <span className="text-xs bg-[#363636] text-gray-400 px-2 py-0.5 rounded-full font-medium">{todayRecurringTasks.length}</span>
          </h2>
          <div className="space-y-3">
            {todayRecurringTasks.length === 0 && (
               <div className="text-sm text-gray-600 italic px-2">
                 No recurring habits for today.
               </div>
            )}
            {todayRecurringTasks.map(task => (
              <TaskItem 
                  key={task.id} 
                  task={task} 
                  onToggleBig3={onToggleBig3}
                  onToggleComplete={onToggleComplete}
                  onDelete={onDeleteTask}
                  showStar={true}
                  onMove={onMoveTask}
                  onEdit={() => onEditTask(task)}
              />
            ))}
          </div>

          {/* Other Recurring (Collapsible) */}
          {otherRecurringTasks.length > 0 && (
            <div className="mt-6 border-t border-[#333] pt-4">
               <button 
                 onClick={() => setIsOtherHabitsExpanded(!isOtherHabitsExpanded)}
                 className="flex items-center gap-2 text-sm text-gray-500 hover:text-gray-300 transition-colors w-full"
               >
                 {isOtherHabitsExpanded ? <ChevronUp size={16} /> : <ChevronDown size={16} />}
                 <span>Other Habits</span>
                 <span className="text-xs bg-[#2a2a2a] px-2 py-0.5 rounded-full">{otherRecurringTasks.length}</span>
               </button>
               
               {isOtherHabitsExpanded && (
                 <div className="space-y-3 mt-4 opacity-75">
                   {otherRecurringTasks.map(task => (
                      <TaskItem 
                          key={task.id} 
                          task={task} 
                          onToggleBig3={undefined} // Disable Big3
                          onToggleComplete={onToggleComplete}
                          onDelete={onDeleteTask}
                          showStar={false} // Hide star
                          // Disable moving for other days tasks to avoid confusion? Or keep it enabled.
                          // Let's keep it enabled so they can edit.
                          onEdit={() => onEditTask(task)}
                          isMinimal={true} // Use minimal style for less emphasis
                      />
                    ))}
                 </div>
               )}
            </div>
          )}
        </section>
      </div>
    </div>
  );
};
