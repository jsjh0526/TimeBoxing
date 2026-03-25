import React from 'react';

export const TimeAxis: React.FC = () => {
  const hours = Array.from({ length: 24 }, (_, i) => i); // 00:00 to 23:00

  return (
    <div className="w-16 flex-shrink-0 flex flex-col pt-6 select-none bg-[#1E1E1E] text-gray-500">
      {hours.map(hour => (
        <div key={hour} className="h-16 relative border-r border-[#333]">
          <span className="absolute -top-3 right-4 text-xs font-medium font-mono">
            {hour.toString().padStart(2, '0')}:00
          </span>
          {/* Half hour marker */}
          <div className="absolute top-1/2 right-0 w-2 border-t border-[#333]"></div>
        </div>
      ))}
      {/* End marker */}
      <div className="relative border-r border-[#333]">
         <span className="absolute -top-3 right-4 text-xs font-medium font-mono">
            24:00
          </span>
      </div>
    </div>
  );
};
