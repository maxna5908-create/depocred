/**
 * @license
 * SPDX-License-Identifier: Apache-2.0
 */

import React from 'react';
import { ChevronLeft, ArrowLeft, Share2 } from 'lucide-react';
import { LoanScheduleItem, DepositScheduleItem, LoanInputs, LoanResults, DepositInputs, DepositResults } from '../types';
import { formatCurrencyCompact } from '../lib/calculators';
import { Button, Toast } from './ui/Common';
import { shareCalculationPDF } from '../lib/pdfGenerator';

import { useTheme } from './ThemeContext';

interface ScheduleModalProps {
  isOpen: boolean;
  onClose: () => void;
  title: string;
  type: 'loan' | 'deposit';
  data: LoanScheduleItem[] | DepositScheduleItem[];
  inputs?: any;
  results?: any;
  calculationMode?: 'direct' | 'reverse';
}

export const ScheduleModal: React.FC<ScheduleModalProps> = ({ 
  isOpen, 
  onClose, 
  title, 
  type, 
  data,
  inputs,
  results,
  calculationMode = 'direct'
}) => {
  const { settings } = useTheme();
  const [toast, setToast] = React.useState<{show: boolean, message: string, type: 'error' | 'info'}>({
    show: false,
    message: '',
    type: 'error'
  });

  // Mobile hardware back button handling
  React.useEffect(() => {
    if (!isOpen) return;

    // Push state to history when modal opens
    const modalState = { modalOpen: true };
    window.history.pushState(modalState, '', window.location.href);

    const handlePopState = (event: PopStateEvent) => {
      // If the back button is pressed, the history entry we pushed is automatically popped.
      // We just need to close the modal.
      onClose();
    };

    window.addEventListener('popstate', handlePopState);

    return () => {
      window.removeEventListener('popstate', handlePopState);
      // Clean up history if modal is closed manually
      if (window.history.state?.modalOpen) {
        window.history.back();
      }
    };
  }, [isOpen]); // Only react to isOpen changing

  // Handle manual closure (via X button)
  const handleManualClose = () => {
    onClose();
  };

  const SummaryLegend = () => {
    if (!inputs || !results) return null;

    if (type === 'loan') {
      const loanInputs = inputs as LoanInputs;
      const loanResults = results as LoanResults;
      return (
        <div className="mb-3 grid grid-cols-2 gap-3">
          <div className="glass py-[10px] px-3 rounded-xl border border-slate-100 dark:border-white/5 flex flex-col justify-center">
            <span className="text-[10px] text-slate-400 uppercase font-bold tracking-wider">Сумма кредита</span>
            <div className="text-sm font-bold text-slate-800 dark:text-white mt-0.5 leading-tight">{formatCurrencyCompact(loanInputs.amount)} ₽</div>
          </div>
          <div className="glass py-[10px] px-3 rounded-xl border border-slate-100 dark:border-white/5 flex flex-col justify-center">
            <span className="text-[10px] text-slate-400 uppercase font-bold tracking-wider">Ставка и Срок</span>
            <div className="text-sm font-bold text-slate-800 dark:text-white mt-0.5 leading-tight">{loanInputs.rate.toFixed(2)}% | {loanInputs.term} мес.</div>
          </div>
          
          {loanResults.totalCommission > 0 && (
            <>
              <div className="glass py-[10px] px-3 rounded-xl border border-slate-100 dark:border-white/5 flex flex-col justify-center">
                <span className="text-[10px] text-slate-400 uppercase font-bold tracking-wider">Комиссия</span>
                <div className="text-sm font-bold text-slate-800 dark:text-white mt-0.5 leading-tight">
                  {formatCurrencyCompact(loanResults.totalCommission)} ₽
                </div>
              </div>
              <div className="glass py-[10px] px-3 rounded-xl border border-slate-100 dark:border-white/5 flex flex-col justify-center">
                <span className="text-[10px] text-slate-400 uppercase font-bold tracking-wider">Проценты</span>
                <div className="text-sm font-bold text-slate-800 dark:text-white mt-0.5 leading-tight">
                  {formatCurrencyCompact(loanResults.totalInterest - loanResults.totalCommission)} ₽
                </div>
              </div>
            </>
          )}

          <div className="glass py-[10px] px-3 rounded-xl border border-slate-100 dark:border-white/5 flex flex-col justify-center">
            <span className="text-[10px] text-slate-400 uppercase font-bold tracking-wider">Переплата</span>
            <div className="text-sm font-bold text-skin-primary mt-0.5 leading-tight">
              {formatCurrencyCompact(loanResults.totalInterest)} ₽
            </div>
          </div>
          <div className="glass py-[10px] px-3 rounded-xl border border-slate-100 dark:border-white/5 flex flex-col justify-center">
            <span className="text-[10px] text-slate-400 uppercase font-bold tracking-wider">Сумма возврата</span>
            <div className="text-sm font-bold text-skin-primary mt-0.5 leading-tight">{formatCurrencyCompact(loanResults.totalRepayment)} ₽</div>
          </div>
        </div>
      );
    } else {
      const depInputs = inputs as DepositInputs;
      const depResults = results as DepositResults;
      return (
        <div className="mb-3 grid grid-cols-2 gap-3">
          <div className="glass py-[10px] px-3 rounded-xl border border-slate-100 dark:border-white/5 flex flex-col justify-center">
            <span className="text-[10px] text-slate-400 uppercase font-bold tracking-wider">Сумма вклада</span>
            <div className="text-sm font-bold text-slate-800 dark:text-white mt-0.5 leading-tight">
              {formatCurrencyCompact(depInputs.initialAmount)} ₽
            </div>
          </div>
          <div className="glass py-[10px] px-3 rounded-xl border border-slate-100 dark:border-white/5 flex flex-col justify-center">
            <span className="text-[10px] text-slate-400 uppercase font-bold tracking-wider">Пополнения</span>
            <div className="text-sm font-bold text-slate-800 dark:text-white mt-0.5 leading-tight">
              {depResults.totalAdditions > 0 ? `${formatCurrencyCompact(depResults.totalAdditions)} ₽` : '-'}
            </div>
          </div>

          <div className="glass py-[10px] px-3 rounded-xl border border-slate-100 dark:border-white/5 flex flex-col justify-center">
            <span className="text-[10px] text-slate-400 uppercase font-bold tracking-wider">Ставка и Срок</span>
            <div className="text-sm font-bold text-slate-800 dark:text-white mt-0.5 leading-tight">
              {depInputs.rate.toFixed(2)}% | {depInputs.term} мес.
            </div>
          </div>
          <div className="glass py-[10px] px-3 rounded-xl border border-slate-100 dark:border-white/5 flex flex-col justify-center">
            <span className="text-[10px] text-slate-400 uppercase font-bold tracking-wider">% за период</span>
            <div className="text-sm font-bold text-skin-primary mt-0.5 leading-tight">
              {((depResults.totalInterest / (depInputs.initialAmount + depResults.totalAdditions)) * 100).toFixed(2)}%
            </div>
          </div>

          <div className="glass py-[10px] px-3 rounded-xl border border-slate-100 dark:border-white/5 flex flex-col justify-center">
            <span className="text-[10px] text-slate-400 uppercase font-bold tracking-wider">Итоговая сумма</span>
            <div className="text-sm font-bold text-skin-primary mt-0.5 leading-tight">
              {formatCurrencyCompact(depResults.finalAmount)} ₽
            </div>
          </div>
          <div className="glass py-[10px] px-3 rounded-xl border border-slate-100 dark:border-white/5 flex flex-col justify-center">
            <span className="text-[10px] text-slate-400 uppercase font-bold tracking-wider">Доход</span>
            <div className="text-sm font-bold text-skin-primary mt-0.5 leading-tight">
              {formatCurrencyCompact(depResults.totalInterest)} ₽
            </div>
          </div>
        </div>
      );
    }
  };

  return (
    <>
      {isOpen && (
        <div
          className="fixed inset-0 z-[110] bg-slate-100/50 dark:bg-slate-900/50 backdrop-blur-sm flex flex-col items-center overflow-hidden"
        >
          <div className="w-full md:max-w-2xl lg:max-w-[800px] h-full flex flex-col relative bg-[#f9fafb] dark:bg-slate-900 shadow-2xl overflow-hidden md:border-x md:border-slate-200/50 dark:md:border-white/10">
            {/* Header */}
            <header 
            className="flex-shrink-0 z-40 px-5 flex items-center relative bg-white/80 dark:bg-slate-900/80 backdrop-blur-xl border-b border-slate-100 dark:border-white/10"
            style={{ paddingTop: 'calc(env(safe-area-inset-top, 0px) + 12px)', height: 'calc(env(safe-area-inset-top, 0px) + 80px)' }}
          >
            <button 
              onClick={handleManualClose}
              className="absolute left-5 w-10 h-10 glass rounded-xl flex items-center justify-center active:scale-95 transition-transform backdrop-blur-md border border-slate-200/50 dark:border-white/10 z-10"
            >
              <ArrowLeft size={20} className="text-skin-primary" />
            </button>
            <h1 className="text-lg font-bold text-slate-800 dark:text-white truncate w-full text-center px-24">
              {title}
            </h1>
            <button 
              onClick={async () => {
                if (!inputs || !results) return;
                
                setToast({ 
                  show: true,
                  message: 'Создание PDF отчета...', 
                  type: 'info' 
                });

                try {
                  const typeStr = type === 'loan' ? 'Loan' : 'Deposit';
                  const modeStr = calculationMode === 'direct' ? 'Direct' : 'Reverse';
                  const dateStr = new Date().toISOString().split('T')[0];
                  const timeStr = new Date().toLocaleTimeString('en-GB', { hour12: false }).replace(/:/g, '-');
                  const filename = `${typeStr}_${modeStr}_${dateStr}_${timeStr}`;
                  await shareCalculationPDF(filename, type, inputs, results, settings.skin);
                } catch (err) {
                  console.error('Share error:', err);
                  setToast({ show: true, message: 'Ошибка: ' + (err instanceof Error ? err.message : String(err)), type: 'error' });
                }
              }}
              className="absolute right-5 w-10 h-10 glass rounded-xl flex items-center justify-center active:scale-95 transition-transform backdrop-blur-md border border-slate-200/50 dark:border-white/10 z-10"
            >
              <Share2 size={20} className="text-skin-primary" />
            </button>
          </header>

          <div className="px-4 pt-6 pb-0 flex-shrink-0">
            <SummaryLegend />
          </div>
          
          <div className="flex-1 flex flex-col min-h-0 px-4 pb-[calc(env(safe-area-inset-bottom,0px)+16px)] overflow-hidden">
            <Toast 
              message={toast.message} 
              isVisible={toast.show} 
              type={toast.type}
              onClose={() => setToast(s => ({ ...s, show: false }))} 
            />
            <div className="flex-1 overflow-y-auto overscroll-contain rounded-lg glass border border-slate-100 dark:border-white/10 shadow-sm relative isolate [&::-webkit-scrollbar]:hidden [-ms-overflow-style:none] [scrollbar-width:none]">
              <table className="w-full text-left border-collapse table-fixed">
                <thead className="sticky top-0 z-30 bg-slate-50 dark:bg-slate-900 shadow-sm">
                  <tr className="border-b border-slate-100 dark:border-white/10">
                    <th className="py-3.5 px-4 text-[9px] font-bold text-skin-primary uppercase tracking-widest border-b border-slate-100 dark:border-white/5 w-12 text-center">№</th>
                    {type === 'loan' ? (
                      <>
                        <th className="py-3.5 px-4 text-[9px] font-bold text-skin-primary uppercase tracking-widest border-b border-slate-100 dark:border-white/5">Платеж / Остаток</th>
                        <th className="py-3.5 px-4 text-[9px] font-bold text-skin-primary uppercase tracking-widest border-b border-slate-100 dark:border-white/5">Проц. / Осн.</th>
                      </>
                    ) : (
                      <>
                        <th className="py-3.5 px-4 text-[9px] font-bold text-skin-primary uppercase tracking-widest border-b border-slate-100 dark:border-white/5">Процент / Взнос</th>
                        <th className="py-3.5 px-4 text-[9px] font-bold text-skin-primary uppercase tracking-widest border-b border-slate-100 dark:border-white/5">Баланс</th>
                      </>
                    )}
                  </tr>
                </thead>
                <tbody className="divide-y divide-slate-50 dark:divide-white/[0.02]">
                  {type === 'loan' ? (data as LoanScheduleItem[]).map((item, idx) => (
                    <tr 
                      key={item.month} 
                      className={`hover:bg-slate-50/50 dark:hover:bg-white/[0.02] transition-colors ${idx % 2 === 0 ? 'bg-white dark:bg-slate-900' : 'bg-slate-50 dark:bg-white/[0.01]'}`}
                    >
                      <td className="py-3.5 px-4 text-xs font-bold text-slate-400 text-center">{item.month}</td>
                      <td className="py-3.5 px-4">
                        <div className="flex flex-col gap-0.5">
                          <span className="text-xs font-normal text-slate-900 dark:text-white">{formatCurrencyCompact(item.payment)} ₽</span>
                          <span className="text-[10px] text-slate-400 italic">ост. {formatCurrencyCompact(item.remainingBalance)} ₽</span>
                        </div>
                      </td>
                      <td className="py-3.5 px-4">
                        <div className="flex flex-col gap-0.5">
                          <span className="text-xs font-normal text-skin-primary">{formatCurrencyCompact(item.interest)} ₽</span>
                          <span className="text-xs font-normal text-slate-600 dark:text-slate-300">{formatCurrencyCompact(item.principal)} ₽</span>
                        </div>
                      </td>
                    </tr>
                  )) : (data as DepositScheduleItem[]).map((item, idx) => (
                    <tr 
                      key={item.month} 
                      className={`hover:bg-slate-50/50 dark:hover:bg-white/[0.02] transition-colors ${idx % 2 === 0 ? 'bg-white dark:bg-slate-900' : 'bg-slate-50 dark:bg-white/[0.01]'}`}
                    >
                      <td className="py-3.5 px-4 text-xs font-bold text-slate-400 text-center">{item.month}</td>
                      <td className="py-3.5 px-4">
                        <div className="flex flex-col gap-0.5">
                          {item.interest > 0 ? (
                            <span className="text-[11px] font-bold text-skin-primary">
                              +{formatCurrencyCompact(item.interest)} ₽
                            </span>
                          ) : (
                            <span className="text-[11px] font-normal text-slate-300 dark:text-slate-700">-</span>
                          )}
                          <span className="text-[11px] font-normal text-slate-600 dark:text-slate-400">
                            {item.addition > 0 ? `+${formatCurrencyCompact(item.addition)} ₽` : '-'}
                          </span>
                        </div>
                      </td>
                      <td className="py-3.5 px-4">
                        <span className="text-xs font-bold text-slate-900 dark:text-white">{formatCurrencyCompact(item.balance)} ₽</span>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </div>
          </div>
        </div>
      )}
    </>
  );
};
