import React from 'react';
import { Home, ListTodo, CalendarClock, Settings } from 'lucide-react';
import { Tab } from '../types';

interface BottomNavProps {
  currentTab: Tab;
  onTabChange: (tab: Tab) => void;
}

export const BottomNav: React.FC<BottomNavProps> = ({ currentTab, onTabChange }) => {
  const navItems: { id: Tab; label: string; icon: React.ReactNode }[] = [
    { id: 'home', label: '홈', icon: <Home size={20} /> },
    { id: 'todo', label: 'TODO', icon: <ListTodo size={20} /> },
    { id: 'timetable', label: '시간표', icon: <CalendarClock size={20} /> },
    { id: 'settings', label: '설정', icon: <Settings size={20} /> },
  ];

  return (
    <nav className="bg-[#363636] border-t border-[#363636] safe-area-bottom z-30">
      <div className="flex justify-around items-center h-20">
        {navItems.map((item) => (
          <button
            key={item.id}
            onClick={() => onTabChange(item.id)}
            className={`flex flex-col items-center justify-center w-full h-full space-y-1.5 transition-colors ${
              currentTab === item.id ? 'text-[#8687E7]' : 'text-gray-400 hover:text-white'
            }`}
          >
            {item.icon}
            <span className="text-[10px] font-medium">{item.label}</span>
          </button>
        ))}
      </div>
    </nav>
  );
};
