import React, { useRef, useMemo } from 'react';
import { useDrop } from 'react-dnd';
import { Task, ItemTypes } from '../types';
import { ScheduledBlock } from './ScheduledBlock';

interface TimeBoxGridProps {
  tasks: Task[];
  onScheduleTask: (id: string, startHour: number, duration?: number) => void;
  onUnscheduleTask: (id: string) => void;
  onUpdateDuration: (id: string, duration: number) => void;
  onEditTask: (task: Task) => void;
  onUpdateStartTime: (id: string, startHour: number) => void;
}

export const TimeBoxGrid: React.FC<TimeBoxGridProps> = ({ 
  tasks, 
  onScheduleTask, 
  onUnscheduleTask,
  onUpdateDuration,
  onEditTask,
  onUpdateStartTime
}) => {
  const ref = useRef<HTMLDivElement>(null);
  const HOUR_HEIGHT = 64; // h-16 = 4rem = 64px

  const [{ isOver }, drop] = useDrop(() => ({
    accept: ItemTypes.TASK,
    drop: (item: { id: string }, monitor) => {
      const offset = monitor.getClientOffset();
      const containerRect = ref.current?.getBoundingClientRect();
      
      if (offset && containerRect) {
        // Calculate Y relative to container
        const relativeY = offset.y - containerRect.top;
        
        // Calculate hour
        // 0px = 00:00
        const hourOffset = relativeY / HOUR_HEIGHT;
        
        // Round to nearest 15 mins (0.25)
        const roundedHourOffset = Math.round(hourOffset * 4) / 4;
        
        // Clamp between 0 and 24
        const startHour = Math.max(0, Math.min(23.75, roundedHourOffset));

        onScheduleTask(item.id, startHour);
      }
    },
    collect: (monitor) => ({
      isOver: !!monitor.isOver(),
    }),
  }), [onScheduleTask]);

  // Combine ref for drop and measuring
  const setRefs = (element: HTMLDivElement) => {
    // @ts-ignore
    drop(element);
    // @ts-ignore
    ref.current = element;
  };

  const hours = Array.from({ length: 24 }, (_, i) => i);

  // --- Overlap Calculation Logic ---
  const scheduledTasksWithLayout = useMemo(() => {
    const scheduled = tasks.filter(t => t.scheduled).map(t => ({
      ...t,
      // Helper properties for calculation
      start: t.scheduled!.startHour,
      end: t.scheduled!.startHour + (t.scheduled!.duration / 60)
    }));

    // Sort by start time
    scheduled.sort((a, b) => a.start - b.start);

    const result: Array<{ task: Task, style: React.CSSProperties }> = [];
    
    // Simple greedy approach to group overlapping tasks
    // We iterate through sorted tasks and group them if they overlap with the group's bounds
    
    // However, complex overlaps (A overlaps B, B overlaps C, but A doesn't overlap C)
    // require a more robust "column" placement or just simple N-split for the whole cluster.
    // Let's implement the "Cluster N-Split" strategy for simplicity and robustness:
    // Any chain of overlapping tasks forms a cluster. All tasks in a cluster get width = 1/N.
    
    let i = 0;
    while (i < scheduled.length) {
      const cluster = [scheduled[i]];
      let clusterEnd = scheduled[i].end;
      let j = i + 1;

      // Expand cluster
      while (j < scheduled.length) {
        // If task[j] overlaps with ANY part of the cluster so far...
        // Actually, strictly speaking, if task[j] starts before task[i] ends, they overlap directly.
        // But we want to group transitive overlaps too?
        // Let's stick to: if task[j].start < clusterEnd, it belongs to this cluster visually.
        
        if (scheduled[j].start < clusterEnd) {
          cluster.push(scheduled[j]);
          if (scheduled[j].end > clusterEnd) {
             clusterEnd = scheduled[j].end;
          }
          j++;
        } else {
          break;
        }
      }

      // Assign layout for this cluster
      const count = cluster.length;
      const GAP = 6; // px gap between columns
      
      cluster.forEach((t, index) => {
        result.push({
          task: t,
          style: {
            // Subtract gap from width to create space between blocks
            width: `calc(((100% - 16px) / ${count}) - ${GAP}px)`, 
            // Left position remains based on the slot start
            left: `calc(8px + ((100% - 16px) / ${count}) * ${index})`
          }
        });
      });

      i = j;
    }

    return result;
  }, [tasks]);


  return (
    <div 
      ref={setRefs}
      className={`flex-1 relative pt-6 transition-colors ${isOver ? 'bg-[#809CFF]/10' : ''}`}
    >
      {/* Grid Lines */}
      {hours.map(hour => (
        <div key={hour} className="h-16 border-b border-[#333] relative group">
          {/* Half hour guideline on hover or always faint */}
          <div className="absolute top-1/2 w-full border-t border-dashed border-[#333] group-hover:border-[#444]"></div>
        </div>
      ))}

      {/* Scheduled Tasks */}
      {scheduledTasksWithLayout.map(({ task, style }) => (
        <ScheduledBlock 
          key={task.id} 
          task={task} 
          hourHeight={HOUR_HEIGHT}
          style={style}
          onUnschedule={onUnscheduleTask}
          onUpdateDuration={onUpdateDuration}
          onEdit={() => onEditTask(task)}
          onUpdateStartTime={onUpdateStartTime}
          onScheduleTask={onScheduleTask}
        />
      ))}
      
      {/* Current Time Indicator (Optional) */}
    </div>
  );
};
