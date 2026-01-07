// src/AuthContext.jsx
import React, { createContext, useContext, useState, useEffect, useCallback } from 'react';

const AuthContext = createContext(null);

export const useAuth = () => {
    const context = useContext(AuthContext);
    if (!context) {
        throw new Error('useAuth must be used within an AuthProvider');
    }
    return context;
};

export const AuthProvider = ({ children }) => {
    const [user, setUser] = useState(null);
    const [isAuthenticated, setIsAuthenticated] = useState(false);
    const [isLoading, setIsLoading] = useState(true);
    const [error, setError] = useState(null);

    const checkAuthStatus = useCallback(async () => {
        try {
            const response = await fetch('/api/auth/status');
            const data = await response.json();

            if (data.authenticated) {
                // Fetch full user info if authenticated
                const userResponse = await fetch('/api/auth/user');
                if (userResponse.ok) {
                    const userData = await userResponse.json();
                    setUser(userData);
                    setIsAuthenticated(true);
                } else {
                    setIsAuthenticated(true);
                    setUser({ username: data.username });
                }
            } else {
                setIsAuthenticated(false);
                setUser(null);
            }
        } catch (err) {
            console.error('Error checking auth status:', err);
            setError(err.message);
            setIsAuthenticated(false);
            setUser(null);
        } finally {
            setIsLoading(false);
        }
    }, []);

    useEffect(() => {
        checkAuthStatus();
    }, [checkAuthStatus]);

    const login = useCallback(() => {
        // Redirect to OAuth2 login
        window.location.href = '/oauth2/authorization/frontend-app';
    }, []);

    const logout = useCallback(async () => {
        try {
            // Call backend logout endpoint
            window.location.href = '/logout';
        } catch (err) {
            console.error('Logout error:', err);
        }
    }, []);

    const value = {
        user,
        isAuthenticated,
        isLoading,
        error,
        login,
        logout,
        refreshAuth: checkAuthStatus
    };

    return (
        <AuthContext.Provider value={value}>
            {children}
        </AuthContext.Provider>
    );
};

export default AuthContext;
