import React, { useState, useEffect, useRef } from 'react';
import { Task } from '../types';
import { X } from 'lucide-react';
import { motion, useDragControls } from 'motion/react';

interface ScheduledBlockProps {
  task: Task;
  hourHeight: number; // pixels per hour
  style?: React.CSSProperties; // Add style prop
  onUnschedule: (id: string) => void;
  onUpdateDuration: (id: string, duration: number) => void;
  onEdit: () => void;
  onUpdateStartTime: (id: string, startHour: number) => void;
  onScheduleTask: (id: string, startHour: number, duration?: number) => void;
}

export const ScheduledBlock: React.FC<ScheduledBlockProps> = ({ 
  task, 
  hourHeight, 
  style,
  onUnschedule, 
  onUpdateDuration,
  onEdit,
  onUpdateStartTime,
  onScheduleTask
}) => {
  if (!task.scheduled) return null;

  // Local state for smooth resizing interaction
  const [localStart, setLocalStart] = useState(task.scheduled.startHour);
  const [localDuration, setLocalDuration] = useState(task.scheduled.duration);
  const [isResizing, setIsResizing] = useState(false);
  const [isDragging, setIsDragging] = useState(false);

  // Refs to track latest values without stale closures
  const latestValues = useRef({ start: task.scheduled.startHour, duration: task.scheduled.duration });

  const containerRef = useRef<HTMLDivElement>(null);
  const dragControls = useDragControls();

  // Sync with props when not interacting
  useEffect(() => {
    if (!isResizing && !isDragging) {
      setLocalStart(task.scheduled!.startHour);
      setLocalDuration(task.scheduled!.duration);
      latestValues.current = { 
        start: task.scheduled!.startHour, 
        duration: task.scheduled!.duration 
      };
    }
  }, [task.scheduled, isResizing, isDragging]);

  // Helper to snap to 15 mins
  const snapToQuarter = (val: number) => Math.round(val * 4) / 4;

  // --- Resize Handlers ---

  const handleResizeStart = (e: React.PointerEvent, direction: 'top' | 'bottom') => {
    e.preventDefault();
    e.stopPropagation();
    setIsResizing(true);

    const startY = e.clientY;
    const initialStart = localStart;
    const initialDuration = localDuration;

    // Update ref immediately
    latestValues.current = { start: initialStart, duration: initialDuration };

    const handlePointerMove = (moveEvent: PointerEvent) => {
      const deltaPixels = moveEvent.clientY - startY;
      const deltaHours = deltaPixels / hourHeight;

      if (direction === 'bottom') {
        // Changing duration only
        const newDuration = Math.max(15, initialDuration + (deltaHours * 60));
        setLocalDuration(newDuration);
        latestValues.current.duration = newDuration;
      } else {
        // Top resize: Change start AND duration to keep bottom fixed
        
        let newStart = initialStart + deltaHours;
        let newDuration = initialDuration - (deltaHours * 60);

        // Limit min duration
        if (newDuration < 15) {
          const diff = 15 - newDuration; 
          newDuration = 15;
          newStart -= diff / 60; 
        }
        
        // Clamp start time to 0
        if (newStart < 0) {
            newStart = 0;
            newDuration = (initialStart + initialDuration / 60) * 60;
        }

        setLocalStart(newStart);
        setLocalDuration(newDuration);
        
        latestValues.current.start = newStart;
        latestValues.current.duration = newDuration;
      }
    };

    const handlePointerUp = () => {
      setIsResizing(false);
      window.removeEventListener('pointermove', handlePointerMove);
      window.removeEventListener('pointerup', handlePointerUp);

      // Use values from Ref, not stale closures
      const finalStart = latestValues.current.start;
      const finalDuration = latestValues.current.duration;

      if (direction === 'bottom') {
        // Snap duration
        const snappedDuration = Math.max(15, Math.round(finalDuration / 15) * 15);
        onUpdateDuration(task.id, snappedDuration);
      } else {
        // Snap start and duration
        const snappedStart = snapToQuarter(finalStart);
        
        // Calculate duration based on fixed bottom to avoid gaps
        // Bottom was calculated based on floating point values
        const rawBottom = finalStart + finalDuration / 60;
        const snappedBottom = snapToQuarter(rawBottom);
        
        // Re-calculate duration from snapped boundaries
        const finalSyncedDuration = Math.max(15, (snappedBottom - snappedStart) * 60);
        
        onScheduleTask(task.id, snappedStart, finalSyncedDuration);
      }
    };

    window.addEventListener('pointermove', handlePointerMove);
    window.addEventListener('pointerup', handlePointerUp);
  };

  // --- Drag Handlers (Move) ---
  const handleDragEnd = (event: any, info: any) => {
    setIsDragging(false);
    
    // Only commit if moved significantly
    if (Math.abs(info.offset.y) < 2) return;

    const movedHours = info.offset.y / hourHeight;
    const newStart = snapToQuarter(task.scheduled!.startHour + movedHours);
    const clampedStart = Math.max(0, Math.min(23.75, newStart));
    
    onUpdateStartTime(task.id, clampedStart);
  };

  const startOffset = localStart * hourHeight;
  const height = (localDuration / 60) * hourHeight;

  return (
    <motion.div 
      ref={containerRef}
      className={`absolute z-10 rounded-md border shadow-md flex flex-col overflow-hidden text-xs
        ${task.isBig3 
          ? 'bg-[#FF9680] border-[#E07D67] text-[#5C2014] shadow-orange-900/10' 
          : 'bg-[#809CFF] border-[#6B85D8] text-[#1E2958] shadow-blue-900/10'}
        ${isDragging ? 'cursor-grabbing z-50 shadow-xl scale-[1.02]' : 'cursor-grab'}
        ${isResizing ? 'z-50 select-none' : ''}
        transition-all hover:brightness-105
      `}
      style={{ 
        top: startOffset, 
        height: height,
        // Apply dynamic width and left styles, defaulting to full width if not provided
        left: style?.left ?? '8px', 
        width: style?.width ?? 'calc(100% - 16px)',
        zIndex: isDragging ? 50 : (isResizing ? 40 : 10),
      }}
      // Drag configuration
      drag="y"
      dragMomentum={false}
      dragListener={false} // We manually start drag
      dragControls={dragControls}
      onDragStart={() => setIsDragging(true)}
      onDragEnd={handleDragEnd}
      // Click to edit
      onClick={(e) => {
        if (!isDragging && !isResizing) {
           onEdit();
        }
      }}
    >
      {/* Top Handle */}
      <div 
        className="absolute top-0 left-0 right-0 h-3 -mt-1.5 cursor-n-resize flex justify-center items-center z-20 group"
        onPointerDown={(e) => handleResizeStart(e, 'top')}
      >
         <div className="w-8 h-1 bg-current opacity-20 group-hover:opacity-60 rounded-full shadow-sm transition-opacity"></div>
      </div>

      {/* Content Area - Draggable Trigger */}
      <div 
        className="flex-1 p-2 select-none"
        onPointerDown={(e) => {
           // Only start drag if not clicking a button/handle
           dragControls.start(e);
        }}
      >
        <div className="flex justify-between items-start gap-1">
          <span className="font-semibold truncate pointer-events-none">{task.content}</span>
          <button 
            onPointerDown={(e) => e.stopPropagation()}
            onClick={(e) => {
              e.stopPropagation();
              onUnschedule(task.id);
            }}
            className="opacity-60 hover:opacity-100 hover:text-red-600 transition-colors p-0.5 cursor-pointer z-30"
          >
            <X size={12} />
          </button>
        </div>
        <div className="mt-1 opacity-75 font-mono text-[10px] font-medium pointer-events-none">
          {formatTime(localStart)} - {formatTime(localStart + localDuration / 60)}
        </div>
      </div>

      {/* Bottom Handle */}
      <div 
        className="absolute bottom-0 left-0 right-0 h-3 -mb-1.5 cursor-s-resize flex justify-center items-center z-20 group"
        onPointerDown={(e) => handleResizeStart(e, 'bottom')}
      >
        <div className="w-8 h-1 bg-current opacity-20 group-hover:opacity-60 rounded-full shadow-sm transition-opacity"></div>
      </div>
    </motion.div>
  );
};

function formatTime(decimalHour: number): string {
  const hours = Math.floor(decimalHour);
  const minutes = Math.round((decimalHour - hours) * 60);
  // Handle edge case where minutes could be 60 due to rounding
  let h = hours;
  let m = minutes;
  if (m >= 59.5) {
    h += 1;
    m = 0;
  }
  return `${h.toString().padStart(2, '0')}:${m.toString().padStart(2, '0')}`;
}
