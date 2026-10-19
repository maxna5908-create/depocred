/**
 * @license
 * SPDX-License-Identifier: Apache-2.0
 */

import React, { createContext, useContext, useEffect, useState } from 'react';
import { AppSkin, AppSettings } from '../types';

interface ThemeContextType {
  settings: AppSettings;
  setTheme: (theme: 'light' | 'dark' | 'system') => void;
  setSkin: (skin: AppSkin) => void;
  setShowHints: (show: boolean) => void;
}

const ThemeContext = createContext<ThemeContextType | undefined>(undefined);

export const ThemeProvider: React.FC<{ children: React.ReactNode }> = ({ children }) => {
  const [settings, setSettings] = useState<AppSettings>(() => {
    try {
      if (typeof window !== 'undefined' && window.localStorage) {
        const saved = localStorage.getItem('fincalc_settings');
        if (saved) return JSON.parse(saved);
      }
    } catch (e) {
      console.warn("Failed to parse theme settings from localStorage", e);
    }
    return { theme: 'light', skin: 'emerald', showHints: true };
  });

  useEffect(() => {
    try {
      if (typeof window !== 'undefined' && window.localStorage) {
        localStorage.setItem('fincalc_settings', JSON.stringify(settings));
      }
    } catch (e) {
      console.warn("Failed to save theme settings to localStorage", e);
    }
    
    // Apply theme
    const root = window.document.documentElement;
    let isDark = settings.theme === 'dark';
    
    if (settings.theme === 'system') {
      try {
        isDark = window.matchMedia && window.matchMedia('(prefers-color-scheme: dark)').matches;
      } catch (e) {
        console.warn("Failed to check preference for dark mode", e);
      }
    }
    
    if (isDark) {
      root.classList.add('dark');
      document.querySelector('meta[name="theme-color"]')?.setAttribute('content', '#374151');
    } else {
      root.classList.remove('dark');
      document.querySelector('meta[name="theme-color"]')?.setAttribute('content', '#f9fafb');
    }

    // Apply skin
    root.setAttribute('data-skin', settings.skin);
  }, [settings]);

  const setTheme = (theme: 'light' | 'dark' | 'system') => setSettings(s => ({ ...s, theme }));
  const setSkin = (skin: AppSkin) => setSettings(s => ({ ...s, skin }));
  const setShowHints = (showHints: boolean) => setSettings(s => ({ ...s, showHints }));

  return (
    <ThemeContext.Provider value={{ settings, setTheme, setSkin, setShowHints }}>
      {children}
    </ThemeContext.Provider>
  );
};

export const useTheme = () => {
  const context = useContext(ThemeContext);
  if (!context) throw new Error('useTheme must be used within ThemeProvider');
  return context;
};
