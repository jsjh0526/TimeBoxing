import React, { useEffect, useState } from 'react';
import { Task } from '../types';
import { Bell, Check, X, Clock } from 'lucide-react';
import { motion, AnimatePresence } from 'motion/react';

interface AlarmModalProps {
  task: Task | null;
  onClose: () => void;
  onComplete: () => void;
  onSnooze: (minutes: number) => void;
}

export const AlarmModal: React.FC<AlarmModalProps> = ({
  task,
  onClose,
  onComplete,
  onSnooze
}) => {
  const [audio] = useState(new Audio('https://assets.mixkit.co/active_storage/sfx/2869/2869-preview.mp3'));

  useEffect(() => {
    if (task) {
      // Play sound
      audio.loop = true;
      audio.play().catch(e => console.log('Audio play failed', e));

      // Vibrate if supported
      if (navigator.vibrate) {
        navigator.vibrate([200, 100, 200]);
      }
    }

    return () => {
      audio.pause();
      audio.currentTime = 0;
    };
  }, [task, audio]);

  if (!task) return null;

  return (
    <AnimatePresence>
      <motion.div
        initial={{ opacity: 0 }}
        animate={{ opacity: 1 }}
        exit={{ opacity: 0 }}
        className="fixed inset-0 z-[100] flex items-center justify-center p-6 bg-black/80 backdrop-blur-md"
      >
        <motion.div
          initial={{ scale: 0.9, y: 20 }}
          animate={{ scale: 1, y: 0 }}
          className="w-full max-w-sm bg-[#1E1E1E] rounded-3xl p-8 flex flex-col items-center shadow-2xl border border-[#333] relative overflow-hidden"
        >
          {/* Background Gradient Animation */}
          <div className="absolute top-0 left-0 w-full h-1 bg-gradient-to-r from-[#8687E7] via-[#FF9680] to-[#8687E7] animate-gradient-x" />

          {/* Icon */}
          <div className="w-20 h-20 bg-[#8687E7]/20 rounded-full flex items-center justify-center mb-6 animate-pulse">
            <Bell size={40} className="text-[#8687E7]" />
          </div>

          {/* Content */}
          <h2 className="text-2xl font-bold text-white text-center mb-2">It's Time!</h2>
          <p className="text-gray-400 text-center mb-8 text-lg px-2 line-clamp-2">
            {task.content}
          </p>

          {/* Actions */}
          <div className="flex flex-col w-full gap-3">
            <button
              onClick={onComplete}
              className="w-full py-4 bg-[#8687E7] hover:bg-[#7576d1] text-white rounded-xl font-bold text-lg flex items-center justify-center gap-2 transition-transform active:scale-95"
            >
              <Check size={20} />
              Complete Task
            </button>
            
            <div className="flex gap-3">
              <button
                onClick={() => onSnooze(5)}
                className="flex-1 py-3 bg-[#333] hover:bg-[#444] text-white rounded-xl font-medium flex items-center justify-center gap-2 transition-colors"
              >
                <Clock size={18} />
                +5 Min
              </button>
              <button
                onClick={onClose}
                className="flex-1 py-3 bg-transparent border border-[#333] hover:bg-[#333] text-gray-400 hover:text-white rounded-xl font-medium flex items-center justify-center gap-2 transition-colors"
              >
                <X size={18} />
                Dismiss
              </button>
            </div>
          </div>
        </motion.div>
      </motion.div>
    </AnimatePresence>
  );
};
