import React from 'react';
import { NotificationItem } from '../types';
import { X, Bell, Trash2 } from 'lucide-react';
import { motion, AnimatePresence } from 'motion/react';

interface NotificationListProps {
  isOpen: boolean;
  onClose: () => void;
  notifications: NotificationItem[];
  onClearAll: () => void;
}

export const NotificationList: React.FC<NotificationListProps> = ({
  isOpen,
  onClose,
  notifications,
  onClearAll,
}) => {
  return (
    <AnimatePresence>
      {isOpen && (
        <>
          {/* Backdrop */}
          <motion.div
            initial={{ opacity: 0 }}
            animate={{ opacity: 1 }}
            exit={{ opacity: 0 }}
            onClick={onClose}
            className="fixed inset-0 z-40 bg-black/50 backdrop-blur-sm"
          />

          {/* Drawer */}
          <motion.div
            initial={{ x: '100%' }}
            animate={{ x: 0 }}
            exit={{ x: '100%' }}
            transition={{ type: 'spring', damping: 25, stiffness: 200 }}
            className="fixed top-0 right-0 bottom-0 z-50 w-80 bg-[#1E1E1E] shadow-2xl border-l border-[#333] flex flex-col"
          >
            {/* Header */}
            <div className="flex items-center justify-between p-4 border-b border-[#333]">
              <div className="flex items-center gap-2">
                <Bell size={18} className="text-[#8687E7]" />
                <h2 className="text-lg font-semibold text-white">Notifications</h2>
              </div>
              <button
                onClick={onClose}
                className="p-2 text-gray-400 hover:text-white rounded-full hover:bg-[#333] transition-colors"
              >
                <X size={20} />
              </button>
            </div>

            {/* List */}
            <div className="flex-1 overflow-y-auto p-4 space-y-4">
              {notifications.length === 0 ? (
                <div className="flex flex-col items-center justify-center h-full text-gray-500 gap-4">
                  <div className="w-16 h-16 bg-[#2C2C2C] rounded-full flex items-center justify-center">
                    <Bell size={24} className="text-gray-600" />
                  </div>
                  <p>No notifications yet</p>
                </div>
              ) : (
                notifications.map((notif) => (
                  <div
                    key={notif.id}
                    className={`p-3 rounded-lg border border-[#333] bg-[#2C2C2C] relative ${
                      !notif.read ? 'border-l-4 border-l-[#8687E7]' : ''
                    }`}
                  >
                    <div className="flex justify-between items-start mb-1">
                      <h3 className="font-medium text-white text-sm">{notif.title}</h3>
                      <span className="text-[10px] text-gray-500">
                        {new Date(notif.timestamp).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })}
                      </span>
                    </div>
                    <p className="text-xs text-gray-400 leading-relaxed">{notif.message}</p>
                  </div>
                ))
              )}
            </div>

            {/* Footer */}
            {notifications.length > 0 && (
              <div className="p-4 border-t border-[#333]">
                <button
                  onClick={onClearAll}
                  className="w-full py-2 bg-[#2C2C2C] hover:bg-[#333] text-gray-400 hover:text-white rounded-lg text-sm font-medium flex items-center justify-center gap-2 transition-colors border border-[#333]"
                >
                  <Trash2 size={16} />
                  Clear All
                </button>
              </div>
            )}
          </motion.div>
        </>
      )}
    </AnimatePresence>
  );
};
