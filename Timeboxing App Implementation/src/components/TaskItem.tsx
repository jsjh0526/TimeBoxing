import React from 'react';
import { useDrag, useDrop } from 'react-dnd';
import { motion, PanInfo, useMotionValue, useTransform } from 'motion/react';
import { Task, ItemTypes } from '../types';
import { Check, Star, Trash2, GripVertical, Repeat } from 'lucide-react';

interface TaskItemProps {
  task: Task;
  onToggleBig3?: (id: string) => void;
  onToggleComplete: (id: string) => void;
  onDelete?: (id: string) => void;
  onMove?: (dragId: string, hoverId: string) => void;
  onEdit?: () => void;
  showStar?: boolean;
  isMinimal?: boolean;
}

export const TaskItem: React.FC<TaskItemProps> = ({ 
  task, 
  onToggleBig3, 
  onToggleComplete, 
  onDelete,
  onMove,
  onEdit,
  showStar = false,
  isMinimal = false
}) => {
  const [{ isDragging }, drag] = useDrag(() => ({
    type: ItemTypes.TASK,
    item: { id: task.id, type: ItemTypes.TASK },
    collect: (monitor) => ({
      isDragging: !!monitor.isDragging(),
    }),
  }), [task.id]);

  const [, drop] = useDrop({
    accept: ItemTypes.TASK,
    hover(item: { id: string }) {
      if (!onMove || item.id === task.id) return;
      onMove(item.id, task.id);
    },
  });

  const x = useMotionValue(0);
  const backgroundOpacity = useTransform(x, [-100, 0], [1, 0]);
  const contentX = useTransform(x, (value) => value < -100 ? -100 : value > 0 ? 0 : value);

  const handleDragEnd = (event: any, info: PanInfo) => {
    if (info.offset.x < -80 && onDelete) {
      onDelete(task.id);
    }
  };

  // Determine styles based on state
  const getContainerStyles = () => {
    if (task.isBig3) {
      return `
        bg-[#2a2a2a] border border-[#8687E7] shadow-[0_0_10px_rgba(134,135,231,0.1)]
        ${task.completed ? 'opacity-60' : ''}
      `;
    }
    return `
      bg-[#363636] border border-transparent
      ${task.completed ? 'opacity-60' : ''}
    `;
  };

  return (
    <div 
      ref={(node) => {
        if (onMove) drop(node);
      }}
      className={`relative ${isMinimal ? 'mb-2' : 'mb-3'} overflow-hidden rounded-lg ${isDragging ? 'opacity-50' : ''}`}
    >
      {/* Swipe Background (Delete) */}
      <motion.div 
        style={{ opacity: backgroundOpacity }}
        className="absolute inset-0 bg-red-500 flex items-center justify-end px-6 rounded-lg"
      >
        <Trash2 className="text-white" size={24} />
      </motion.div>

      {/* Content */}
      <motion.div
        drag="x"
        dragConstraints={{ left: 0, right: 0 }}
        dragElastic={0.1}
        onDragEnd={handleDragEnd}
        style={{ x: contentX }}
        className="relative z-10"
      >
        <div
          className={`
            flex items-center gap-2 transition-all rounded-lg
            ${getContainerStyles()}
            ${isMinimal ? 'p-3' : 'p-4'}
          `}
        >
           {/* Drag Handle */}
           <div ref={drag} className="cursor-grab active:cursor-grabbing p-1 -ml-1 text-gray-600 hover:text-gray-400">
             <GripVertical size={16} />
           </div>

          {/* Checkbox (Circle Style) */}
          <button
            onClick={(e) => { e.stopPropagation(); onToggleComplete(task.id); }}
            className={`
              flex-shrink-0 w-6 h-6 rounded-full border-2 flex items-center justify-center transition-all duration-200
              ${task.completed 
                ? 'bg-[#8687E7] border-[#8687E7]' 
                : 'border-[#8687E7] bg-transparent'}
            `}
          >
            {task.completed && <Check size={14} className="text-white" strokeWidth={3} />}
          </button>

          {/* Text */}
          <div 
            className="flex-1 min-w-0 ml-2 cursor-pointer"
            onClick={(e) => {
              if (onEdit) onEdit();
            }}
          >
            <p className={`font-medium truncate ${isMinimal ? 'text-sm' : 'text-base'} ${task.completed ? 'text-gray-500' : 'text-white'}`}>
              {task.content}
            </p>
            {!isMinimal && task.tags && task.tags.length > 0 && (
              <div className="flex gap-1 mt-1 flex-wrap">
                {task.tags.map(tag => (
                  <span key={tag} className="text-[10px] text-gray-300 bg-[#444] px-1.5 py-0.5 rounded">
                    #{tag}
                  </span>
                ))}
              </div>
            )}
            {!isMinimal && task.recurrence && (
               <span className="text-[10px] text-[#8687E7] bg-[#8687E7]/10 px-2 py-0.5 rounded mt-1 inline-flex items-center gap-1">
                 <Repeat size={10} />
                 {(() => {
                    const r = task.recurrence;
                    if (typeof r === 'string') return r; // Legacy fallback
                    if (r.type === 'daily') return 'Every Day';
                    if (r.type === 'weekdays') return 'Weekdays';
                    if (r.type === 'weekly') return 'Weekly';
                    if (r.type === 'custom' && r.repeatDays) {
                      const days = ['Sun', 'Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat'];
                      return r.repeatDays.map(d => days[d]).join(', ');
                    }
                    return 'Recurring';
                 })()}
               </span>
            )}
            {!isMinimal && task.scheduled && !task.completed && (
              <span className="text-[10px] text-gray-400 block mt-0.5">
                {Math.floor(task.scheduled.startHour)}:{(task.scheduled.startHour % 1 * 60).toString().padStart(2, '0')} 
                {' - '}
                {Math.floor(task.scheduled.startHour + task.scheduled.duration / 60)}:{((task.scheduled.startHour + task.scheduled.duration / 60) % 1 * 60).toString().padStart(2, '0')}
              </span>
            )}
          </div>

          {/* Actions */}
          <div className="flex items-center gap-3">
            {showStar && onToggleBig3 && (
              <button 
                onClick={(e) => { e.stopPropagation(); onToggleBig3(task.id); }}
                className={`transition-colors ${task.isBig3 ? 'text-yellow-400' : 'text-gray-500 hover:text-gray-300'}`}
              >
                <Star size={20} className={task.isBig3 ? 'fill-current' : ''} />
              </button>
            )}
          </div>
        </div>
      </motion.div>
    </div>
  );
};
