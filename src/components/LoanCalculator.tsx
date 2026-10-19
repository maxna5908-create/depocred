/**
 * @license
 * SPDX-License-Identifier: Apache-2.0
 */

import React, { useState, useEffect, useRef } from 'react';
import { LoanInputs, LoanResults, HistoryItem } from '../types';
import { calculateLoan, formatCurrency, formatCurrencyCompact } from '../lib/calculators';
import { GlassCard, NumInput, Button, Toast } from './ui/Common';
import { Calculator, Trash2, Save, Share2, Calendar, FileText, ArrowUp, Info as InfoIcon, X } from 'lucide-react';
import { ScheduleModal } from './ScheduleModal';
import { shareCalculationPDF } from '../lib/pdfGenerator';
import { useTheme } from './ThemeContext';

export const LoanCalculator: React.FC<{ 
  onSave: (item: HistoryItem) => void,
  session: { 
    directInputs: LoanInputs; 
    reverseInputs: LoanInputs; 
    results: LoanResults | null; 
    calculationMode: 'direct' | 'reverse' 
  },
  onSessionChange: (session: { 
    directInputs: LoanInputs; 
    reverseInputs: LoanInputs; 
    results: LoanResults | null; 
    calculationMode: 'direct' | 'reverse' 
  }) => void
}> = ({ onSave, session, onSessionChange }) => {
  const { settings, setShowHints } = useTheme();
  const containerRef = useRef<HTMLDivElement>(null);
  const resultsHeaderRef = useRef<HTMLDivElement>(null);
  const amountRef = useRef<HTMLInputElement>(null);
  const rateRef = useRef<HTMLInputElement>(null);
  const termRef = useRef<HTMLInputElement>(null);
  const paymentTypeRef = useRef<HTMLDivElement>(null);
  const wasCalculated = useRef(false);

  const [toast, setToast] = useState<{show: boolean, message: string, type: 'error' | 'info'}>({
    show: false,
    message: '',
    type: 'error'
  });

  const validateReverseAccess = () => {
    if (calculationMode !== 'reverse') return true;
    
    if (inputs.amount <= 0) {
      setToast({ show: true, message: "Сначала укажите сумму кредита", type: 'error' });
      amountRef.current?.focus();
      return false;
    }
    
    if (inputs.paymentType === null) {
      setToast({ show: true, message: "Сначала выберите тип платежа", type: 'error' });
      paymentTypeRef.current?.scrollIntoView({ behavior: 'smooth', block: 'center' });
      return false;
    }
    
    return true;
  };

  const solveForRate = (amount: number, term: number, payment: number, type: 'annuity' | 'differentiated'): number => {
    if (amount <= 0 || term <= 0 || payment <= 0) return 0;
    
    if (type === 'differentiated') {
      // payment is M1 (first payment) for differentiated
      // M1 = P/n + P*i => i = (M1 - P/n) / P
      if (payment <= amount / term) return 0;
      const i = (payment - amount / term) / amount;
      return Math.max(0, i * 12 * 100);
    } else {
      // Annuity Newton-Raphson
      const A = amount;
      const P = payment;
      const n = term;
      
      if (P * n <= A) return 0;

      let i = (P * n / A - 1) / (n / 2);
      for (let j = 0; j < 25; j++) {
        const pow = Math.pow(1 + i, n);
        const f = A * i * pow / (pow - 1) - P;
        const fPrime = A * (pow * (pow - 1 - n * i) / Math.pow(pow - 1, 2));
        if (Math.abs(fPrime) < 1e-10) break;
        const nextI = i - f / fPrime;
        if (Math.abs(nextI - i) < 0.0000001) {
          i = nextI;
          break;
        }
        i = nextI;
      }
      return Math.max(0, i * 12 * 100);
    }
  };

  const [rateInfoModal, setRateInfoModal] = useState<{ show: boolean; type: 'nominal' | 'effective' }>({ show: false, type: 'nominal' });
  const getInitialHintState = (key: string) => {
    try {
      if (typeof window !== 'undefined' && window.localStorage) {
        return localStorage.getItem(key) === 'true';
      }
    } catch (e) {
      console.warn("localStorage access denied", e);
    }
    return false;
  };

  const [isReverseDescHidden, setIsReverseDescHidden] = useState(() => getInitialHintState('isReverseDescHidden'));
  const [isReverseActionHintHidden, setIsReverseActionHintHidden] = useState(() => getInitialHintState('isReverseActionHintHidden'));

  useEffect(() => {
    if (settings.showHints) {
      setIsReverseDescHidden(false);
      setIsReverseActionHintHidden(false);
      try {
        if (typeof window !== 'undefined' && window.localStorage) {
          localStorage.removeItem('isReverseDescHidden');
          localStorage.removeItem('isReverseActionHintHidden');
        }
      } catch (e) {
        console.warn("localStorage clear failed", e);
      }
    }
  }, [settings.showHints]);

  const hideHint = (setter: (v: boolean) => void, key: string) => {
    setter(true);
    try {
      if (typeof window !== 'undefined' && window.localStorage) {
        localStorage.setItem(key, 'true');
      }
    } catch (e) {
      console.warn("localStorage save failed", e);
    }
    setShowHints(false);
    setToast({ 
      show: true, 
      message: 'Вы можете включить подсказки в настройках - меню "инфо"', 
      type: 'info' 
    });
  };

  const hideReverseDesc = () => hideHint(setIsReverseDescHidden, 'isReverseDescHidden');
  const hideReverseActionHint = () => hideHint(setIsReverseActionHintHidden, 'isReverseActionHintHidden');

  const [calculationMode, setCalculationMode] = useState<'direct' | 'reverse'>(session.calculationMode);
  const [calculatedField, setCalculatedField] = useState<'term' | 'reversePayment' | 'reverseTotalRepayment' | null>(null);
  const [directInputs, setDirectInputs] = useState<LoanInputs>(session.directInputs);
  const [reverseInputs, setReverseInputs] = useState<LoanInputs>(session.reverseInputs);
  const [inputs, setInputs] = useState<LoanInputs>(
    session.calculationMode === 'direct' ? session.directInputs : session.reverseInputs
  );

  const [results, setResults] = useState<LoanResults | null>(session.results);
  const [showSchedule, setShowSchedule] = useState(false);

  useEffect(() => {
    // Scroll to top instantly on mount when returning to tool
    window.scrollTo({ top: 0, behavior: 'instant' });
    // Reset results on mount to clear result panel when switching back to this tool
    setResults(null);
  }, []);

  // Reverse calculation logic: 2-of-3 fields
  useEffect(() => {
    if (calculationMode !== 'reverse') {
      if (calculatedField) setCalculatedField(null);
      return;
    }

    const { term, reversePayment = 0, reverseTotalRepayment = 0 } = inputs;
    
    // Check non-zero fields among the three
    const fields = [
      { name: 'term', val: term },
      { name: 'reversePayment', val: reversePayment },
      { name: 'reverseTotalRepayment', val: reverseTotalRepayment }
    ];
    
    if (!calculatedField) {
      // Look for exactly 2 filled fields to lock the 3rd one
      const filled = fields.filter(f => f.val > 0);
      if (filled.length === 2) {
        const empty = fields.find(f => f.val === 0);
        if (empty) {
          const fieldName = empty.name as 'term' | 'reversePayment' | 'reverseTotalRepayment';
          
          setCalculatedField(fieldName);
          
          let result = 0;
          const loanP = inputs.amount - (inputs.downPayment || 0);
          if (fieldName === 'reverseTotalRepayment') {
            if (inputs.paymentType === 'differentiated') {
              // S = P * (n-1)/(2n) + M1 * (n+1)/2
              result = term > 0 ? Math.round(loanP * (term - 1) / (2 * term) + reversePayment * (term + 1) / 2) : 0;
            } else {
              result = term * reversePayment;
            }
          } else if (fieldName === 'reversePayment') {
            if (inputs.paymentType === 'differentiated') {
              // M1 = (2S - loanP * (n-1)/n) / (n+1)
              result = term > 0 ? Math.round((2 * reverseTotalRepayment - loanP * (term - 1) / term) / (term + 1)) : 0;
            } else {
              result = term > 0 ? Math.round(reverseTotalRepayment / term) : 0;
            }
          } else if (fieldName === 'term') {
            if (inputs.paymentType === 'differentiated' && loanP > 0 && reversePayment > 0 && reverseTotalRepayment > 0) {
              // Quadratic equation for n: M1*n^2 + (P+M1-2S)*n - P = 0
              const a = reversePayment;
              const b = loanP + reversePayment - 2 * reverseTotalRepayment;
              const c = -loanP;
              const discriminant = b * b - 4 * a * c;
              if (discriminant >= 0) {
                result = Math.round((-b + Math.sqrt(discriminant)) / (2 * a));
              }
            } else {
              result = reversePayment > 0 ? Math.round(reverseTotalRepayment / reversePayment) : 0;
            }
          }
          
          if (result > 0) {
            setInputs(s => ({ ...s, [fieldName]: result }));
          }
        }
      }
    } else {
      // Calculate or unlock
      const others = fields.filter(f => f.name !== calculatedField);
      const anyZero = others.some(f => f.val === 0);
      
      if (anyZero) {
        const fieldToClear = calculatedField;
        setCalculatedField(null);
        setInputs(s => ({ ...s, [fieldToClear]: 0 }));
      } else {
        let result = 0;
        const loanP = inputs.amount - (inputs.downPayment || 0);
        if (calculatedField === 'reverseTotalRepayment') {
          if (inputs.paymentType === 'differentiated') {
            // S = P * (n-1)/(2n) + M1 * (n+1)/2
            result = term > 0 ? Math.round(loanP * (term - 1) / (2 * term) + reversePayment * (term + 1) / 2) : 0;
          } else {
            result = term * reversePayment;
          }
        } else if (calculatedField === 'reversePayment') {
          if (inputs.paymentType === 'differentiated') {
            // M1 = (2S - loanP * (n-1)/n) / (n+1)
            result = term > 0 ? Math.round((2 * reverseTotalRepayment - loanP * (term - 1) / term) / (term + 1)) : 0;
          } else {
            result = term > 0 ? Math.round(reverseTotalRepayment / term) : 0;
          }
        } else if (calculatedField === 'term') {
          if (inputs.paymentType === 'differentiated' && loanP > 0 && reversePayment > 0 && reverseTotalRepayment > 0) {
            const a = reversePayment;
            const b = loanP + reversePayment - 2 * reverseTotalRepayment;
            const c = -loanP;
            const discriminant = b * b - 4 * a * c;
            if (discriminant >= 0) {
              result = Math.round((-b + Math.sqrt(discriminant)) / (2 * a));
            }
          } else {
            result = reversePayment > 0 ? Math.round(reverseTotalRepayment / reversePayment) : 0;
          }
        }
        
        if (result !== inputs[calculatedField]) {
          setInputs(s => ({ ...s, [calculatedField]: result }));
        }
      }
    }
  }, [inputs.term, inputs.reversePayment, inputs.reverseTotalRepayment, inputs.paymentType, calculationMode, calculatedField]);

  // Update independent states when local inputs change
  useEffect(() => {
    if (calculationMode === 'direct') {
      setDirectInputs(inputs);
    } else {
      setReverseInputs(inputs);
    }
  }, [inputs]);

  // Update session in parent when local state changes
  useEffect(() => {
    onSessionChange({ directInputs, reverseInputs, results, calculationMode });
  }, [directInputs, reverseInputs, results, calculationMode]);

  useEffect(() => {
    if (results && resultsHeaderRef.current) {
      const scrollContainer = resultsHeaderRef.current.closest('.overflow-y-auto');
      if (scrollContainer instanceof HTMLElement) {
        const isNewCalc = wasCalculated.current;
        const behavior = isNewCalc ? 'smooth' : 'auto';
        
        setTimeout(() => {
          if (!resultsHeaderRef.current || !scrollContainer) return;
          
          const containerRect = scrollContainer.getBoundingClientRect();
          const elementRect = resultsHeaderRef.current.getBoundingClientRect();
          
          const targetScroll = scrollContainer.scrollTop + (elementRect.top - containerRect.top) - 22;
          
          scrollContainer.scrollTo({
            top: Math.max(0, targetScroll),
            behavior: behavior as ScrollBehavior
          });

          wasCalculated.current = false;
        }, isNewCalc ? 150 : 50);
      }
    }
  }, [results, inputs.paymentType]);

  const handleCalculate = () => {
    if (inputs.amount <= 0) {
      setToast({ show: true, message: "Заполните все необходимые поля для расчета кредита", type: 'error' });
      amountRef.current?.focus();
      return;
    }

    let finalInputs = { ...inputs };

    if (calculationMode === 'direct') {
      if (inputs.rate <= 0) {
        setToast({ show: true, message: "Заполните все необходимые поля для расчета кредита", type: 'error' });
        rateRef.current?.focus();
        return;
      }
      if (inputs.term <= 0) {
        setToast({ show: true, message: "Заполните все необходимые поля для расчета кредита", type: 'error' });
        termRef.current?.focus();
        return;
      }
    } else {
      // Reverse mode
      if (inputs.paymentType === null) {
        setToast({ show: true, message: "Выберите тип платежа для продолжения", type: 'error' });
        return;
      }

      const loanAmount = inputs.amount - (inputs.downPayment || 0);
      
      if (inputs.reverseTotalRepayment > 0 && inputs.reverseTotalRepayment < loanAmount) {
        setToast({ show: true, message: "Сумма возврата не может быть меньше суммы кредита", type: 'error' });
        return;
      }

      if (!inputs.term || !inputs.reversePayment) {
        setToast({ show: true, message: "Заполните любые два значения из трех", type: 'error' });
        return;
      }
      const solvedRate = solveForRate(
        loanAmount,
        inputs.term,
        inputs.reversePayment,
        inputs.paymentType as 'annuity' | 'differentiated'
      );
      
      finalInputs.rate = solvedRate;
    }

    const res = calculateLoan(finalInputs as any);
    setInputs(finalInputs);
    setResults(res);
    wasCalculated.current = true;
  };

  const handleSave = () => {
    if (!results) return;
    const historyItem: HistoryItem = {
      id: Math.random().toString(36).substr(2, 9),
      type: 'loan',
      timestamp: Date.now(),
      inputs: { ...inputs },
      results: { ...results },
      label: `Кредит ${formatCurrency(inputs.amount)}`
    };
    onSave(historyItem);
    setToast({ show: true, message: "Текущий расчет по кредиту сохранен в историю расчетов", type: 'info' });
  };

  const scrollToTop = () => {
    const scrollContainer = containerRef.current?.closest('.overflow-y-auto');
    if (scrollContainer) {
      scrollContainer.scrollTo({ top: 0, behavior: 'smooth' });
    }
  };

  const handleReset = () => {
    setInputs({
      amount: 0,
      rate: 0,
      term: 0,
      fixedCommission: 0,
      percentCommission: 0,
      paymentType: calculationMode === 'reverse' ? null : 'annuity',
      downPayment: 0,
      reversePayment: 0,
      reverseTotalRepayment: 0
    });
    setResults(null);
    const scrollContainer = containerRef.current?.closest('.overflow-y-auto');
    if (scrollContainer) {
      scrollContainer.scrollTo({ top: 0, behavior: 'smooth' });
    }
  };

  const isInputsEmpty = inputs.amount === 0 && 
    inputs.rate === 0 && 
    inputs.term === 0 && 
    inputs.fixedCommission === 0 && 
    inputs.percentCommission === 0 &&
    (inputs.downPayment || 0) === 0 &&
    (calculationMode === 'reverse' ? inputs.paymentType === null : true);

  const handleShare = async () => {
    if (!results) return;
    
    setToast({ 
      show: true,
      message: 'Создание PDF отчета...', 
      type: 'info' 
    });

    try {
      const typeStr = 'Loan';
      const modeStr = calculationMode === 'direct' ? 'Direct' : 'Reverse';
      const dateStr = new Date().toISOString().split('T')[0];
      const timeStr = new Date().toLocaleTimeString('en-GB', { hour12: false }).replace(/:/g, '-');
      const filename = `${typeStr}_${modeStr}_${dateStr}_${timeStr}`;
      
      await shareCalculationPDF(filename, 'loan', inputs, results, settings.skin);
    } catch (err) {
      console.error('Share error:', err);
      // Fallback to text share if PDF fails
      const text = `Кредитный расчет:
Сумма: ${formatCurrency(inputs.amount)}
Ставка: ${inputs.rate.toFixed(2)}%
Срок: ${inputs.term} мес.
Платёж: ${formatCurrency(Array.isArray(results.monthlyPayment) ? results.monthlyPayment[0] : results.monthlyPayment)}
Переплата: ${formatCurrency(results.totalInterest)}`;

      if (navigator.share) {
        await navigator.share({
          title: 'Кредитный расчет',
          text: text
        });
      } else {
        await navigator.clipboard.writeText(text);
        setToast({ show: true, message: 'Расчет скопирован в буфер', type: 'info' });
      }
    }
  };

  const handleSwitchTab = (type: 'annuity' | 'differentiated') => {
    if (inputs.paymentType === type) return;
    const nextInputs = { ...inputs, paymentType: type };
    setInputs(nextInputs);
    if (results) {
      if (calculationMode === 'direct') {
        setResults(calculateLoan(nextInputs));
      } else {
        // Reverse mode: clear results as requested
        setResults(null);
      }
    }
  };

  return (
    <div ref={containerRef} className="flex flex-col gap-6 p-4 pb-32">
      <Toast 
        message={toast.message} 
        isVisible={toast.show} 
        type={toast.type}
        onClose={() => setToast(s => ({ ...s, show: false }))} 
      />

      <div className="flex flex-col">
        <div className="flex h-[42px] w-full relative">
          <button 
            tabIndex={-1}
            onClick={() => {
              setCalculationMode('direct');
              setInputs(directInputs);
              setResults(null);
            }}
            className={`flex-1 py-2 rounded-t-2xl font-bold text-xs relative z-20 flex items-center justify-center overflow-hidden transition-none duration-0 ${
              calculationMode === 'direct' 
              ? 'glass-card !shadow-none !p-0 !rounded-b-none !border-b-0 -mb-[1px] text-slate-900 dark:text-white' 
              : 'text-slate-500 dark:text-slate-400 hover:text-slate-800 dark:hover:text-slate-200 bg-transparent'
            }`}
          >
            <span className="relative z-10">Прямой</span>
          </button>
          <button 
            tabIndex={-1}
            onClick={() => {
              setCalculationMode('reverse');
              setInputs(reverseInputs);
              setResults(null);
            }}
            className={`flex-1 py-2 rounded-t-2xl font-bold text-xs relative z-20 flex items-center justify-center overflow-hidden transition-none duration-0 ${
              calculationMode === 'reverse' 
              ? 'glass-card !shadow-none !p-0 !rounded-b-none !border-b-0 -mb-[1px] text-slate-900 dark:text-white' 
              : 'text-slate-500 dark:text-slate-400 hover:text-slate-800 dark:hover:text-slate-200 bg-transparent'
            }`}
          >
            <span className="relative z-10">Обратный</span>
          </button>
        </div>

        <GlassCard className={`flex flex-col gap-6 !mt-0 ${calculationMode === 'direct' ? 'rounded-tl-none' : 'rounded-tr-none'}`}>
          {settings.showHints && calculationMode === 'reverse' && !isReverseDescHidden && (
            <div 
              onClick={hideReverseDesc}
              className="p-3 bg-skin-primary/5 dark:bg-skin-primary/10 rounded-xl border border-skin-primary/20 cursor-pointer hover:bg-skin-primary/10 dark:hover:bg-skin-primary/20 transition-all flex flex-col gap-2"
            >
              <p className="text-[13px] italic leading-tight text-skin-primary dark:text-skin-primary/90 font-medium">
                Обратный расчет позволяет вычислить номинальную и эффективную ставку исходя из указанных параметров кредита.
              </p>
              <p className="text-[9px] font-bold uppercase underline tracking-wider text-skin-primary/60 text-center">
                НАЖМИТЕ ЧТОБЫ СКРЫТЬ ПОДСКАЗКУ
              </p>
            </div>
          )}
          <div className="flex flex-col gap-4">
          <NumInput 
            ref={amountRef}
            label="СУММА КРЕДИТА (₽)" 
            value={inputs.amount} 
            onChange={v => {
              if (results) setResults(null);
              setInputs(s => ({ ...s, amount: v }));
            }} 
            suffix="₽"
          />

          {calculationMode === 'reverse' && (
            <div ref={paymentTypeRef} className="flex flex-col gap-2">
              <span className="text-[10px] uppercase text-slate-500 dark:text-slate-400 font-bold tracking-widest ml-1">Тип ежемесячного платежа</span>
              <div className="grid grid-cols-1 gap-2">
                <button 
                  tabIndex={-1}
                  onClick={() => {
                    if (inputs.amount <= 0) {
                      setToast({ show: true, message: "Для расчета укажите сумму кредита", type: 'error' });
                      return;
                    }
                    handleSwitchTab('annuity');
                  }}
                  className={`flex items-center gap-3 p-3 rounded-xl border transition-all ${
                    inputs.paymentType === 'annuity' 
                    ? 'bg-transparent border-slate-200 dark:border-white/10 text-slate-900 dark:text-white' 
                    : 'bg-transparent border-slate-200 dark:border-white/10 text-slate-500 dark:text-slate-400 hover:border-slate-300 dark:hover:border-white/20'
                  }`}
                >
                  <div className={`w-4 h-4 rounded-full border-2 flex items-center justify-center ${inputs.paymentType === 'annuity' ? 'border-skin-primary' : 'border-slate-300 dark:border-white/20'}`}>
                    {inputs.paymentType === 'annuity' && <div className="w-2 h-2 rounded-full bg-skin-primary" />}
                  </div>
                  <span className="text-xs font-medium">Аннуитетный - фиксированный</span>
                </button>
                <button 
                  tabIndex={-1}
                  onClick={() => {
                    if (inputs.amount <= 0) {
                      setToast({ show: true, message: "Для расчета укажите сумму кредита", type: 'error' });
                      return;
                    }
                    handleSwitchTab('differentiated');
                  }}
                  className={`flex items-center gap-3 p-3 rounded-xl border transition-all ${
                    inputs.paymentType === 'differentiated' 
                    ? 'bg-transparent border-slate-200 dark:border-white/10 text-slate-900 dark:text-white' 
                    : 'bg-transparent border-slate-200 dark:border-white/10 text-slate-500 dark:text-slate-400 hover:border-slate-300 dark:hover:border-white/20'
                  }`}
                >
                  <div className={`w-4 h-4 rounded-full border-2 flex items-center justify-center ${inputs.paymentType === 'differentiated' ? 'border-skin-primary' : 'border-slate-300 dark:border-white/20'}`}>
                    {inputs.paymentType === 'differentiated' && <div className="w-2 h-2 rounded-full bg-skin-primary" />}
                  </div>
                  <span className="text-xs font-medium">Дифференцированный - уменьшаемый</span>
                </button>
              </div>
            </div>
          )}

          {settings.showHints && calculationMode === 'reverse' && inputs.paymentType !== null && !isReverseActionHintHidden && (
            <div 
              onClick={hideReverseActionHint}
              className="p-3 bg-skin-primary/5 dark:bg-skin-primary/10 rounded-xl border border-skin-primary/20 cursor-pointer hover:bg-skin-primary/10 dark:hover:hover:bg-skin-primary/20 transition-all flex flex-col gap-2"
            >
              <p className="text-[13px] italic leading-tight text-skin-primary dark:text-skin-primary/90 font-medium">
                Ниже заполните любые два значения параметров кредита, третье значение рассчитается автоматически.
              </p>
              <p className="text-[9px] font-bold uppercase underline tracking-wider text-skin-primary/60 text-center">
                НАЖМИТЕ ЧТОБЫ СКРЫТЬ ПОДСКАЗКУ
              </p>
            </div>
          )}
          
          {calculationMode === 'direct' && (
            <div className="flex gap-4">
              <NumInput 
                ref={rateRef}
                label="СТАВКА (%)" 
                value={inputs.rate} 
                onChange={v => {
                  if (results) setResults(null);
                  setInputs(s => ({ ...s, rate: v }));
                }} 
                suffix="%"
                maxBeforeDecimal={4}
              />
              <NumInput 
                ref={termRef}
                label="СРОК (МЕС.)" 
                value={inputs.term} 
                onChange={v => {
                  if (results) setResults(null);
                  setInputs(s => ({ ...s, term: v }));
                }} 
                onlyInteger={true}
                suffix="мес."
                maxTotal={3}
              />
            </div>
          )}

          {calculationMode === 'reverse' && inputs.paymentType !== null && (
            <div className="flex flex-col gap-4">
              <div className="flex gap-4">
                <NumInput 
                  ref={termRef}
                  label="СРОК (МЕС.)" 
                  value={inputs.term} 
                  onChange={v => {
                    if (results) setResults(null);
                    setInputs(s => ({ ...s, term: v }));
                  }} 
                  onFocus={(e) => {
                    if (!validateReverseAccess()) {
                      e.target.blur();
                    }
                  }}
                  onBlur={() => {
                    if (calculationMode === 'reverse' && calculatedField === 'reverseTotalRepayment') {
                      const loanP = inputs.amount - (inputs.downPayment || 0);
                      if (inputs.reverseTotalRepayment > 0 && inputs.reverseTotalRepayment < loanP) {
                        setToast({ show: true, message: "Сумма возврата не может быть меньше суммы кредита", type: 'error' });
                      }
                    }
                  }}
                  onlyInteger={true}
                  suffix="мес."
                  maxTotal={3}
                  disabled={calculatedField === 'term'}
                  isCalculated={calculatedField === 'term'}
                  showClear={calculatedField !== 'term' && inputs.term > 0}
                  onClear={() => {
                    if (results) setResults(null);
                    setInputs(s => ({ ...s, term: 0 }));
                  }}
                />
                <NumInput 
                  label={inputs.paymentType === 'differentiated' ? "ПЕРВЫЙ ПЛАТЁЖ (₽)" : "ПЛАТЁЖ (₽)"} 
                  value={inputs.reversePayment || 0} 
                  onChange={v => {
                    if (results) setResults(null);
                    setInputs(s => ({ ...s, reversePayment: v }));
                  }} 
                  onFocus={(e) => {
                    if (!validateReverseAccess()) {
                      e.target.blur();
                    }
                  }}
                  onBlur={() => {
                    if (calculationMode === 'reverse' && calculatedField === 'reverseTotalRepayment') {
                      const loanP = inputs.amount - (inputs.downPayment || 0);
                      if (inputs.reverseTotalRepayment > 0 && inputs.reverseTotalRepayment < loanP) {
                        setToast({ show: true, message: "Сумма возврата не может быть меньше суммы кредита", type: 'error' });
                      }
                    }
                  }}
                  suffix="₽"
                  disabled={calculatedField === 'reversePayment'}
                  isCalculated={calculatedField === 'reversePayment'}
                  showClear={calculatedField !== 'reversePayment' && (inputs.reversePayment || 0) > 0}
                  onClear={() => {
                    if (results) setResults(null);
                    setInputs(s => ({ ...s, reversePayment: 0 }));
                  }}
                />
              </div>
              <NumInput 
                label="СУММА ВОЗВРАТА (₽)" 
                value={inputs.reverseTotalRepayment || 0} 
                onChange={v => {
                  if (results) setResults(null);
                  setInputs(s => ({ ...s, reverseTotalRepayment: v }));
                }} 
                onFocus={(e) => {
                  if (!validateReverseAccess()) {
                    e.target.blur();
                  }
                }}
                onBlur={() => {
                  const loanP = inputs.amount - (inputs.downPayment || 0);
                  if (inputs.reverseTotalRepayment > 0 && inputs.reverseTotalRepayment < loanP) {
                    setToast({ show: true, message: "Сумма возврата не может быть меньше суммы кредита", type: 'error' });
                  }
                }}
                suffix="₽"
                disabled={calculatedField === 'reverseTotalRepayment'}
                isCalculated={calculatedField === 'reverseTotalRepayment'}
                showClear={calculatedField !== 'reverseTotalRepayment' && (inputs.reverseTotalRepayment || 0) > 0}
                onClear={() => {
                  if (results) setResults(null);
                  setInputs(s => ({ ...s, reverseTotalRepayment: 0 }));
                }}
              />
              <NumInput 
                label="ПЕРВОНАЧАЛЬНЫЙ ВЗНОС (₽)" 
                value={inputs.downPayment || 0} 
                onChange={v => {
                  if (results) setResults(null);
                  if (v > inputs.amount) {
                    setToast({ show: true, message: "Взнос не может быть больше суммы кредита/покупки", type: 'error' });
                    setInputs(s => ({ ...s, downPayment: inputs.amount }));
                  } else {
                    setInputs(s => ({ ...s, downPayment: v }));
                  }
                }} 
                suffix="₽"
                isLast={true}
                placeholder="Вычитается из суммы кредита"
                placeholderClassName="placeholder:text-slate-500 dark:placeholder:text-slate-400 placeholder:font-medium placeholder:text-[12px]"
              />
            </div>
          )}

          {calculationMode === 'direct' && (
            <div className="flex gap-4">
              <NumInput 
                label="КОМИССИЯ (₽)" 
                value={inputs.fixedCommission} 
                onChange={v => {
                  if (results) setResults(null);
                  if (v > inputs.amount) {
                    setToast({ show: true, message: "Комиссия не может быть больше суммы кредита", type: 'error' });
                    // We can either cap it or just not update. Capping to amount is often better UI.
                    setInputs(s => ({ ...s, fixedCommission: inputs.amount }));
                  } else {
                    setInputs(s => ({ ...s, fixedCommission: v }));
                  }
                }} 
                suffix="₽"
              />
              <NumInput 
                label="КОМИССИЯ (%)" 
                value={inputs.percentCommission} 
                onChange={v => {
                  if (results) setResults(null);
                  if (v > 100) {
                    setToast({ show: true, message: "Комиссия не может быть более 100% суммы кредита", type: 'error' });
                    setInputs(s => ({ ...s, percentCommission: 100 }));
                  } else {
                    setInputs(s => ({ ...s, percentCommission: v }));
                  }
                }} 
                suffix="%"
                maxBeforeDecimal={3}
                isLast={true}
              />
            </div>
          )}

          {calculationMode === 'direct' && (
            <p className="text-[13px] italic text-skin-primary ml-1 mt-1 leading-tight">
              *Расчет производится для обоих типов платежей.
            </p>
          )}

          <div className="flex gap-2 mt-3 mb-1">
            <Button variant="primary" onClick={handleCalculate} className="flex-1 !h-12 !rounded-2xl shadow-lg shadow-skin-primary/20">
              <Calculator size={18} className="mr-2" />
              Рассчитать
            </Button>
            <Button variant="secondary" onClick={handleReset} className="w-12 !h-12 !p-0 !rounded-2xl" aria-label="Сброс">
              <Trash2 size={20} />
            </Button>
          </div>
        </div>
      </GlassCard>
    </div>

      {results && (
        <div ref={resultsHeaderRef} className="relative h-12 flex items-center justify-center mt-2">
          <div className="absolute inset-0 flex items-center" aria-hidden="true">
            <div className="w-full border-t border-slate-200 dark:border-white/10"></div>
          </div>
          <div className="relative flex justify-center text-sm">
            <span className="px-5 py-1.5 rounded-full bg-white dark:bg-slate-900 border border-slate-200 dark:border-white/10 text-skin-primary font-bold tracking-tight shadow-sm backdrop-blur-md">
              {calculationMode === 'direct' ? 'Прямой расчет по кредиту' : 'Обратный расчет по кредиту'}
            </span>
          </div>
        </div>
      )}

      {results && (
        <div className="flex flex-col mt-[6px]">
          {calculationMode === 'direct' ? (
              <>
                <div className="flex h-[42px] w-full relative">
                  <button 
                    tabIndex={-1}
                    onClick={() => handleSwitchTab('annuity')}
                    className={`flex-1 py-2 rounded-t-2xl font-bold text-xs relative z-20 flex items-center justify-center overflow-hidden transition-none duration-0 ${
                      inputs.paymentType === 'annuity' 
                      ? 'glass-card !shadow-none !p-0 !rounded-b-none !border-b-0 -mb-[1px] text-slate-900 dark:text-white' 
                      : 'text-slate-500 dark:text-slate-400 hover:text-slate-800 dark:hover:text-slate-200 bg-transparent'
                    }`}
                  >
                    <span className="relative z-10">Аннуитетный</span>
                  </button>
                  <button 
                    tabIndex={-1}
                    onClick={() => handleSwitchTab('differentiated')}
                    className={`flex-1 py-2 rounded-t-2xl font-bold text-xs relative z-20 flex items-center justify-center overflow-hidden transition-none duration-0 ${
                      inputs.paymentType === 'differentiated' 
                      ? 'glass-card !shadow-none !p-0 !rounded-b-none !border-b-0 -mb-[1px] text-slate-900 dark:text-white' 
                      : 'text-slate-500 dark:text-slate-400 hover:text-slate-800 dark:hover:text-slate-200 bg-transparent'
                    }`}
                  >
                    <span className="relative z-10">Дифференцированный</span>
                  </button>
                </div>
                
                <div className={`glass-card !mt-0 relative overflow-hidden ${inputs.paymentType === 'annuity' ? 'rounded-tl-none' : 'rounded-tr-none'}`}>
                  <div className="flex flex-col gap-4 relative z-10">
                    <div className="flex justify-between items-center">
                      <span className="text-sm text-slate-400 dark:text-slate-300">
                        Ежемесячный платеж
                      </span>
                      <div className="flex gap-2">
                        <span className="text-[10px] px-2 py-0.5 rounded-lg bg-skin-primary/20 text-skin-primary border border-skin-primary/30 font-medium tracking-tight whitespace-nowrap">
                          {inputs.paymentType === 'annuity' ? 'Фиксированный' : 'Уменьшаемый'}
                        </span>
                      </div>
                    </div>
                    
                    <div className="flex flex-col">
                      <div className="text-4xl font-bold text-slate-900 dark:text-white tracking-tighter">
                        {Array.isArray(results.monthlyPayment) 
                          ? `${formatCurrencyCompact(results.monthlyPayment[0])} ₽`
                          : `${formatCurrencyCompact(results.monthlyPayment)} ₽`}
                      </div>
                      {inputs.paymentType === 'differentiated' && Array.isArray(results.monthlyPayment) && (
                        <div className="text-[11px] text-slate-500 dark:text-slate-400 mt-0.5 ml-1">
                          до {formatCurrencyCompact(results.monthlyPayment[results.monthlyPayment.length - 1])} ₽
                        </div>
                      )}
                    </div>

                    <div className="h-px bg-black/5 dark:bg-white/10 w-full my-1"></div>

                    <div className="grid grid-cols-2 gap-4">
                      <div className="flex flex-col">
                        <span className="text-[10px] uppercase text-slate-500 dark:text-slate-400 font-bold tracking-widest h-[14px] leading-none">Переплата</span>
                        <span className="text-md font-semibold text-skin-primary leading-none mt-1">
                          {formatCurrencyCompact(results.totalInterest)} ₽
                        </span>
                      </div>
                      <div className="flex flex-col">
                        <span className="text-[10px] uppercase text-slate-500 dark:text-slate-400 font-bold tracking-widest h-[14px] leading-none">Сумма возврата</span>
                        <span className="text-md font-semibold text-skin-primary leading-none mt-1">
                          {formatCurrencyCompact(results.totalRepayment)} ₽
                        </span>
                      </div>
                    </div>

                    <div className="grid grid-cols-2 gap-4">
                      <div className="flex flex-col">
                        <span className="text-[10px] uppercase text-slate-500 dark:text-slate-400 font-bold tracking-widest h-[14px] leading-none">Комиссия</span>
                        <div className="flex flex-col">
                          <span className="text-md font-semibold text-slate-700 dark:text-white leading-none mt-1">
                            {(() => {
                              const hasFixed = inputs.fixedCommission > 0;
                              const hasPercent = inputs.percentCommission > 0;
                              if (hasFixed && hasPercent) return `${formatCurrencyCompact(inputs.fixedCommission)} ₽ + ${inputs.percentCommission}%`;
                              if (hasFixed) return `${formatCurrencyCompact(inputs.fixedCommission)} ₽`;
                              if (hasPercent) return `${inputs.percentCommission}%`;
                              return "-";
                            })()}
                          </span>
                        </div>
                      </div>
                      <div className="flex flex-col">
                        <span className="text-[10px] uppercase text-slate-500 dark:text-slate-400 font-bold tracking-widest h-[14px] leading-none">Срок кредита</span>
                        <span className="text-md font-semibold text-slate-700 dark:text-white leading-none mt-1">
                          {inputs.term} мес.
                        </span>
                      </div>
                    </div>

                    <div className="grid grid-cols-2 gap-4">
                      <div 
                        className="flex flex-col group cursor-pointer p-2 -m-2 rounded-xl active:bg-black/5 dark:active:bg-white/5 transition-colors" 
                        onClick={() => setRateInfoModal({ show: true, type: 'nominal' })}
                      >
                        <div className="flex items-center gap-1 h-[14px]">
                          <span className="text-[10px] uppercase text-slate-500 dark:text-slate-400 font-bold tracking-widest group-hover:text-skin-primary transition-colors leading-none">Номинальная ставка</span>
                          <InfoIcon size={12} className="text-skin-primary transition-colors" />
                        </div>
                        <span className="text-md font-semibold text-skin-primary leading-none mt-1">
                          {inputs.rate.toFixed(2)}%
                        </span>
                      </div>
                      {results.effectiveRate !== undefined && (
                        <div 
                          className="flex flex-col group cursor-pointer p-2 -m-2 rounded-xl active:bg-black/5 dark:active:bg-white/5 transition-colors" 
                          onClick={() => setRateInfoModal({ show: true, type: 'effective' })}
                        >
                          <div className="flex items-center gap-1 h-[14px]">
                            <span className="text-[10px] uppercase text-slate-500 dark:text-slate-400 font-bold tracking-widest group-hover:text-skin-primary transition-colors leading-none">Эффективная ставка</span>
                            <InfoIcon size={12} className="text-skin-primary transition-colors" />
                          </div>
                          <span className="text-md font-semibold text-skin-primary leading-none mt-1">
                            {results.effectiveRate.toFixed(2)}%
                          </span>
                        </div>
                      )}
                    </div>

                    <div className="flex items-center gap-3 mt-2">
                      <button 
                        onClick={() => setShowSchedule(true)}
                        className="flex-1 h-14 bg-skin-primary text-white rounded-2xl flex items-center justify-center text-sm font-bold active:scale-95 transition-transform shadow-lg shadow-skin-primary/20"
                      >
                        График платежей
                      </button>
                      <div className="flex gap-2">
                        <button 
                          onClick={handleSave}
                          className="w-14 h-14 glass rounded-2xl flex items-center justify-center text-slate-900 dark:text-white active:scale-95 transition-transform backdrop-blur-md"
                        >
                          <Save size={20} />
                        </button>
                        <button 
                          onClick={handleShare}
                          className="w-14 h-14 glass rounded-2xl flex items-center justify-center text-slate-900 dark:text-white active:scale-95 transition-transform backdrop-blur-md"
                        >
                          <Share2 size={20} />
                        </button>
                      </div>
                    </div>
                  </div>
                </div>
              </>
            ) : (
              <div className="glass-card relative overflow-hidden rounded-2xl">
                <div className="flex flex-col gap-4 relative z-10">
                  <div className="grid grid-cols-2 gap-4">
                    <div 
                      className="flex flex-col group cursor-pointer p-2 -m-2 rounded-xl active:bg-black/5 dark:active:bg-white/5 transition-colors" 
                      onClick={() => setRateInfoModal({ show: true, type: 'nominal' })}
                    >
                      <div className="flex items-center gap-1 h-[14px]">
                        <span className="text-[10px] uppercase text-slate-500 dark:text-slate-400 font-bold tracking-widest group-hover:text-skin-primary transition-colors leading-none">Номинальная ставка</span>
                        <InfoIcon size={12} className="text-skin-primary transition-colors" />
                      </div>
                      <span className="text-2xl font-bold text-skin-primary tracking-tighter leading-none mt-1">
                        {inputs.rate.toFixed(2)}%
                      </span>
                    </div>
                    {results.effectiveRate !== undefined && (
                      <div 
                        className="flex flex-col group cursor-pointer p-2 -m-2 rounded-xl active:bg-black/5 dark:active:bg-white/5 transition-colors" 
                        onClick={() => setRateInfoModal({ show: true, type: 'effective' })}
                      >
                        <div className="flex items-center gap-1 h-[14px]">
                          <span className="text-[10px] uppercase text-slate-500 dark:text-slate-400 font-bold tracking-widest group-hover:text-skin-primary transition-colors leading-none">Эффективная ставка</span>
                          <InfoIcon size={12} className="text-skin-primary transition-colors" />
                        </div>
                        <span className="text-2xl font-bold text-skin-primary tracking-tighter leading-none mt-1">
                          {results.effectiveRate.toFixed(2)}%
                        </span>
                      </div>
                    )}
                  </div>

                  <div className="h-px bg-black/5 dark:bg-white/10 w-full my-1"></div>

                  <div className="grid grid-cols-2 gap-4">
                    <div className="flex flex-col">
                      <span className="text-[10px] uppercase text-slate-500 dark:text-slate-400 font-bold tracking-widest h-[14px] leading-none">Сумма кредита</span>
                      <span className="text-md font-semibold text-slate-700 dark:text-white leading-none mt-1">
                        {formatCurrencyCompact(inputs.amount)} ₽
                      </span>
                    </div>
                    <div className="flex flex-col">
                      <span className="text-[10px] uppercase text-slate-500 dark:text-slate-400 font-bold tracking-widest h-[14px] leading-none">Срок кредита</span>
                      <span className="text-md font-semibold text-slate-700 dark:text-white leading-none mt-1">
                        {inputs.term} мес.
                      </span>
                    </div>
                  </div>

                  <div className="grid grid-cols-2 gap-4">
                    <div className="flex flex-col">
                      <span className="text-[10px] uppercase text-slate-500 dark:text-slate-400 font-bold tracking-widest h-[14px] leading-none">Тип платежа</span>
                      <span className="text-xs font-semibold text-slate-700 dark:text-white leading-none mt-1">
                        {inputs.paymentType === 'annuity' ? 'аннуитетный' : 'дифференцированный'}
                      </span>
                    </div>
                    <div className="flex flex-col">
                      <span className="text-[10px] uppercase text-slate-500 dark:text-slate-400 font-bold tracking-widest h-[14px] leading-none">
                        {inputs.paymentType === 'annuity' ? 'Платеж' : 'Первый платёж'}
                      </span>
                      <span className="text-md font-semibold text-slate-700 dark:text-white leading-none mt-1">
                        {Array.isArray(results.monthlyPayment) 
                          ? `${formatCurrencyCompact(results.monthlyPayment[0])} ₽`
                          : `${formatCurrencyCompact(results.monthlyPayment)} ₽`}
                      </span>
                    </div>
                  </div>

                  <div className="grid grid-cols-2 gap-4">
                    <div className="flex flex-col">
                      <span className="text-[10px] uppercase text-slate-500 dark:text-slate-400 font-bold tracking-widest h-[14px] leading-none">Переплата</span>
                      <span className="text-md font-semibold text-skin-primary leading-none mt-1">
                        {formatCurrencyCompact(results.totalInterest)} ₽
                      </span>
                    </div>
                    <div className="flex flex-col">
                      <span className="text-[10px] uppercase text-slate-500 dark:text-slate-400 font-bold tracking-widest h-[14px] leading-none">Сумма возврата</span>
                      <span className="text-md font-semibold text-skin-primary leading-none mt-1">
                        {formatCurrencyCompact(results.totalRepayment)} ₽
                      </span>
                    </div>
                  </div>

                  <div className="flex items-center gap-3 mt-2">
                    <button 
                      onClick={() => setShowSchedule(true)}
                      className="flex-1 h-14 bg-skin-primary text-white rounded-2xl flex items-center justify-center text-sm font-bold active:scale-95 transition-transform shadow-lg shadow-skin-primary/20"
                    >
                      График платежей
                    </button>
                    <div className="flex gap-2">
                      <button 
                        onClick={handleSave}
                        className="w-14 h-14 glass rounded-2xl flex items-center justify-center text-slate-900 dark:text-white active:scale-95 transition-transform backdrop-blur-md"
                      >
                        <Save size={20} />
                      </button>
                      <button 
                        onClick={handleShare}
                        className="w-14 h-14 glass rounded-2xl flex items-center justify-center text-slate-900 dark:text-white active:scale-95 transition-transform backdrop-blur-md"
                      >
                        <Share2 size={20} />
                      </button>
                    </div>
                  </div>
                </div>
              </div>
            )}
            
            <div 
              onClick={scrollToTop}
              className="flex flex-col items-center gap-2 mt-8 mb-8 cursor-pointer group active:scale-95 transition-transform"
            >
              <div className="w-10 h-10 rounded-full border-2 border-slate-400 dark:border-slate-500 flex items-center justify-center group-hover:border-skin-primary transition-colors">
                <ArrowUp size={20} className="text-slate-400 dark:text-slate-500 group-hover:text-skin-primary transition-colors" />
              </div>
              <span className="text-[10px] font-bold text-slate-400 dark:text-slate-500 group-hover:text-skin-primary transition-colors uppercase tracking-widest">
                к параметрам кредита
              </span>
            </div>
          </div>
        )}

      <ScheduleModal 
        isOpen={showSchedule}
        onClose={() => setShowSchedule(false)}
        title="График платежей"
        type="loan"
        data={results?.schedule || []}
        inputs={inputs}
        results={results}
        calculationMode={calculationMode}
      />
      
      {/* Rate info modal */}
      {rateInfoModal.show && (
        <div className="fixed inset-0 z-[60] flex items-center justify-center p-4 bg-slate-900/40 backdrop-blur-sm">
          <div 
            className="bg-white dark:bg-slate-900 rounded-2xl w-full max-w-lg overflow-hidden shadow-2xl border border-slate-200/50 dark:border-white/10"
          >
            <div className="p-5 border-b border-slate-100 dark:border-white/5 flex items-center justify-between">
              <h3 className="text-lg font-bold text-slate-800 dark:text-white">
                {rateInfoModal.type === 'nominal' ? 'Номинальная ставка' : 'Эффективная ставка'}
              </h3>
              <button 
                onClick={() => setRateInfoModal({ ...rateInfoModal, show: false })}
                className="p-2 hover:bg-slate-100 dark:hover:bg-white/5 rounded-full transition-colors"
              >
                <X size={20} className="text-slate-500" />
              </button>
            </div>
            
            <div className="p-6 overflow-y-auto max-h-[70vh] flex flex-col gap-6">
              {rateInfoModal.type === 'nominal' ? (
                <>
                  <section className="flex flex-col gap-2">
                    <h4 className="text-[10px] uppercase font-bold text-skin-primary tracking-widest">Экскурс</h4>
                    <p className="text-sm text-slate-600 dark:text-slate-400 leading-relaxed">
                      Номинальная процентная ставка — это годовая процентная ставка, указанная в договоре кредитования. 
                      Она служит базой для начисления процентов за каждый расчетный период, но не учитывает 
                      капитализацию и дополнительные расходы (комиссии).
                    </p>
                  </section>
                  
                  <section className="flex flex-col gap-2">
                    <h4 className="text-[10px] uppercase font-bold text-skin-primary tracking-widest">Как рассчитывается</h4>
                    <p className="text-sm text-slate-600 dark:text-slate-400 leading-relaxed">
                      Рассчитывается как произведение ставки за один период начисления на количество таких периодов в году.
                      Например, если ставка в месяц составляет 1%, номинальная годовая ставка будет 12%.
                    </p>
                    <div className="bg-slate-50 dark:bg-white/5 p-3 rounded-lg border border-slate-100 dark:border-white/10 mt-1">
                      <code className="text-xs font-mono text-skin-primary font-bold">J = j / m</code>
                      <p className="text-[10px] text-slate-500 mt-1 italic">где J — ставка за период, j — номинальная ставка, m — число периодов в году.</p>
                    </div>
                  </section>

                  <section className="flex flex-col gap-2">
                    <h4 className="text-[10px] uppercase font-bold text-skin-primary tracking-widest">Ваш расчет</h4>
                    <div className="bg-skin-primary/5 p-4 rounded-xl border border-skin-primary/20 flex flex-col gap-4">
                      <div className="flex flex-col gap-2">
                        <p className="text-[11px] font-bold text-slate-500 uppercase tracking-wider">Шаг 1: Определение базовой ставки</p>
                        <p className="text-sm text-slate-700 dark:text-slate-300">
                          Вы указали годовую ставку <span className="text-skin-primary font-bold">{inputs.rate.toFixed(2)}%</span>.
                        </p>
                      </div>
                      
                      <div className="flex flex-col gap-2">
                        <p className="text-[11px] font-bold text-slate-500 uppercase tracking-wider">Шаг 2: Расчет ставки за период</p>
                        <p className="text-sm text-slate-700 dark:text-slate-300">
                          Кредит рассчитывается помесячно, поэтому годовая ставка делится на 12 месяцев:
                        </p>
                        <div className="bg-white/50 dark:bg-black/20 p-2 rounded border border-skin-primary/10 font-mono text-xs">
                          {inputs.rate.toFixed(2)}% / 12 = {(inputs.rate / 12).toFixed(4)}% в месяц
                        </div>
                      </div>

                      <div className="flex flex-col gap-1 mt-1 border-t border-skin-primary/10 pt-3">
                        <p className="text-xs text-slate-500 italic">
                          Эта ставка используется как константа для начисления процентов на остаток долга в каждом из {inputs.term} месяцев.
                        </p>
                      </div>
                    </div>
                  </section>
                </>
              ) : (
                <>
                  <section className="flex flex-col gap-2">
                    <h4 className="text-[10px] uppercase font-bold text-skin-primary tracking-widest">Экскурс</h4>
                    <p className="text-sm text-slate-600 dark:text-slate-400 leading-relaxed">
                      Эффективная процентная ставка (ЭПС) — это показатель, отражающий реальную годовую стоимость кредита. 
                      Она учитывает не только номинальный процент, но и эффект капитализации (сложные проценты), 
                      а также все выплаченные комиссии и сборы.
                    </p>
                  </section>
                  
                  <section className="flex flex-col gap-2">
                    <h4 className="text-[10px] uppercase font-bold text-skin-primary tracking-widest">Как рассчитывается</h4>
                    <p className="text-sm text-slate-600 dark:text-slate-400 leading-relaxed">
                      Эффективная ставка определяется на основе уравнения денежных потоков (аналог IRR). 
                      Она учитывает временную стоимость денег: сумма полученного кредита за вычетом комиссий 
                      должна быть равна приведенной стоимости всех будущих платежей.
                    </p>
                    <div className="bg-slate-50 dark:bg-white/5 p-3 rounded-lg border border-slate-100 dark:border-white/10 mt-1">
                      <code className="text-[10px] font-mono text-skin-primary font-bold">Prec = Σ [Mk / (1+r)^k]</code>
                      <p className="text-[9px] text-slate-500 mt-1 italic">Эффективная ставка = ( (1+r)^12 - 1 ) * 100%</p>
                    </div>
                  </section>

                  <section className="flex flex-col gap-2">
                    <h4 className="text-[10px] uppercase font-bold text-skin-primary tracking-widest">Ваш расчет по шагам</h4>
                    <div className="bg-skin-primary/5 p-4 rounded-xl border border-skin-primary/20 flex flex-col gap-4">
                      <div className="flex flex-col gap-2">
                        <p className="text-[11px] font-bold text-slate-500 uppercase tracking-wider">Шаг 1: Чистая сумма кредита</p>
                        <p className="text-sm text-slate-700 dark:text-slate-300">
                          Вычитаем комиссии из суммы кредита ({formatCurrency(inputs.amount)}):
                        </p>
                        <div className="bg-white/50 dark:bg-black/20 p-2 rounded border border-skin-primary/10 font-mono text-xs">
                          {formatCurrency(inputs.amount)} - {formatCurrency(results?.totalCommission || 0)} = {formatCurrency(inputs.amount - (results?.totalCommission || 0))}
                        </div>
                      </div>

                      <div className="flex flex-col gap-2">
                        <p className="text-[11px] font-bold text-slate-500 uppercase tracking-wider">Шаг 2: Поиск доходности (IRR)</p>
                        <ul className="text-[11px] text-slate-600 dark:text-slate-400 list-disc pl-4 flex flex-col gap-1">
                          {inputs.paymentType === 'annuity' ? (
                            <li>Берутся все {inputs.term} платежей по {formatCurrency(results?.monthlyPayment as number)}</li>
                          ) : (
                            <li>Берутся все {inputs.term} дифференцированных платежей (от {formatCurrency((results?.monthlyPayment as number[])[0])} до {formatCurrency((results?.monthlyPayment as number[])[inputs.term-1])})</li>
                          )}
                          <li>Методом итераций находится ставка <span className="font-bold">r</span>, при которой сумма всех этих платежей, дисконтированных по ставке <span className="font-bold">r</span>, в точности равна чистой сумме из Шага 1.</li>
                        </ul>
                      </div>

                      <div className="flex flex-col gap-2">
                        <p className="text-[11px] font-bold text-slate-500 uppercase tracking-wider">Шаг 3: Годовая эффективная ставка</p>
                        {(() => {
                          const eRate = results?.effectiveRate || 0;
                          const rValue = Math.pow(eRate / 100 + 1, 1 / 12) - 1;
                          const rStr = rValue.toFixed(6);
                          return (
                            <>
                              <p className="text-sm text-slate-700 dark:text-slate-300">
                                Найденная месячная ставка <span className="font-bold">r = {rStr}</span> переводится в годовую с учетом капитализации:
                              </p>
                              <div className="bg-white/50 dark:bg-black/20 p-2 rounded border border-skin-primary/10 font-mono text-xs">
                                ((1 + {rStr})^12 - 1) * 100% = <span className="text-skin-primary font-bold">{eRate.toFixed(2)}%</span>
                              </div>
                            </>
                          );
                        })()}
                      </div>
                    </div>
                  </section>
                </>
              )}
            </div>
            
            <div className="p-4 bg-slate-50 dark:bg-white/5 border-t border-slate-100 dark:border-white/5 text-center">
              <button 
                onClick={() => setRateInfoModal({ ...rateInfoModal, show: false })}
                className="w-full py-3 bg-skin-primary text-white rounded-xl font-bold shadow-lg shadow-skin-primary/30 hover:opacity-90 transition-all active:scale-[0.98]"
              >
                ПОНЯТНО
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
};
