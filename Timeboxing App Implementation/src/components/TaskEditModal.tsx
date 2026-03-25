import React, { useState, useEffect } from 'react';
import { Task, RecurrenceConfig } from '../types';
import { X, Clock, Tag, AlignLeft, Repeat, Bell } from 'lucide-react';

interface TaskEditModalProps {
  isOpen: boolean;
  onClose: () => void;
  task: Task | null;
  onSave: (taskId: string, updates: Partial<Task>) => void;
}

// Generate time options in 15-minute intervals
const generateTimeOptions = () => {
  const options = [];
  for (let i = 0; i < 24; i++) {
    for (let j = 0; j < 60; j += 15) {
      const h = i.toString().padStart(2, '0');
      const m = j.toString().padStart(2, '0');
      options.push(`${h}:${m}`);
    }
  }
  return options;
};

const TIME_OPTIONS = generateTimeOptions();
const WEEKDAYS = ['S', 'M', 'T', 'W', 'T', 'F', 'S'];

const decimalToTimeStr = (decimal: number): string => {
  const h = Math.floor(decimal);
  const m = Math.round((decimal - h) * 60);
  const snappedM = Math.round(m / 15) * 15;
  
  let finalH = h;
  let finalM = snappedM;
  if (finalM === 60) {
    finalH += 1;
    finalM = 0;
  }
  
  return `${finalH.toString().padStart(2, '0')}:${finalM.toString().padStart(2, '0')}`;
};

const timeStrToDecimal = (timeStr: string): number => {
  const [h, m] = timeStr.split(':').map(Number);
  return h + m / 60;
};

export const TaskEditModal: React.FC<TaskEditModalProps> = ({
  isOpen,
  onClose,
  task,
  onSave,
}) => {
  const [content, setContent] = useState('');
  const [note, setNote] = useState('');
  const [tags, setTags] = useState<string[]>([]);
  const [tagInput, setTagInput] = useState('');
  
  // Time range state
  const [hasTime, setHasTime] = useState(false);
  const [startTimeStr, setStartTimeStr] = useState('09:00');
  const [endTimeStr, setEndTimeStr] = useState('09:30');
  const [enableReminder, setEnableReminder] = useState(false);

  // Recurrence state
  const [isRecurring, setIsRecurring] = useState(false);
  const [recurrenceType, setRecurrenceType] = useState<RecurrenceConfig['type']>('daily');
  const [selectedDays, setSelectedDays] = useState<number[]>([]); // 0=Sun, 6=Sat

  useEffect(() => {
    if (isOpen) {
      if (task) {
        setContent(task.content);
        setNote(task.note || '');
        // Handle legacy tag if necessary, but we are using tags[] now
        const initialTags = task.tags || (task.tag ? [task.tag] : []);
        setTags(initialTags);
        
        if (task.scheduled) {
          setHasTime(true);
          const start = task.scheduled.startHour;
          const end = start + (task.scheduled.duration / 60);
          setStartTimeStr(decimalToTimeStr(start));
          setEndTimeStr(decimalToTimeStr(end));
          setEnableReminder(task.scheduled.reminder || false);
        } else {
          setHasTime(false);
          setStartTimeStr('09:00');
          setEndTimeStr('09:30');
          setEnableReminder(false);
        }

        if (task.recurrence) {
          setIsRecurring(true);
          // Handle legacy string recurrence ('daily' | 'weekly')
          if (typeof task.recurrence === 'string') {
             setRecurrenceType(task.recurrence);
             if (task.recurrence === 'weekly') {
               // Default to today if weekly
               setSelectedDays([new Date().getDay()]);
             } else {
               setSelectedDays([]);
             }
          } else {
             setRecurrenceType(task.recurrence.type);
             setSelectedDays(task.recurrence.repeatDays || []);
          }
        } else {
          setIsRecurring(false);
          setRecurrenceType('daily');
          setSelectedDays([]);
        }
      } else {
        // Reset for new task
        setContent('');
        setNote('');
        setTags([]);
        setHasTime(false);
        setStartTimeStr('09:00');
        setEndTimeStr('09:30');
        setEnableReminder(false);
        setIsRecurring(false);
        setRecurrenceType('daily');
        setSelectedDays([]);
      }
    }
  }, [task, isOpen]);

  const handleStartTimeChange = (newStart: string) => {
    setStartTimeStr(newStart);
    const startVal = timeStrToDecimal(newStart);
    const endVal = timeStrToDecimal(endTimeStr);
    
    if (endVal <= startVal) {
      const newEndVal = startVal + 0.5;
      if (newEndVal < 24) {
        setEndTimeStr(decimalToTimeStr(newEndVal));
      } else {
        setEndTimeStr('23:45');
      }
    }
  };

  const handleTagInputKeyDown = (e: React.KeyboardEvent<HTMLInputElement>) => {
    if (e.key === 'Enter') {
      e.preventDefault();
      addTag();
    }
  };

  const addTag = () => {
    const trimmed = tagInput.trim();
    if (!trimmed) return;
    const cleanTag = trimmed.startsWith('#') ? trimmed.slice(1) : trimmed;
    if (cleanTag && !tags.includes(cleanTag)) {
      setTags([...tags, cleanTag]);
    }
    setTagInput('');
  };

  const removeTag = (tagToRemove: string) => {
    setTags(tags.filter(t => t !== tagToRemove));
  };

  const toggleDay = (dayIndex: number) => {
    if (selectedDays.includes(dayIndex)) {
      setSelectedDays(selectedDays.filter(d => d !== dayIndex));
    } else {
      setSelectedDays([...selectedDays, dayIndex]);
    }
  };

  const handleSave = () => {
    const updates: Partial<Task> = {
      content,
      note,
      tags,
    };

    if (hasTime) {
      const startDecimal = timeStrToDecimal(startTimeStr);
      const endDecimal = timeStrToDecimal(endTimeStr);
      let duration = (endDecimal - startDecimal) * 60;
      if (duration < 15) duration = 15;
      updates.scheduled = {
        startHour: startDecimal,
        duration: duration,
        reminder: enableReminder,
      };
    } else {
      if (task?.scheduled && !hasTime) {
         updates.scheduled = null; 
      }
    }

    if (isRecurring) {
      let finalDays = selectedDays;
      // Logic to auto-set days based on type if empty
      if (recurrenceType === 'daily') {
         finalDays = [0, 1, 2, 3, 4, 5, 6];
      }
      
      updates.recurrence = {
        type: recurrenceType,
        repeatDays: finalDays
      };
    } else {
      updates.recurrence = null;
    }

    onSave(task ? task.id : 'NEW_TASK', updates);
    onClose();
  };

  if (!isOpen) return null;

  const getEndTimeOptions = () => {
    const startIdx = TIME_OPTIONS.indexOf(startTimeStr);
    return TIME_OPTIONS.slice(startIdx + 1);
  };

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/50 backdrop-blur-sm">
      <div className="w-full max-w-md bg-[#1E1E1E] rounded-2xl shadow-xl border border-[#333] overflow-hidden max-h-[90vh] overflow-y-auto no-scrollbar">
        {/* Header */}
        <div className="flex items-center justify-between p-4 border-b border-[#333]">
          <h2 className="text-lg font-semibold text-white">
            {task ? 'Edit Task' : 'New Task'}
          </h2>
          <button onClick={onClose} className="p-2 text-gray-400 hover:text-white rounded-full hover:bg-[#333]">
            <X size={20} />
          </button>
        </div>

        {/* Body */}
        <div className="p-4 space-y-6">
          {/* 1. Title */}
          <div>
            <label className="block text-sm font-medium text-gray-400 mb-1">Title</label>
            <input
              type="text"
              value={content}
              onChange={(e) => setContent(e.target.value)}
              className="w-full bg-[#2C2C2C] border border-[#333] rounded-lg px-4 py-2 text-white focus:outline-none focus:border-[#8687E7]"
              placeholder="What do you need to do?"
              autoFocus={!task}
            />
          </div>

          {/* 2. Tags */}
          <div>
            <label className="block text-sm font-medium text-gray-400 mb-1 flex items-center gap-2">
              <Tag size={14} /> Tags
            </label>
            <div className="flex flex-wrap items-center gap-2 p-2 bg-[#2C2C2C] border border-[#333] rounded-lg min-h-[42px]">
              {tags.map((t) => (
                <span
                  key={t}
                  className="flex items-center gap-1 px-2 py-1 bg-[#8687E7]/20 text-[#8687E7] rounded text-xs border border-[#8687E7]/30"
                >
                  #{t}
                  <button onClick={() => removeTag(t)} className="hover:text-white transition-colors">
                    <X size={10} />
                  </button>
                </span>
              ))}
              <input 
                type="text" 
                value={tagInput} 
                onChange={(e) => setTagInput(e.target.value)}
                onKeyDown={handleTagInputKeyDown}
                onBlur={addTag}
                placeholder={tags.length === 0 ? "Type #tag and press Enter..." : "Add tag..."}
                className="bg-transparent text-sm text-white focus:outline-none min-w-[120px] flex-1"
              />
            </div>
          </div>

          {/* 3. Memo */}
          <div>
            <label className="block text-sm font-medium text-gray-400 mb-1 flex items-center gap-2">
              <AlignLeft size={14} /> Memo
            </label>
            <textarea
              value={note}
              onChange={(e) => setNote(e.target.value)}
              className="w-full bg-[#2C2C2C] border border-[#333] rounded-lg px-4 py-2 text-white focus:outline-none focus:border-[#8687E7] min-h-[60px] resize-none text-sm"
              placeholder="Details..."
            />
          </div>

          {/* 4. Recurrence (Habit) */}
          <div className="bg-[#2C2C2C] p-4 rounded-lg border border-[#333]">
             <div className="flex items-center justify-between mb-2">
                <label className="text-sm font-medium text-gray-400 flex items-center gap-2">
                  <Repeat size={14} /> Recurring Habit
                </label>
                <button 
                  onClick={() => setIsRecurring(!isRecurring)}
                  className={`w-10 h-5 rounded-full relative transition-colors ${isRecurring ? 'bg-[#8687E7]' : 'bg-[#444]'}`}
                >
                  <div className={`absolute top-1 w-3 h-3 rounded-full bg-white transition-transform ${isRecurring ? 'left-6' : 'left-1'}`} />
                </button>
             </div>

             {isRecurring && (
               <div className="mt-4 space-y-3 animate-in slide-in-from-top-2 duration-200 border-t border-[#333] pt-4">
                  <div className="flex gap-2">
                    {(['daily', 'weekly', 'custom'] as const).map(type => (
                      <button
                        key={type}
                        onClick={() => setRecurrenceType(type)}
                        className={`px-3 py-1.5 rounded text-xs capitalize transition-colors border ${
                          recurrenceType === type 
                           ? 'bg-[#8687E7] text-white border-[#8687E7]' 
                           : 'bg-[#1E1E1E] text-gray-400 border-[#333] hover:border-gray-500'
                        }`}
                      >
                        {type}
                      </button>
                    ))}
                  </div>

                  {(recurrenceType === 'custom' || recurrenceType === 'weekly') && (
                    <div className="pt-2">
                      <div className="text-xs text-gray-500 mb-2">Select Days</div>
                      <div className="flex justify-between gap-1">
                        {WEEKDAYS.map((day, idx) => {
                           const isSelected = selectedDays.includes(idx);
                           return (
                             <button
                               key={idx}
                               onClick={() => toggleDay(idx)}
                               className={`w-8 h-8 rounded-full text-xs font-medium flex items-center justify-center transition-all ${
                                 isSelected
                                   ? 'bg-[#8687E7] text-white shadow-lg shadow-[#8687E7]/30'
                                   : 'bg-[#1E1E1E] text-gray-500 border border-[#333] hover:border-gray-500'
                               }`}
                             >
                               {day}
                             </button>
                           );
                        })}
                      </div>
                    </div>
                  )}
               </div>
             )}
          </div>

          {/* 5. Time Range */}
          <div className="bg-[#2C2C2C] p-4 rounded-lg border border-[#333]">
            <div className="flex items-center justify-between mb-2">
              <label className="text-sm font-medium text-gray-400 flex items-center gap-2">
                <Clock size={14} /> Time Block (Optional)
              </label>
              <button 
                onClick={() => setHasTime(!hasTime)}
                className={`w-10 h-5 rounded-full relative transition-colors ${hasTime ? 'bg-[#8687E7]' : 'bg-[#444]'}`}
              >
                <div className={`absolute top-1 w-3 h-3 rounded-full bg-white transition-transform ${hasTime ? 'left-6' : 'left-1'}`} />
              </button>
            </div>
            
            {hasTime && (
              <div className="mt-4 space-y-4 animate-in slide-in-from-top-2 duration-200 border-t border-[#333] pt-4">
                <div className="flex items-center gap-3">
                  <div className="flex-1">
                    <span className="text-xs text-gray-500 block mb-1">Start</span>
                    <select 
                      value={startTimeStr}
                      onChange={(e) => handleStartTimeChange(e.target.value)}
                      className="w-full bg-[#1E1E1E] text-white text-sm rounded-lg px-3 py-2 border border-[#333] focus:outline-none focus:border-[#8687E7]"
                    >
                      {TIME_OPTIONS.map(t => (
                        <option key={`start-${t}`} value={t}>{t}</option>
                      ))}
                    </select>
                  </div>
                  <span className="text-gray-500 mt-5">→</span>
                  <div className="flex-1">
                    <span className="text-xs text-gray-500 block mb-1">End</span>
                    <select 
                      value={endTimeStr}
                      onChange={(e) => setEndTimeStr(e.target.value)}
                      className="w-full bg-[#1E1E1E] text-white text-sm rounded-lg px-3 py-2 border border-[#333] focus:outline-none focus:border-[#8687E7]"
                    >
                      {getEndTimeOptions().map(t => (
                        <option key={`end-${t}`} value={t}>{t}</option>
                      ))}
                    </select>
                  </div>
                </div>
                
                {/* Duration & Reminder */}
                <div className="flex items-center justify-between pt-2 border-t border-[#333] mt-2">
                   <div className="text-xs text-gray-500">
                     Duration: <span className="text-[#8687E7] font-medium">
                       {Math.round((timeStrToDecimal(endTimeStr) - timeStrToDecimal(startTimeStr)) * 60)} min
                     </span>
                   </div>
                   
                   <div className="flex items-center gap-2">
                      <span className="text-xs text-gray-400 flex items-center gap-1">
                         <Bell size={10} /> Alert
                      </span>
                      <button 
                         onClick={() => setEnableReminder(!enableReminder)}
                         className={`w-8 h-4 rounded-full relative transition-colors ${enableReminder ? 'bg-[#8687E7]' : 'bg-[#444]'}`}
                      >
                         <div className={`absolute top-0.5 w-3 h-3 rounded-full bg-white transition-transform ${enableReminder ? 'left-4.5' : 'left-0.5'}`} />
                      </button>
                   </div>
                </div>
              </div>
            )}
          </div>

        </div>

        {/* Footer */}
        <div className="p-4 border-t border-[#333] flex justify-end gap-3 bg-[#1E1E1E]">
          <button onClick={onClose} className="px-4 py-2 text-gray-400 hover:text-white transition-colors">
            Cancel
          </button>
          <button onClick={handleSave} className="px-6 py-2 bg-[#8687E7] hover:bg-[#7576d1] text-white rounded-lg font-medium transition-colors">
            {task ? 'Save' : 'Create'}
          </button>
        </div>
      </div>
    </div>
  );
};