import React, { useState, useEffect } from 'react';
import { DndProvider } from 'react-dnd';
import { HTML5Backend } from 'react-dnd-html5-backend';
import { Task, Tab, NotificationItem } from './types';
import { BottomNav } from './components/BottomNav';
import { HomeView } from './views/HomeView';
import { TodoView } from './views/TodoView';
import { TimeTableView } from './views/TimeTableView';
import { SettingsView } from './views/SettingsView';
import { LoginView } from './views/LoginView';
import { TaskEditModal } from './components/TaskEditModal';
import { AlarmModal } from './components/AlarmModal';
import { NotificationList } from './components/NotificationList';

const INITIAL_TASKS: Task[] = [
  { id: '1', content: '이메일 확인 및 정리', isBig3: false, scheduled: null, completed: false, recurrence: { type: 'daily' }, tags: ['Work'] },
  { id: '2', content: '주간 보고서 작성', isBig3: true, scheduled: null, completed: false, recurrence: { type: 'weekly', repeatDays: [5] }, tags: ['Work', 'Report'] },
  { id: '3', content: '디자인 팀 회의', isBig3: true, scheduled: null, completed: false, recurrence: null, tags: ['Meeting'] },
  { id: '4', content: '운동 (30분)', isBig3: false, scheduled: null, completed: false, recurrence: null, tags: ['Health'] },
  { id: '5', content: '프로젝트 기획안 마무리', isBig3: true, scheduled: { startHour: 10, duration: 90 }, completed: false, recurrence: null, tags: ['Project'] },
];

export default function App() {
  const [isAuthenticated, setIsAuthenticated] = useState(false);
  const [tasks, setTasks] = useState<Task[]>(INITIAL_TASKS);
  const [overdueTasks, setOverdueTasks] = useState<Task[]>([]);
  const [currentTab, setCurrentTab] = useState<Tab>('home');
  const [editingTask, setEditingTask] = useState<Task | null>(null);
  const [isTaskModalOpen, setIsTaskModalOpen] = useState(false);
  const [activeAlarmTask, setActiveAlarmTask] = useState<Task | null>(null);
  const [notifications, setNotifications] = useState<NotificationItem[]>([]);
  const [isNotificationListOpen, setIsNotificationListOpen] = useState(false);

  const addNotification = (title: string, message: string) => {
    setNotifications(prev => [{
      id: Date.now().toString(),
      title,
      message,
      timestamp: Date.now(),
      read: false,
      type: 'alarm'
    }, ...prev]);
  };

  const handleLogin = () => {
    setIsAuthenticated(true);
  };

  // Request Notification Permission
  useEffect(() => {
    if (isAuthenticated && 'Notification' in window) {
      if (Notification.permission === 'default') {
        Notification.requestPermission();
      }
    }
  }, [isAuthenticated]);

  // Alarm Check Logic
  useEffect(() => {
    if (!isAuthenticated) return;

    const checkAlarms = () => {
      const now = new Date();
      const currentH = now.getHours();
      const currentM = now.getMinutes();

      tasks.forEach(task => {
        // Skip if completed or no schedule or reminder disabled
        if (task.completed || !task.scheduled || !task.scheduled.reminder) return;

        // Calculate task time
        const taskH = Math.floor(task.scheduled.startHour);
        const taskM = Math.round((task.scheduled.startHour - taskH) * 60);

        // Check exact minute match
        if (taskH === currentH && taskM === currentM) {
          // Avoid re-triggering if already active for this task
          // Note: This simple check might re-trigger if user closes and it's still same minute.
          // For MVP it is fine, or we can use a 'lastAlerted' timestamp in task.
          if (activeAlarmTask?.id !== task.id) {
            setActiveAlarmTask(task);
            addNotification(`Task Due: ${task.content}`, task.note || 'Scheduled time reached.');
            
            if (Notification.permission === 'granted') {
              new Notification(`It's time: ${task.content}`, {
                body: task.note || 'Time to focus!',
                silent: false,
              });
            }
          }
        }
      });
    };

    // Check every 10 seconds to be safe (in case interval drift), but only trigger on minute change effectively
    // To be simpler, let's check every 30s
    const interval = setInterval(checkAlarms, 30000);
    
    // Also run once immediately
    checkAlarms();

    return () => clearInterval(interval);
  }, [tasks, isAuthenticated, activeAlarmTask]);

  // Daily Reset Logic
  useEffect(() => {
    if (!isAuthenticated) return;

    const lastDate = localStorage.getItem('lastActiveDate');
    const todayStr = new Date().toDateString();
    const todayDay = new Date().getDay();

    setTasks(currentTasks => {
      let nextTasks = [...currentTasks];
      let overdueToAdd: Task[] = [];
      const isDateChanged = lastDate && lastDate !== todayStr;

      // 1. If date changed: Reset recurring tasks and move incomplete tasks to overdue
      if (isDateChanged) {
        const resetTasks: Task[] = [];
        
        nextTasks.forEach(task => {
          if (task.recurrence) {
            // Keep recurring tasks but uncheck them
            resetTasks.push({ ...task, completed: false });
          } else {
            // One-time tasks
            if (!task.completed) {
              // Not completed -> Move to overdue
              overdueToAdd.push(task);
            }
            // Completed -> Removed from today's list
          }
        });
        nextTasks = resetTasks;
      }

      // 2. Consistency Check: Unset Big 3 for recurring tasks that are not for today
      // This runs on app start regardless of date change to ensure consistency
      let hasChanges = isDateChanged;
      
      const validatedTasks = nextTasks.map(task => {
        // Only check recurring tasks that are marked as Big 3
        if (!task.recurrence || !task.isBig3) return task;

        let isToday = false;
        
        if (typeof task.recurrence === 'string') {
           // Legacy support
           isToday = true; 
        } else {
           const { type, repeatDays } = task.recurrence;
           if (type === 'daily') isToday = true;
           else if (repeatDays && repeatDays.length > 0) {
             isToday = repeatDays.includes(todayDay);
           } else if (type === 'weekly') {
             // If weekly but no specific days, assume valid (or handle as needed)
             isToday = true; 
           }
        }

        if (!isToday) {
          hasChanges = true;
          return { ...task, isBig3: false };
        }
        return task;
      });

      // Handle side effects (Overdue tasks)
      if (overdueToAdd.length > 0) {
        setOverdueTasks(prev => [...prev, ...overdueToAdd]);
      }
      
      return hasChanges ? validatedTasks : currentTasks;
    });
    
    // Update last active date
    localStorage.setItem('lastActiveDate', todayStr);
  }, [isAuthenticated]);

  const moveOverdueToToday = () => {
    setTasks(prev => [...prev, ...overdueTasks]);
    setOverdueTasks([]);
  };

  const clearOverdueTasks = () => {
    setOverdueTasks([]);
  };

  const addTask = (content: string) => {
    const newTask: Task = {
      id: Date.now().toString(),
      content,
      isBig3: false,
      scheduled: null,
      completed: false,
    };
    setTasks([...tasks, newTask]);
  };

  const toggleBig3 = (id: string) => {
    setTasks(tasks.map(t => {
      if (t.id === id) {
        const big3Count = tasks.filter(task => task.isBig3 && task.id !== id).length;
        if (!t.isBig3 && big3Count >= 3) {
          alert('Big 3는 최대 3개까지만 선정할 수 있습니다.');
          return t;
        }
        return { ...t, isBig3: !t.isBig3 };
      }
      return t;
    }));
  };

  const toggleComplete = (id: string) => {
    setTasks(tasks.map(t => t.id === id ? { ...t, completed: !t.completed } : t));
  }
  
  const deleteTask = (id: string) => {
    if (confirm('정말 삭제하시겠습니까?')) {
      setTasks(tasks.filter(t => t.id !== id));
    }
  }

  const scheduleTask = (id: string, startHour: number, duration: number = 30) => {
    setTasks(tasks.map(t => t.id === id ? { ...t, scheduled: { startHour, duration } } : t));
  };

  const unscheduleTask = (id: string) => {
    setTasks(tasks.map(t => t.id === id ? { ...t, scheduled: null } : t));
  };

  const updateTaskDuration = (id: string, duration: number) => {
    setTasks(tasks.map(t => {
      if (t.id === id && t.scheduled) {
        return { ...t, scheduled: { ...t.scheduled, duration } };
      }
      return t;
    }));
  };

  const updateTaskStartTime = (id: string, startHour: number) => {
    setTasks(tasks.map(t => {
      if (t.id === id && t.scheduled) {
        return { ...t, scheduled: { ...t.scheduled, startHour } };
      }
      return t;
    }));
  };

  const updateTask = (id: string, updates: Partial<Task>) => {
    if (id === 'NEW_TASK' || id === 'DRAFT') {
      const newTask: Task = {
        id: Date.now().toString(),
        content: updates.content || 'New Task',
        isBig3: false,
        scheduled: updates.scheduled || null,
        completed: false,
        recurrence: updates.recurrence || null,
        note: updates.note,
        tags: updates.tags
      };
      setTasks([...tasks, newTask]);
      return;
    }

    setTasks(tasks.map(t => {
      if (t.id === id) {
        const updatedTask = { ...t, ...updates };
        
        // If recurrence is changing, check if it's still valid for today
        // If not, automatically uncheck Big 3
        if (updates.recurrence !== undefined && updatedTask.isBig3) {
           const todayDay = new Date().getDay();
           let isToday = false;

           if (!updatedTask.recurrence) {
             isToday = true;
           } else if (typeof updatedTask.recurrence === 'string') {
             isToday = true;
           } else {
             const { type, repeatDays } = updatedTask.recurrence;
             if (type === 'daily') isToday = true;
             else if (repeatDays && repeatDays.length > 0) {
               isToday = repeatDays.includes(todayDay);
             } else if (type === 'weekly') {
               isToday = true; // Fallback
             }
           }

           if (!isToday) {
             updatedTask.isBig3 = false;
           }
        }
        
        return updatedTask;
      }
      return t;
    }));
  };

  const moveTask = (dragId: string, hoverId: string) => {
    const dragIndex = tasks.findIndex(t => t.id === dragId);
    const hoverIndex = tasks.findIndex(t => t.id === hoverId);
    
    if (dragIndex === -1 || hoverIndex === -1 || dragIndex === hoverIndex) return;

    const newTasks = [...tasks];
    const [draggedItem] = newTasks.splice(dragIndex, 1);
    newTasks.splice(hoverIndex, 0, draggedItem);
    setTasks(newTasks);
  };

  const openEditModal = (task: Task) => {
    setEditingTask(task);
    setIsTaskModalOpen(true);
  };

  const openCreateModal = (initialContent?: string) => {
    if (initialContent) {
      const draftTask: Task = {
        id: 'DRAFT',
        content: initialContent,
        isBig3: false,
        scheduled: null,
        completed: false,
        recurrence: null
      };
      setEditingTask(draftTask);
    } else {
      setEditingTask(null);
    }
    setIsTaskModalOpen(true);
  };

  const handleTestAlarm = () => {
    setActiveAlarmTask({
      id: 'TEST_ALARM',
      content: 'Sample Task for Alarm',
      note: 'This is a test alarm to preview the notification screen.',
      isBig3: false,
      scheduled: { startHour: 10, duration: 30, reminder: true },
      completed: false,
      recurrence: null
    });
    addNotification('Test Alarm', 'This is a test notification generated from Settings.');
  };

  const renderView = () => {
    switch (currentTab) {
      case 'home':
        return (
          <HomeView
            tasks={tasks}
            onAddTask={addTask}
            onToggleBig3={toggleBig3}
            onToggleComplete={toggleComplete}
            onScheduleTask={scheduleTask}
            onUnscheduleTask={unscheduleTask}
            onUpdateDuration={updateTaskDuration}
            onUpdateTask={updateTask}
            onEditTask={openEditModal}
            onUpdateStartTime={updateTaskStartTime}
            onOpenNotifications={() => setIsNotificationListOpen(true)}
            unreadCount={notifications.filter(n => !n.read).length}
          />
        );
      case 'todo':
        return (
          <TodoView
            tasks={tasks}
            overdueTasks={overdueTasks}
            onMoveOverdueToToday={moveOverdueToToday}
            onClearOverdueTasks={clearOverdueTasks}
            onAddTask={addTask}
            onOpenAddModal={openCreateModal}
            onToggleBig3={toggleBig3}
            onToggleComplete={toggleComplete}
            onDeleteTask={deleteTask}
            onMoveTask={moveTask}
            onEditTask={openEditModal}
          />
        );
      case 'timetable':
        return (
          <TimeTableView
            tasks={tasks}
            onScheduleTask={scheduleTask}
            onUnscheduleTask={unscheduleTask}
            onUpdateDuration={updateTaskDuration}
            onEditTask={openEditModal}
            onUpdateStartTime={updateTaskStartTime}
          />
        );
      case 'settings':
        return <SettingsView onTestAlarm={handleTestAlarm} />;
      default:
        return null;
    }
  };

  if (!isAuthenticated) {
    return <LoginView onLogin={handleLogin} />;
  }

  return (
    <DndProvider backend={HTML5Backend}>
      <style>{`
        .no-scrollbar::-webkit-scrollbar {
          display: none;
        }
        .no-scrollbar {
          -ms-overflow-style: none;
          scrollbar-width: none;
        }
        /* Safe area handling for mobile */
        .safe-area-bottom {
          padding-bottom: env(safe-area-inset-bottom);
        }
      `}</style>
      <div className="h-screen bg-[#121212] flex flex-col font-sans text-white overflow-hidden select-none">
        
        <main className="flex-1 overflow-hidden relative">
          {renderView()}
        </main>

        <div className="landscape:hidden">
          <BottomNav currentTab={currentTab} onTabChange={setCurrentTab} />
        </div>

        <TaskEditModal 
          isOpen={isTaskModalOpen}
          task={editingTask}
          onClose={() => setIsTaskModalOpen(false)}
          onSave={updateTask}
        />
        
        <AlarmModal 
          task={activeAlarmTask}
          onClose={() => setActiveAlarmTask(null)}
          onComplete={() => {
            if (activeAlarmTask) {
              toggleComplete(activeAlarmTask.id);
              setActiveAlarmTask(null);
            }
          }}
          onSnooze={() => setActiveAlarmTask(null)}
        />
        
        <NotificationList
          isOpen={isNotificationListOpen}
          onClose={() => setIsNotificationListOpen(false)}
          notifications={notifications}
          onClearAll={() => setNotifications([])}
        />
      </div>
    </DndProvider>
  );
}
