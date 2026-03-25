import React from 'react';
import { ImageWithFallback } from '../components/figma/ImageWithFallback';

interface LoginViewProps {
  onLogin: () => void;
}

export const LoginView: React.FC<LoginViewProps> = ({ onLogin }) => {
  return (
    <div className="flex flex-col h-screen bg-[#121212] items-center justify-center p-6 relative overflow-hidden">
      
      {/* Background/Startup Image */}
      <div className="absolute inset-0 z-0 opacity-40">
         <ImageWithFallback 
           src="https://images.unsplash.com/photo-1652074847108-0b4294408ca1?crop=entropy&cs=tinysrgb&fit=max&fm=jpg&ixid=M3w3Nzg4Nzd8MHwxfHNlYXJjaHwxfHxkYXJrJTIwbWluaW1hbGlzdCUyMGdlb21ldHJpY3xlbnwxfHx8fDE3NzAxMjk3NTJ8MA&ixlib=rb-4.1.0&q=80&w=1080&utm_source=figma&utm_medium=referral"
           alt="Background"
           className="w-full h-full object-cover"
         />
         <div className="absolute inset-0 bg-gradient-to-t from-[#121212] via-[#121212]/80 to-transparent" />
      </div>

      <div className="z-10 w-full max-w-sm flex flex-col items-center animate-in fade-in slide-in-from-bottom-8 duration-700">
        {/* App Logo/Title */}
        <div className="mb-12 text-center">
          <div className="w-16 h-16 bg-[#8687E7] rounded-2xl flex items-center justify-center mx-auto mb-6 shadow-lg shadow-[#8687E7]/30">
            <svg width="32" height="32" viewBox="0 0 24 24" fill="none" stroke="white" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round">
              <path d="M12 2v20M2 12h20" />
            </svg>
          </div>
          <h1 className="text-3xl font-bold text-white mb-2 tracking-tight">Timebox</h1>
          <p className="text-gray-400 text-sm">Focus on what matters most.</p>
        </div>

        {/* Login Button */}
        <button 
          onClick={onLogin}
          className="w-full bg-white text-black font-semibold py-3.5 px-4 rounded-xl flex items-center justify-center gap-3 hover:bg-gray-100 transition-colors shadow-lg active:scale-95 duration-200"
        >
          <svg width="20" height="20" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
            <path d="M22.56 12.25c0-.78-.07-1.53-.2-2.25H12v4.26h5.92c-.26 1.37-1.04 2.53-2.21 3.31v2.77h3.57c2.08-1.92 3.28-4.74 3.28-8.09z" fill="#4285F4"/>
            <path d="M12 23c2.97 0 5.46-.98 7.28-2.66l-3.57-2.77c-.98.66-2.23 1.06-3.71 1.06-2.86 0-5.29-1.93-6.16-4.53H2.18v2.84C3.99 20.53 7.7 23 12 23z" fill="#34A853"/>
            <path d="M5.84 14.09c-.22-.66-.35-1.36-.35-2.09s.13-1.43.35-2.09V7.07H2.18C1.43 8.55 1 10.22 1 12s.43 3.45 1.18 4.93l2.85-2.22.81-.62z" fill="#FBBC05"/>
            <path d="M12 5.38c1.62 0 3.06.56 4.21 1.64l3.15-3.15C17.45 2.09 14.97 1 12 1 7.7 1 3.99 3.47 2.18 7.07l3.66 2.84c.87-2.6 3.3-4.53 6.16-4.53z" fill="#EA4335"/>
          </svg>
          Continue with Google
        </button>

        <p className="mt-8 text-xs text-center text-gray-600">
          By continuing, you agree to our Terms & Conditions.
        </p>
      </div>
    </div>
  );
};
