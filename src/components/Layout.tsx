/**
 * @license
 * SPDX-License-Identifier: Apache-2.0
 */

import React, { useState, useEffect, useRef } from 'react';
import { motion, AnimatePresence } from 'motion/react';
import { CreditCard, PiggyBank, Bookmark, Info as InfoIcon, Coins } from 'lucide-react';
import { LoanCalculator } from './LoanCalculator';
import { DepositCalculator } from './DepositCalculator';
import { History } from './History';
import { Info } from './Info';
import { GlassCard } from './ui/Common';
import { HistoryItem, LoanInputs, LoanResults, DepositInputs, DepositResults } from '../types';
import { useTheme } from './ThemeContext';

export const Layout: React.FC = () => {
  const { settings } = useTheme();
  const initialLoanInputs: LoanInputs = { amount: 0, rate: 0, term: 0, fixedCommission: 0, percentCommission: 0, paymentType: 'annuity' };
  const initialDepositInputs: DepositInputs = { initialAmount: 0, monthlyAddition: 0, rate: 0, term: 0, capitalization: 'monthly', additionPeriod: 'none' };

  const [loanSession, setLoanSession] = useState<{
    directInputs: LoanInputs,
    reverseInputs: LoanInputs,
    results: LoanResults | null,
    calculationMode: 'direct' | 'reverse'
  }>({
    directInputs: initialLoanInputs,
    reverseInputs: { ...initialLoanInputs, paymentType: null, reversePayment: 0, reverseTotalRepayment: 0 },
    results: null,
    calculationMode: 'direct'
  });
  const [depositSession, setDepositSession] = useState<{
    directInputs: DepositInputs,
    reverseInputs: DepositInputs,
    results: DepositResults | null,
    calculationMode: 'direct' | 'reverse'
  }>({
    directInputs: initialDepositInputs,
    reverseInputs: { ...initialDepositInputs, reverseFinalAmount: 0 },
    results: null,
    calculationMode: 'direct'
  });

  const [activeTab, setActiveTab] = useState<'loan' | 'deposit' | 'history' | 'info'>('loan');
  const mainContentRef = useRef<HTMLDivElement>(null);
  const layoutRef = useRef<HTMLDivElement>(null);
  const heightRef = useRef(0);

  const [showSplash, setShowSplash] = useState(true);

  useEffect(() => {
    const updateDimensions = (force = false) => {
      const h = window.innerHeight;
      if (h <= 0) return;

      const activeElement = document.activeElement;
      const isInputFocused = activeElement && (activeElement.tagName === 'INPUT' || activeElement.tagName === 'TEXTAREA');

      // If an input is focused and height shrinks, it's the virtual keyboard opening.
      // We ignore the shrink so the layout stays full height and bottom menu stays hidden beneath keyboard instead of riding up.
      if (!force && isInputFocused && h < heightRef.current) {
        return;
      }

      heightRef.current = h;
      if (layoutRef.current) {
        layoutRef.current.style.height = `${h}px`;
      }
    };

    updateDimensions(true);
    // Safety updates for slower webviews initializing window bounds
    const t1 = setTimeout(() => updateDimensions(true), 200);
    const t2 = setTimeout(() => updateDimensions(true), 800);

    const handleResize = () => updateDimensions(false);
    const handleOrientation = () => setTimeout(() => updateDimensions(true), 300);

    window.addEventListener('resize', handleResize);
    window.addEventListener('orientationchange', handleOrientation);
    
    return () => {
      window.removeEventListener('resize', handleResize);
      window.removeEventListener('orientationchange', handleOrientation);
      clearTimeout(t1);
      clearTimeout(t2);
    };
  }, []);

  const validateHistoryItem = (item: any): boolean => {
    try {
      if (!item || typeof item !== 'object') return false;
      const required = ['id', 'type', 'timestamp', 'inputs', 'results', 'label'];
      if (!required.every(key => key in item)) return false;
      if (!['loan', 'deposit'].includes(item.type)) return false;
      
      const inputs = item.inputs;
      const results = item.results;
      
      if (item.type === 'loan') {
        if (typeof inputs.amount !== 'number' || typeof inputs.rate !== 'number' || typeof inputs.term !== 'number') return false;
        if (!results || typeof results.totalInterest !== 'number' || !Array.isArray(results.schedule)) return false;
      } else {
        if (typeof inputs.initialAmount !== 'number' || typeof inputs.rate !== 'number' || typeof inputs.term !== 'number') return false;
        if (!results || typeof results.totalInterest !== 'number' || !Array.isArray(results.schedule)) return false;
      }
      return true;
    } catch (e) {
      return false;
    }
  };

  const [history, setHistory] = useState<HistoryItem[]>(() => {
    try {
      if (typeof window !== 'undefined' && window.localStorage) {
        const saved = localStorage.getItem('fincalc_history');
        if (!saved) return [];
        const parsed = JSON.parse(saved);
        if (Array.isArray(parsed)) {
          const validItems = parsed.filter(validateHistoryItem);
          // If some items were filtered out, update localStorage
          if (validItems.length !== parsed.length) {
            localStorage.setItem('fincalc_history', JSON.stringify(validItems));
          }
          return validItems;
        }
      }
    } catch (e) {
      console.warn("Failed to parse history from localStorage", e);
    }
    return [];
  });

  useEffect(() => {
    const timer = setTimeout(() => setShowSplash(false), 2000);
    return () => clearTimeout(timer);
  }, []);

  useEffect(() => {
    try {
      if (typeof window !== 'undefined' && window.localStorage) {
        localStorage.setItem('fincalc_history', JSON.stringify(history));
      }
    } catch (e) {
      console.warn("Failed to save history to localStorage", e);
    }
  }, [history]);

  useEffect(() => {
    if (mainContentRef.current) {
      mainContentRef.current.scrollTo({ top: 0, behavior: 'instant' });
    }
  }, [activeTab]);

  const saveToHistory = (item: HistoryItem) => {
    setHistory(prev => [item, ...prev].slice(0, 10));
  };

  const deleteFromHistory = (id: string) => {
    setHistory(prev => prev.filter(i => i.id !== id));
  };

  const tabs = [
    { id: 'loan', label: 'Кредит', icon: CreditCard },
    { id: 'deposit', label: 'Депозит', icon: PiggyBank },
    { id: 'history', label: 'История', icon: Bookmark },
    { id: 'info', label: 'Инфо', icon: InfoIcon },
  ];

  return (
    <div 
      ref={layoutRef}
      className="fixed inset-0 h-full bg-[radial-gradient(circle_at_top_left,#f9fafb,#e5e7eb)] dark:bg-[radial-gradient(circle_at_top_left,#374151,#111827)] flex flex-col items-center overflow-hidden"
    >
      <AnimatePresence>
        {showSplash && (
          <motion.div
            key="splash"
            initial={{ opacity: 1 }}
            exit={{ opacity: 0 }}
            className="absolute inset-0 z-[100] bg-skin-primary flex flex-col items-center justify-center font-sans"
          >
            <motion.div
              initial={{ scale: 0.5, opacity: 0 }}
              animate={{ scale: 1, opacity: 1 }}
              transition={{ duration: 0.5, type: 'spring' }}
              className="relative"
            >
              <Coins className="text-white w-24 h-24" />
            </motion.div>
            <motion.h1 
              initial={{ y: 20, opacity: 0 }}
              animate={{ y: 0, opacity: 1 }}
              transition={{ delay: 0.3 }}
              className="text-white text-3xl font-black mt-8 tracking-tighter"
            >
              DepoCred
            </motion.h1>
            <motion.p
              initial={{ y: 20, opacity: 0 }}
              animate={{ y: 0, opacity: 1 }}
              transition={{ delay: 0.4 }}
              className="text-white/80 text-sm font-medium mt-2 tracking-wide uppercase"
            >
              Ваш финансовый ассистент
            </motion.p>
            <motion.div 
              initial={{ opacity: 0 }}
              animate={{ opacity: 1 }}
              transition={{ delay: 1, duration: 1 }}
              className="absolute bottom-12"
            >
              <div className="w-12 h-1 bg-white/30 rounded-lg overflow-hidden">
                <motion.div 
                  initial={{ x: '-100%' }}
                  animate={{ x: '100%' }}
                  transition={{ repeat: Infinity, duration: 1.5, ease: 'linear' }}
                  className="w-full h-full bg-white"
                />
              </div>
            </motion.div>
          </motion.div>
        )}
      </AnimatePresence>

      <main className="w-full max-w-lg md:max-w-2xl lg:max-w-[800px] h-full flex flex-col overflow-hidden relative">
        <header 
          className="flex-shrink-0 z-40 px-5 pb-4 flex justify-between items-center"
          style={{ paddingTop: 'calc(env(safe-area-inset-top, 0px) + 24px)' }}
        >
           <div className="flex flex-col">
            <div className="flex flex-col w-fit mb-1.5">
              <span className="text-[11px] text-slate-500 dark:text-slate-300 font-medium uppercase tracking-widest leading-none">Ваш финансовый ассистент</span>
              <div className="h-[1.5px] w-full bg-skin-primary rounded-full mt-1" />
            </div>
             <h1 className="text-xl font-bold text-skin-primary tracking-tight leading-tight">
              {activeTab === 'loan' && "Кредитный калькулятор"}
              {activeTab === 'deposit' && "Депозитный калькулятор"}
              {activeTab === 'history' && "Сохраненные расчеты"}
              {activeTab === 'info' && "О приложении"}
             </h1>
           </div>
           <div className="text-skin-primary ml-4">
              <Coins size={32} strokeWidth={1.5} />
           </div>
        </header>

        <div ref={mainContentRef} className="flex-1 overflow-y-auto overflow-x-hidden px-1 pb-40">
          <AnimatePresence>
            <motion.div
              key={activeTab}
              initial={{ x: 20, opacity: 0 }}
              animate={{ x: 0, opacity: 1 }}
              exit={{ opacity: 0, transition: { duration: 0 } }}
              transition={{ 
                type: 'spring', 
                damping: 25, 
                stiffness: 200
              }}
            >
              {activeTab === 'loan' && (
                <LoanCalculator 
                  onSave={saveToHistory} 
                  session={loanSession}
                  onSessionChange={setLoanSession}
                />
              )}
              {activeTab === 'deposit' && (
                <DepositCalculator 
                  onSave={saveToHistory} 
                  session={depositSession}
                  onSessionChange={setDepositSession}
                />
              )}
              {activeTab === 'history' && <History items={history} onDelete={deleteFromHistory} />}
              {activeTab === 'info' && <Info />}
            </motion.div>
          </AnimatePresence>
        </div>
      </main>
      
      <nav 
        className="absolute left-6 right-6 max-w-lg md:max-w-2xl lg:max-w-[800px] mx-auto z-50 pointer-events-none pb-[env(safe-area-inset-bottom,0px)]"
        style={{ bottom: '1.25rem' }}
      >
        <div className="h-20 glass-menu rounded-[20px] flex items-center justify-around px-2 shadow-2xl pointer-events-auto border border-white/20 dark:border-white/10">
          {tabs.map((tab) => {
            const Icon = tab.icon;
            const isActive = activeTab === tab.id;
            
            return (
              <button
                key={tab.id}
                onClick={() => setActiveTab(tab.id as any)}
                className={`
                  relative flex flex-col items-center justify-center gap-1.5 py-2 px-1 flex-1 transition-colors h-full
                  ${isActive ? 'text-skin-primary' : 'text-slate-900 dark:text-white opacity-40 hover:opacity-70'}
                `}
              >
                <div className={`p-2 rounded-lg transition-colors ${isActive ? 'bg-skin-primary/10 border border-skin-primary/30' : ''}`}>
                  <Icon size={20} strokeWidth={isActive ? 2.5 : 2} />
                </div>
                <span className={`text-[10px] font-bold uppercase tracking-wider`}>
                  {tab.label}
                </span>
              </button>
            );
          })}
        </div>
      </nav>
    </div>
  );
};
