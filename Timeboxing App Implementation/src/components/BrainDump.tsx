import React, { useState } from 'react';
import { Task } from '../types';
import { DraggableTask } from './DraggableTask';
import { Plus, ListTodo } from 'lucide-react';

interface BrainDumpProps {
  tasks: Task[];
  onAddTask: (content: string) => void;
  onToggleBig3: (id: string) => void;
  onToggleComplete: (id: string) => void;
}

export const BrainDump: React.FC<BrainDumpProps> = ({ tasks, onAddTask, onToggleBig3, onToggleComplete }) => {
  const [inputValue, setInputValue] = useState('');

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    if (inputValue.trim()) {
      onAddTask(inputValue.trim());
      setInputValue('');
    }
  };

  const big3Tasks = tasks.filter(t => t.isBig3);
  const otherTasks = tasks.filter(t => !t.isBig3);
  
  // Tasks that are not scheduled yet, or we show all and let them be dragged multiple times?
  // Usually timeboxing moves them. Let's filter out scheduled ones from the "Todo" view if we want strict movement.
  // But for better UX, keeping them in list but marking as "Scheduled" is often better.
  // The user prompt implies "Brain Dump" -> "Timebox".
  // Let's keep all tasks here but maybe dim scheduled ones? 
  // For now, I will render all.

  return (
    <div className="flex flex-col h-full bg-gray-50/50">
      <div className="p-4 border-b border-gray-100 bg-white">
        <h2 className="text-lg font-bold text-gray-800 flex items-center gap-2 mb-1">
          <ListTodo className="text-indigo-600" size={20} />
          Brain Dump
        </h2>
        <p className="text-xs text-gray-500 mb-4">머릿속의 할 일을 모두 적어보세요.</p>
        
        <form onSubmit={handleSubmit} className="relative">
          <input
            type="text"
            value={inputValue}
            onChange={(e) => setInputValue(e.target.value)}
            placeholder="새로운 할 일 입력..."
            className="w-full pl-3 pr-10 py-2.5 bg-gray-50 border border-gray-200 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-indigo-500/20 focus:border-indigo-500 transition-all"
          />
          <button 
            type="submit"
            className="absolute right-2 top-1/2 -translate-y-1/2 p-1.5 bg-white text-indigo-600 rounded-md hover:bg-indigo-50 transition-colors"
          >
            <Plus size={16} />
          </button>
        </form>
      </div>

      <div className="flex-1 overflow-y-auto p-4 space-y-6 no-scrollbar">
        {/* Big 3 Section */}
        {big3Tasks.length > 0 && (
          <section>
            <h3 className="text-xs font-bold text-red-500 uppercase tracking-wider mb-3 flex items-center gap-2">
              <span className="w-1.5 h-1.5 rounded-full bg-red-500"></span>
              Today's Big 3
            </h3>
            <div className="space-y-2">
              {big3Tasks.map(task => (
                <DraggableTask 
                  key={task.id} 
                  task={task} 
                  onToggleBig3={onToggleBig3}
                  onToggleComplete={onToggleComplete}
                />
              ))}
            </div>
          </section>
        )}

        {/* Other Tasks Section */}
        <section>
           <h3 className="text-xs font-bold text-gray-400 uppercase tracking-wider mb-3 flex items-center gap-2">
              <span className="w-1.5 h-1.5 rounded-full bg-gray-300"></span>
              Tasks
            </h3>
          {otherTasks.length === 0 && big3Tasks.length === 0 && (
            <div className="text-center py-10 text-gray-400 text-sm">
              할 일이 없습니다.<br />위 입력창에 할 일을 추가해보세요.
            </div>
          )}
          <div className="space-y-2">
            {otherTasks.map(task => (
              <DraggableTask 
                key={task.id} 
                task={task} 
                onToggleBig3={onToggleBig3}
                onToggleComplete={onToggleComplete}
              />
            ))}
          </div>
        </section>
      </div>
    </div>
  );
};
