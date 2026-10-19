/**
 * @license
 * SPDX-License-Identifier: Apache-2.0
 */

import React, { useState } from 'react';
import { HistoryItem, LoanInputs, LoanResults, DepositInputs, DepositResults, AdditionPeriod } from '../types';
import { formatCurrency, formatCurrencyCompact } from '../lib/calculators';
import { GlassCard } from './ui/Common';
import { motion, AnimatePresence } from 'motion/react';
import { Trash2, Clock, Share2, CreditCard, PiggyBank, Calendar, Bookmark } from 'lucide-react';
import { shareCalculationPDF } from '../lib/pdfGenerator';
import { useTheme } from './ThemeContext';
import { ScheduleModal } from './ScheduleModal';

export const History: React.FC<{ items: HistoryItem[], onDelete: (id: string) => void }> = ({ items, onDelete }) => {
  const { settings } = useTheme();
  const [filter, setFilter] = useState<'all' | 'loan' | 'deposit'>('all');
  const [selectedItem, setSelectedItem] = useState<HistoryItem | null>(null);

  const filteredItems = items
    .filter(item => filter === 'all' || item.type === filter)
    .sort((a, b) => b.timestamp - a.timestamp);

  const stats = {
    loan: items.filter(i => i.type === 'loan').length,
    deposit: items.filter(i => i.type === 'deposit').length,
  };

  const handleToggleFilter = (type: 'loan' | 'deposit') => {
    setFilter(prev => prev === type ? 'all' : type);
  };

  const handleShare = async (item: HistoryItem) => {
    try {
      const typeStr = item.type === 'loan' ? 'Loan' : 'Deposit';
      const dateStr = new Date(item.timestamp).toISOString().split('T')[0];
      const filename = `${typeStr}_History_${dateStr}`;
      await shareCalculationPDF(filename, item.type, item.inputs, item.results, settings.skin);
    } catch (err) {
      console.error('Share error:', err);
    }
  };

  const formatDate = (ts: number) => {
    const d = new Date(ts);
    const day = d.getDate().toString().padStart(2, '0');
    const month = d.toLocaleString('ru-RU', { month: 'short' }).replace('.', '');
    const year = d.getFullYear();
    const time = d.toLocaleTimeString('ru-RU', { hour: '2-digit', minute: '2-digit' });
    return `${day} ${month} ${year} - ${time}`;
  };

  return (
    <div className="flex flex-col gap-6 p-4 pb-32">
      <div className="grid grid-cols-2 p-1 glass rounded-xl">
        <button 
          onClick={() => handleToggleFilter('loan')}
          className={`py-3 rounded-lg transition-all font-semibold flex items-center justify-center gap-2 ${filter === 'loan' ? 'bg-skin-primary text-white shadow-sm px-4' : 'text-slate-500 dark:text-white hover:text-slate-900 dark:hover:text-white'}`}
        >
          Кредиты {stats.loan > 0 && <span className={`text-[10px] px-1.5 py-0.5 rounded-lg ${filter === 'loan' ? 'bg-black/10' : 'bg-skin-primary/10 text-skin-primary border border-skin-primary/20'}`}>{stats.loan}</span>}
        </button>
        <button 
          onClick={() => handleToggleFilter('deposit')}
          className={`py-3 rounded-lg transition-all font-semibold flex items-center justify-center gap-2 ${filter === 'deposit' ? 'bg-skin-primary text-white shadow-sm px-4' : 'text-slate-500 dark:text-white hover:text-slate-900 dark:hover:text-white'}`}
        >
          Депозиты {stats.deposit > 0 && <span className={`text-[10px] px-1.5 py-0.5 rounded-lg ${filter === 'deposit' ? 'bg-black/10' : 'bg-skin-primary/10 text-skin-primary border border-skin-primary/20'}`}>{stats.deposit}</span>}
        </button>
      </div>

      <div className="flex flex-col gap-4">
        <AnimatePresence mode="popLayout">
          {filteredItems.length === 0 ? (
            <motion.div 
              key="empty"
              initial={{ opacity: 0, y: 10 }}
              animate={{ opacity: 1, y: 0 }}
              exit={{ opacity: 0, scale: 0.95 }}
              className="flex flex-col items-center justify-center py-20 gap-4"
            >
              <div className="w-20 h-20 rounded-full bg-slate-100 dark:bg-white/5 flex items-center justify-center">
                <Bookmark size={40} className="text-slate-300 dark:text-slate-600" />
              </div>
              <p className="text-sm font-bold text-slate-400 dark:text-slate-500 tracking-wide text-center">
                История расчетов пуста
              </p>
            </motion.div>
          ) : (
            filteredItems.map(item => (
              <motion.div
                key={item.id}
                layout
                initial={{ opacity: 0, scale: 0.9, y: 20 }}
                animate={{ opacity: 1, scale: 1, y: 0 }}
                exit={{ opacity: 0, scale: 0.9, y: -20 }}
                className="w-full relative"
              >
                {item.type === 'loan' ? (
                  <GlassCard className="!p-5 border-l-4 overflow-hidden relative group" style={{ borderLeftColor: '#f43f5e' }}>
                    <div className="flex justify-between items-center mb-1">
                      <div className="flex items-center gap-2">
                        <span className="text-[10px] text-slate-400 dark:text-slate-500 uppercase tracking-widest flex items-center gap-1 font-semibold">
                          <Calendar size={10} />
                          {formatDate(item.timestamp)}
                        </span>
                      </div>
                      <div className="bg-[#f43f5e] text-white text-[10px] px-2 py-0.5 rounded-full font-bold uppercase tracking-wider h-fit">
                        Кредит
                      </div>
                    </div>

                    <div className="flex flex-col mt-2">
                      <span className="text-[11px] text-slate-400 font-medium uppercase tracking-wider">Итоговая сумма</span>
                      <div className="text-3xl font-black text-slate-800 dark:text-white mt-1 mb-4 tracking-tight">
                        {formatCurrency((item.results as LoanResults).totalRepayment || ((item.inputs as LoanInputs).amount + (item.results as LoanResults).totalInterest))}
                      </div>
                    </div>

                    <div className="h-px bg-rose-500/20 w-full mb-4"></div>

                    <div className="grid grid-cols-2 gap-y-4 gap-x-6 mb-4">
                      <div className="flex flex-col">
                        <span className="text-[10px] text-slate-400 font-bold uppercase tracking-widest">Кредит</span>
                        <span className="text-sm font-bold text-slate-800 dark:text-white mt-0.5">
                          {formatCurrency((item.inputs as LoanInputs).amount)}
                        </span>
                      </div>
                      <div className="flex flex-col">
                        <span className="text-[10px] text-slate-400 font-bold uppercase tracking-widest">Ставка / Срок</span>
                        <span className="text-sm font-bold text-slate-800 dark:text-white mt-0.5">
                          {(item.inputs as LoanInputs).rate.toFixed(2)}% | {(item.inputs as LoanInputs).term} мес.
                        </span>
                      </div>
                      <div className="flex flex-col">
                        <span className="text-[10px] text-slate-400 font-bold uppercase tracking-widest">{Array.isArray((item.results as LoanResults).monthlyPayment) ? 'Первый платёж' : 'Платёж'}</span>
                        <span className="text-sm font-bold text-slate-800 dark:text-white mt-0.5">
                          {formatCurrency(Array.isArray((item.results as LoanResults).monthlyPayment) ? (item.results as LoanResults).monthlyPayment[0] : (item.results as LoanResults).monthlyPayment)}
                          {Array.isArray((item.results as LoanResults).monthlyPayment) ? ' ...' : ''}
                        </span>
                      </div>
                      <div className="flex flex-col">
                        <span className="text-[10px] text-slate-400 font-bold uppercase tracking-widest">Переплата</span>
                        <span className="text-sm font-bold text-rose-500 mt-0.5">
                          {formatCurrency((item.results as LoanResults).totalInterest)}
                        </span>
                      </div>
                    </div>

                    <div className="h-px bg-rose-500/20 w-full mb-4"></div>

                    <div className="mb-4">
                      <p className="text-[11px] italic text-rose-500 leading-snug">
                        Переплата по кредиту составит {(((item.results as LoanResults).totalInterest / (item.inputs as LoanInputs).amount) * 100).toFixed(2)}% от первоначальной суммы.
                      </p>
                    </div>

                    <div className="flex items-center gap-3">
                      <button 
                        onClick={() => setSelectedItem(item)}
                        className="flex-1 h-14 rounded-[18px] border-2 border-slate-200 dark:border-white/10 flex items-center justify-center text-slate-400 text-sm font-bold hover:text-skin-primary hover:border-skin-primary transition-all active:scale-[0.98]"
                      >
                        График платежей
                      </button>
                      <button 
                        onClick={() => handleShare(item)}
                        className="w-14 h-14 rounded-[18px] border-2 border-slate-200 dark:border-white/10 flex items-center justify-center text-slate-400 transition-all active:scale-[0.98] hover:border-skin-primary hover:text-skin-primary"
                      >
                        <Share2 size={24} />
                      </button>
                      <button 
                        onClick={() => onDelete(item.id)}
                        className="w-14 h-14 rounded-[18px] border-2 border-slate-200 dark:border-white/10 flex items-center justify-center text-slate-400 hover:text-rose-500 hover:border-rose-500 transition-all active:scale-[0.98]"
                      >
                        <Trash2 size={24} />
                      </button>
                    </div>
                  </GlassCard>
                ) : (
                  <GlassCard className="!p-5 border-l-4 overflow-hidden relative group" style={{ borderLeftColor: '#10b981' }}>
                    <div className="flex justify-between items-center mb-1">
                      <div className="flex items-center gap-2">
                        <span className="text-[10px] text-slate-400 dark:text-slate-500 uppercase tracking-widest flex items-center gap-1 font-semibold">
                          <Calendar size={10} />
                          {formatDate(item.timestamp)}
                        </span>
                      </div>
                      <div className="bg-[#10b981] text-white text-[10px] px-2 py-0.5 rounded-full font-bold uppercase tracking-wider h-fit">
                        Депозит
                      </div>
                    </div>

                    <div className="flex flex-col mt-2">
                      <span className="text-[11px] text-slate-400 font-medium uppercase tracking-wider">Итоговая сумма</span>
                      <div className="text-3xl font-black text-slate-800 dark:text-white mt-1 mb-4 tracking-tight">
                        {formatCurrency((item.results as DepositResults).finalAmount)}
                      </div>
                    </div>

                    <div className="h-px bg-emerald-500/20 w-full mb-4"></div>

                    <div className="grid grid-cols-2 gap-y-4 gap-x-6 mb-4">
                      <div className="flex flex-col">
                        <span className="text-[10px] text-slate-400 font-bold uppercase tracking-widest">Депозит</span>
                        <span className="text-sm font-bold text-slate-800 dark:text-white mt-0.5">
                          {formatCurrency((item.inputs as DepositInputs).initialAmount)}
                        </span>
                      </div>
                      <div className="flex flex-col">
                        <span className="text-[10px] text-slate-400 font-bold uppercase tracking-widest">Ставка / Срок</span>
                        <span className="text-sm font-bold text-slate-800 dark:text-white mt-0.5">
                          {(item.inputs as DepositInputs).rate.toFixed(2)}% | {(item.inputs as DepositInputs).term} мес.
                        </span>
                      </div>
                      <div className="flex flex-col">
                        <span className="text-[10px] text-slate-400 font-bold uppercase tracking-widest">Пополнения</span>
                        <span className="text-sm font-bold text-slate-800 dark:text-white mt-0.5">
                          {(item.results as DepositResults).totalAdditions > 0 ? (
                            formatCurrency((item.results as DepositResults).totalAdditions)
                          ) : '-'}
                        </span>
                      </div>
                      <div className="flex flex-col">
                        <span className="text-[10px] text-slate-400 font-bold uppercase tracking-widest">Доход</span>
                        <span className="text-sm font-bold text-emerald-500 mt-0.5">
                          {formatCurrency((item.results as DepositResults).totalInterest)}
                        </span>
                      </div>
                    </div>

                    <div className="h-px bg-emerald-500/20 w-full mb-4"></div>

                    <div className="mb-4">
                      <p className="text-[11px] italic text-emerald-500 leading-snug">
                        Доходность с учетом сложного процента составит {(((item.results as DepositResults).totalInterest / ((item.inputs as DepositInputs).initialAmount + (item.results as DepositResults).totalAdditions)) * 100).toFixed(2)}% за весь период.
                      </p>
                    </div>

                    <div className="flex items-center gap-3">
                      <button 
                        onClick={() => setSelectedItem(item)}
                        className="flex-1 h-14 rounded-[18px] border-2 border-slate-200 dark:border-white/10 flex items-center justify-center text-slate-400 text-sm font-bold hover:text-skin-primary hover:border-skin-primary transition-all active:scale-[0.98]"
                      >
                        График начислений
                      </button>
                      <button 
                        onClick={() => handleShare(item)}
                        className="w-14 h-14 rounded-[18px] border-2 border-slate-200 dark:border-white/10 flex items-center justify-center text-slate-400 transition-all active:scale-[0.98] hover:border-skin-primary hover:text-skin-primary"
                      >
                        <Share2 size={24} />
                      </button>
                      <button 
                        onClick={() => onDelete(item.id)}
                        className="w-14 h-14 rounded-[18px] border-2 border-slate-200 dark:border-white/10 flex items-center justify-center text-slate-400 hover:text-rose-500 hover:border-rose-500 transition-all active:scale-[0.98]"
                      >
                        <Trash2 size={24} />
                      </button>
                    </div>
                  </GlassCard>
                )}
              </motion.div>
            ))
          )}
        </AnimatePresence>
      </div>

      <ScheduleModal 
        isOpen={!!selectedItem}
        onClose={() => setSelectedItem(null)}
        title={selectedItem?.type === 'loan' ? "График платежей" : "График начислений"}
        type={selectedItem?.type || 'loan'}
        data={selectedItem?.results.schedule || []}
        inputs={selectedItem?.inputs}
        results={selectedItem?.results}
      />
    </div>
  );
};
