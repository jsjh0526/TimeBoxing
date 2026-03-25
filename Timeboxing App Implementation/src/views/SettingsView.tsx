import React, { useState } from 'react';
import { Bell, Database, AlertTriangle } from 'lucide-react';

export const SettingsView: React.FC<{ onTestAlarm: () => void }> = ({ onTestAlarm }) => {
  const [notifications, setNotifications] = useState({
    start: true,
    end: false,
    summary: true,
  });

  return (
    <div className="flex flex-col h-full bg-[#121212]">
      <header className="px-6 py-5 bg-[#121212] border-b border-[#333]">
        <h1 className="text-xl font-bold text-white">Settings</h1>
      </header>

      <div className="flex-1 overflow-y-auto no-scrollbar p-6 pb-24 space-y-6">
        
        {/* Notifications */}
        <section className="bg-[#363636] rounded-xl overflow-hidden">
          <div className="px-5 py-4 border-b border-[#444] flex items-center gap-3">
            <Bell size={18} className="text-[#8687E7]" />
            <h2 className="text-sm font-bold text-white">Notifications</h2>
          </div>
          <div className="divide-y divide-[#444]">
            <div className="px-5 py-4 flex justify-between items-center">
              <span className="text-sm text-gray-300">Start Time Alert</span>
              <Toggle checked={notifications.start} onChange={() => setNotifications({...notifications, start: !notifications.start})} />
            </div>
            <div className="px-5 py-4 flex justify-between items-center">
              <span className="text-sm text-gray-300">End Time Alert</span>
              <Toggle checked={notifications.end} onChange={() => setNotifications({...notifications, end: !notifications.end})} />
            </div>
            <div className="px-5 py-4 flex justify-between items-center">
              <span className="text-sm text-gray-300">Daily Summary</span>
              <Toggle checked={notifications.summary} onChange={() => setNotifications({...notifications, summary: !notifications.summary})} />
            </div>
             <div className="px-5 py-4 flex justify-between items-center border-t border-[#444]">
               <button 
                  onClick={onTestAlarm}
                  className="w-full py-2 bg-[#8687E7]/20 border border-[#8687E7] text-[#8687E7] rounded-lg text-sm font-medium hover:bg-[#8687E7] hover:text-white transition-colors"
               >
                 Test Alarm Screen
               </button>
             </div>
          </div>
        </section>

        {/* Data */}
        <section className="bg-[#363636] rounded-xl overflow-hidden">
          <div className="px-5 py-4 border-b border-[#444] flex items-center gap-3">
            <Database size={18} className="text-[#86E786]" />
            <h2 className="text-sm font-bold text-white">Data</h2>
          </div>
          <div className="p-5 space-y-3">
             <button className="w-full py-3 border border-[#444] rounded-lg text-sm font-medium text-gray-300 hover:bg-[#444] hover:text-white transition-colors">
               Backup Data
             </button>
             <button 
               className="w-full py-3 border border-red-900/30 bg-red-900/10 text-red-400 rounded-lg text-sm font-medium flex items-center justify-center gap-2 hover:bg-red-900/20 transition-colors"
               onClick={() => confirm("Are you sure you want to reset all data?")}
             >
               <AlertTriangle size={16} />
               Reset All Data
             </button>
          </div>
        </section>

        <div className="text-center text-xs text-gray-600 pt-4 pb-8">
          Version 1.0.0
        </div>
      </div>
    </div>
  );
};

const Toggle = ({ checked, onChange }: { checked: boolean, onChange: () => void }) => (
  <button 
    onClick={onChange}
    className={`w-11 h-6 rounded-full relative transition-colors duration-200 ${checked ? 'bg-[#8687E7]' : 'bg-[#444]'}`}
  >
    <div className={`absolute top-1 w-4 h-4 rounded-full bg-white shadow-sm transition-transform duration-200 ${checked ? 'left-6' : 'left-1'}`}></div>
  </button>
);
