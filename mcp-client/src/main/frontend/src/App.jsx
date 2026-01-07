// src/App.jsx
import React, { useState, useEffect } from 'react';
import ChatPage from './pages/ChatPage';
import ThemeToggle from './components/ui/ThemeToggle';
import { AuthProvider, useAuth } from './AuthContext';

const LoginButton = () => {
    const { login } = useAuth();

    return (
        <button
            onClick={login}
            className="px-4 py-2 bg-blue-600 text-white rounded-lg hover:bg-blue-700 transition-colors"
        >
            Sign In
        </button>
    );
};

const UserMenu = ({ isDarkMode }) => {
    const { user, logout } = useAuth();
    const [isOpen, setIsOpen] = useState(false);

    return (
        <div className="relative">
            <button
                onClick={() => setIsOpen(!isOpen)}
                className={`flex items-center space-x-2 px-3 py-2 rounded-lg transition-colors ${
                    isDarkMode ? 'hover:bg-gray-700' : 'hover:bg-gray-100'
                }`}
            >
                <div className={`w-8 h-8 rounded-full flex items-center justify-center text-white text-sm font-medium ${
                    isDarkMode ? 'bg-blue-600' : 'bg-blue-500'
                }`}>
                    {user?.username?.charAt(0)?.toUpperCase() || 'U'}
                </div>
                <span className="text-sm font-medium">{user?.username || 'User'}</span>
                <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                    <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M19 9l-7 7-7-7" />
                </svg>
            </button>

            {isOpen && (
                <div className={`absolute right-0 mt-2 w-48 rounded-lg shadow-lg py-1 z-50 ${
                    isDarkMode ? 'bg-gray-800 border border-gray-700' : 'bg-white border border-gray-200'
                }`}>
                    <div className={`px-4 py-2 text-sm border-b ${
                        isDarkMode ? 'border-gray-700 text-gray-300' : 'border-gray-100 text-gray-600'
                    }`}>
                        <div className="font-medium">{user?.name || user?.username}</div>
                        <div className="text-xs opacity-75">{user?.email}</div>
                        {user?.roles && user.roles.length > 0 && (
                            <div className="text-xs mt-1 opacity-60">
                                {user.roles.map(role => role.replace('ROLE_', '')).join(', ')}
                            </div>
                        )}
                    </div>
                    <button
                        onClick={logout}
                        className={`w-full text-left px-4 py-2 text-sm transition-colors ${
                            isDarkMode ? 'hover:bg-gray-700 text-red-400' : 'hover:bg-gray-50 text-red-600'
                        }`}
                    >
                        Sign Out
                    </button>
                </div>
            )}
        </div>
    );
};

const LoadingSpinner = () => (
    <div className="min-h-screen flex items-center justify-center bg-gray-100 dark:bg-gray-900">
        <div className="text-center">
            <div className="animate-spin rounded-full h-12 w-12 border-b-2 border-blue-600 mx-auto"></div>
            <p className="mt-4 text-gray-600 dark:text-gray-400">Loading...</p>
        </div>
    </div>
);

const LoginPage = ({ isDarkMode }) => {
    const { login } = useAuth();

    return (
        <div className={`min-h-screen flex items-center justify-center ${
            isDarkMode ? 'bg-gray-900' : 'bg-gradient-to-br from-blue-50 to-indigo-100'
        }`}>
            <div className={`max-w-md w-full mx-4 p-8 rounded-xl shadow-xl ${
                isDarkMode ? 'bg-gray-800' : 'bg-white'
            }`}>
                <div className="text-center">
                    <h1 className={`text-3xl font-bold mb-2 ${
                        isDarkMode ? 'text-white' : 'text-gray-900'
                    }`}>
                        Spring AI ResOS
                    </h1>
                    <p className={`mb-8 ${isDarkMode ? 'text-gray-400' : 'text-gray-600'}`}>
                        Restaurant Reservation AI Assistant
                    </p>
                    <button
                        onClick={login}
                        className="w-full py-3 px-4 bg-gradient-to-r from-blue-600 to-indigo-600 text-white rounded-lg font-semibold hover:from-blue-700 hover:to-indigo-700 transition-all shadow-lg hover:shadow-xl"
                    >
                        Sign In to Continue
                    </button>
                    <p className={`mt-4 text-sm ${isDarkMode ? 'text-gray-500' : 'text-gray-500'}`}>
                        You'll be redirected to the authentication server
                    </p>
                </div>
            </div>
        </div>
    );
};

const AppContent = () => {
    const { isAuthenticated, isLoading } = useAuth();
    const [isDarkMode, setIsDarkMode] = useState(
        localStorage.getItem('theme') === 'dark' ||
        window.matchMedia('(prefers-color-scheme: dark)').matches
    );

    useEffect(() => {
        if (isDarkMode) {
            document.documentElement.classList.add('dark');
            localStorage.setItem('theme', 'dark');
        } else {
            document.documentElement.classList.remove('dark');
            localStorage.setItem('theme', 'light');
        }
    }, [isDarkMode]);

    const toggleTheme = () => {
        setIsDarkMode(!isDarkMode);
    };

    if (isLoading) {
        return <LoadingSpinner />;
    }

    if (!isAuthenticated) {
        return <LoginPage isDarkMode={isDarkMode} />;
    }

    return (
        <div className={`min-h-screen transition-colors duration-200 ${
            isDarkMode ? 'dark bg-gray-900 text-white' : 'bg-white text-gray-900'
        }`}>
            <header className="px-6 py-4 border-b">
                <div className="max-w-4xl mx-auto flex justify-between items-center">
                    <h1 className="text-xl font-semibold">ResOS Frontend</h1>
                    <div className="flex items-center space-x-4">
                        <ThemeToggle isDarkMode={isDarkMode} toggleTheme={toggleTheme} />
                        <UserMenu isDarkMode={isDarkMode} />
                    </div>
                </div>
            </header>
            <main>
                <ChatPage isDarkMode={isDarkMode} />
            </main>
        </div>
    );
};

const App = () => {
    return (
        <AuthProvider>
            <AppContent />
        </AuthProvider>
    );
};

export default App;
