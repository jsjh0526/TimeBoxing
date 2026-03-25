import React from 'react';
import { useDrag } from 'react-dnd';
import { Task, ItemTypes } from '../types';
import { GripVertical, Star, CheckCircle, Circle } from 'lucide-react';

interface DraggableTaskProps {
  task: Task;
  onToggleBig3: (id: string) => void;
  onToggleComplete: (id: string) => void;
}

export const DraggableTask: React.FC<DraggableTaskProps> = ({ task, onToggleBig3, onToggleComplete }) => {
  const [{ isDragging }, drag] = useDrag(() => ({
    type: ItemTypes.TASK,
    item: { id: task.id, type: ItemTypes.TASK },
    collect: (monitor) => ({
      isDragging: !!monitor.isDragging(),
    }),
  }), [task.id]);

  return (
    <div
      ref={drag}
      className={`
        group flex items-start gap-3 p-3 mb-2 rounded-lg border transition-all cursor-move
        ${isDragging ? 'opacity-50' : 'opacity-100'}
        ${task.isBig3 ? 'bg-red-50 border-red-200' : 'bg-white border-gray-200 hover:border-indigo-300 hover:shadow-sm'}
        ${task.completed ? 'opacity-60 bg-gray-50' : ''}
      `}
    >
      <div className="mt-1 text-gray-400 group-hover:text-gray-600 cursor-grab active:cursor-grabbing">
        <GripVertical size={16} />
      </div>
      
      <div className="flex-1 min-w-0">
        <p className={`text-sm font-medium truncate ${task.completed ? 'line-through text-gray-400' : 'text-gray-800'}`}>
          {task.content}
        </p>
        <div className="flex items-center gap-2 mt-1.5">
          <button 
            onClick={() => onToggleBig3(task.id)}
            className={`
              text-xs flex items-center gap-1 px-1.5 py-0.5 rounded transition-colors
              ${task.isBig3 
                ? 'text-red-600 bg-red-100 font-semibold' 
                : 'text-gray-400 hover:text-gray-600 hover:bg-gray-100'}
            `}
          >
            <Star size={12} className={task.isBig3 ? 'fill-current' : ''} />
            {task.isBig3 ? 'Big 3' : 'Big 3 선정'}
          </button>
        </div>
      </div>

      <button 
        onClick={() => onToggleComplete(task.id)}
        className={`mt-0.5 transition-colors ${task.completed ? 'text-indigo-500' : 'text-gray-300 hover:text-indigo-400'}`}
      >
        {task.completed ? <CheckCircle size={18} /> : <Circle size={18} />}
      </button>
    </div>
  );
};
