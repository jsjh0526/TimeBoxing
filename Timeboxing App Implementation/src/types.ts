export interface ScheduledTime {
  startHour: number; // 0-24 float
  duration: number; // minutes
  reminder?: boolean; // Alarm enabled
}

export interface RecurrenceConfig {
  type: 'daily' | 'weekly' | 'weekdays' | 'custom';
  repeatDays?: number[]; // 0(Sun) - 6(Sat)
}

export interface Task {
  id: string;
  content: string;
  note?: string;
  tags?: string[];
  isBig3: boolean;
  scheduled: ScheduledTime | null;
  completed: boolean;
  recurrence: RecurrenceConfig | null; // Changed from simple string
}

export interface NotificationItem {
  id: string;
  title: string;
  message: string;
  timestamp: number; // unix timestamp
  read: boolean;
  type: 'alarm' | 'info';
}

export type Tab = 'home' | 'todo' | 'timetable' | 'settings';

export const ItemTypes = {
  TASK: 'task',
};
