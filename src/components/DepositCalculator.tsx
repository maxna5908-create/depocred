/**
 * @license
 * SPDX-License-Identifier: Apache-2.0
 */

import React, { useState, useEffect, useRef } from 'react';
import { DepositInputs, DepositResults, HistoryItem, CapitalizationType, AdditionPeriod } from '../types';
import { calculateDeposit, formatCurrency, formatCurrencyCompact } from '../lib/calculators';
import { GlassCard, NumInput, Button, Toast } from './ui/Common';
import { Calculator, Trash2, Save, X, Share2, Calendar, FileText, ArrowUp, Info as InfoIcon } from 'lucide-react';
import { motion, AnimatePresence } from 'motion/react';
import { ScheduleModal } from './ScheduleModal';
import { shareCalculationPDF } from '../lib/pdfGenerator';
import { useTheme } from './ThemeContext';

export const DepositCalculator: React.FC<{ 
  onSave: (item: HistoryItem) => void,
  session: { 
    directInputs: DepositInputs; 
    reverseInputs: DepositInputs; 
    results: DepositResults | null; 
    calculationMode: 'direct' | 'reverse' 
  },
  onSessionChange: (session: { 
    directInputs: DepositInputs; 
    reverseInputs: DepositInputs; 
    results: DepositResults | null; 
    calculationMode: 'direct' | 'reverse' 
  }) => void
}> = ({ onSave, session, onSessionChange }) => {
  const { settings, setShowHints } = useTheme();
  const containerRef = useRef<HTMLDivElement>(null);
  const resultsHeaderRef = useRef<HTMLDivElement>(null);
  const amountRef = useRef<HTMLInputElement>(null);
  const reverseFinalAmountRef = useRef<HTMLInputElement>(null);
  const rateRef = useRef<HTMLInputElement>(null);
  const termRef = useRef<HTMLInputElement>(null);
  const additionRef = useRef<HTMLInputElement>(null);
  const wasCalculated = useRef(false);

  const [toast, setToast] = useState<{show: boolean, message: string, type: 'error' | 'info'}>({
    show: false,
    message: '',
    type: 'error'
  });

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

  const [isReverseDescHidden, setIsReverseDescHidden] = useState(() => getInitialHintState('isDepositReverseDescHidden'));

  useEffect(() => {
    if (settings.showHints) {
      setIsReverseDescHidden(false);
      try {
        if (typeof window !== 'undefined' && window.localStorage) {
          localStorage.removeItem('isDepositReverseDescHidden');
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

  const hideReverseDesc = () => hideHint(setIsReverseDescHidden, 'isDepositReverseDescHidden');

  const [showPeriodModal, setShowPeriodModal] = useState(false);
  const [showCapModal, setShowCapModal] = useState(false);
  const [calculationMode, setCalculationMode] = useState<'direct' | 'reverse'>(session.calculationMode);

  const [directInputs, setDirectInputs] = useState<DepositInputs>(session.directInputs);
  const [reverseInputs, setReverseInputs] = useState<DepositInputs>(session.reverseInputs);
  
  const [inputs, setInputs] = useState<DepositInputs>(
    session.calculationMode === 'direct' ? session.directInputs : session.reverseInputs
  );

  const [results, setResults] = useState<DepositResults | null>(session.results);
  const [showSchedule, setShowSchedule] = useState(false);
  const [last3ManualKeys, setLast3ManualKeys] = useState<string[]>([]);

  const updateLast3 = (key: string) => {
    setLast3ManualKeys(prev => {
      const next = prev.filter(k => k !== key);
      next.push(key);
      if (next.length > 3) next.shift();
      return next;
    });
  };

  // Solvers for the 4 parameters
  const solveForRate = (inps: DepositInputs): number => {
    const target = inps.reverseFinalAmount || 0;
    if (target <= inps.initialAmount) return inps.rate;
    let low = 0, high = 1000, mid = 0;
    for (let i = 0; i < 40; i++) {
      mid = (low + high) / 2;
      const res = calculateDeposit({ ...inps, rate: mid });
      if (res.finalAmount < target) low = mid;
      else high = mid;
    }
    return Math.round(mid * 100) / 100;
  };

  const solveForFinalAmount = (inps: DepositInputs): number => {
    const res = calculateDeposit(inps);
    return Math.round(res.finalAmount);
  };

  const solveForInitialAmount = (inps: DepositInputs): number => {
    const target = inps.reverseFinalAmount || 0;
    if (target <= 0) return inps.initialAmount;
    let low = 0, high = target, mid = 0;
    for (let i = 0; i < 40; i++) {
      mid = (low + high) / 2;
      const res = calculateDeposit({ ...inps, initialAmount: mid });
      if (res.finalAmount < target) low = mid;
      else high = mid;
    }
    return Math.round(mid);
  };

  const solveForTerm = (inps: DepositInputs): number => {
    const target = inps.reverseFinalAmount || 0;
    if (target <= inps.initialAmount) return inps.term;
    let low = 1, high = 600, mid = 0;
    for (let i = 0; i < 40; i++) {
      mid = (low + high) / 2;
      const res = calculateDeposit({ ...inps, term: Math.round(mid) });
      if (res.finalAmount < target) low = mid;
      else high = mid;
    }
    return Math.round(mid);
  };

  useEffect(() => {
    if (calculationMode !== 'reverse') return;

    const { initialAmount, rate, term, reverseFinalAmount } = inputs;
    const values = { initialAmount, rate, term, reverseFinalAmount: reverseFinalAmount || 0 };
    const filledKeys = (Object.keys(values) as Array<keyof typeof values>).filter(k => values[k] > 0);
    
    let targetKey: keyof typeof values | null = null;

    if (filledKeys.length === 3) {
      targetKey = (Object.keys(values) as Array<keyof typeof values>).find(k => values[k] === 0 || !values[k]) || null;
    } else if (filledKeys.length === 4) {
      targetKey = (Object.keys(values) as Array<keyof typeof values>).find(k => !last3ManualKeys.includes(k)) || null;
    }

    if (targetKey && !last3ManualKeys.includes(targetKey)) {
        let calculatedValue = 0;
        if (targetKey === 'rate') calculatedValue = solveForRate(inputs);
        else if (targetKey === 'reverseFinalAmount') calculatedValue = solveForFinalAmount(inputs);
        else if (targetKey === 'initialAmount') calculatedValue = solveForInitialAmount(inputs);
        else if (targetKey === 'term') calculatedValue = solveForTerm(inputs);

        if (calculatedValue > 0 && Math.abs(calculatedValue - values[targetKey]) > (targetKey === 'rate' ? 0.005 : 0.5)) {
          setInputs(prev => ({ ...prev, [targetKey!]: calculatedValue }));
        }
    }
  }, [inputs.initialAmount, inputs.rate, inputs.term, inputs.reverseFinalAmount, inputs.monthlyAddition, inputs.additionPeriod, inputs.capitalization, calculationMode, last3ManualKeys]);

  useEffect(() => {
    // Reset results on mount to clear result panel when switching back to this tool
    setResults(null);
  }, []);

  useEffect(() => {
    setResults(null);
    if (calculationMode === 'direct') {
      setInputs(directInputs);
    } else {
      setInputs(reverseInputs);
    }
  }, [calculationMode]);

  // Update session in parent when local state changes
  useEffect(() => {
    onSessionChange({ directInputs, reverseInputs, results, calculationMode });
  }, [directInputs, reverseInputs, results, calculationMode, onSessionChange]);

  useEffect(() => {
    if (calculationMode === 'direct') {
      setDirectInputs(inputs);
    } else {
      setReverseInputs(inputs);
    }
  }, [inputs]);

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
          
          // Position relative to current scroll
          const targetScroll = scrollContainer.scrollTop + (elementRect.top - containerRect.top) - 22;
          
          scrollContainer.scrollTo({
            top: Math.max(0, targetScroll),
            behavior: behavior as ScrollBehavior
          });

          wasCalculated.current = false;
        }, isNewCalc ? 150 : 50);
      }
    }
  }, [results]);

  const solveForDepositRate = (inps: DepositInputs): number => {
    const target = inps.reverseFinalAmount || 0;
    if (target <= inps.initialAmount) return 0;

    let low = 0;
    let high = 1000; 
    let mid = 0;

    for (let i = 0; i < 30; i++) {
      mid = (low + high) / 2;
      const res = calculateDeposit({ ...inps, rate: mid });
      if (res.finalAmount < target) {
        low = mid;
      } else {
        high = mid;
      }
    }
    return Math.round(mid * 100) / 100;
  };

  const handleCalculate = () => {
    if (inputs.initialAmount <= 0) {
      setToast({ show: true, message: "Заполните все необходимые поля для расчета депозита", type: 'error' });
      amountRef.current?.focus();
      return;
    }

    if (calculationMode === 'direct') {
      if (inputs.rate <= 0) {
        setToast({ show: true, message: "Заполните все необходимые поля для расчета депозита", type: 'error' });
        rateRef.current?.focus();
        return;
      }
    } else {
      if ((inputs.reverseFinalAmount || 0) <= inputs.initialAmount) {
        setToast({ show: true, message: "Итоговая сумма должна быть больше суммы депозита", type: 'error' });
        reverseFinalAmountRef.current?.focus();
        return;
      }
    }

    if (inputs.term <= 0) {
      setToast({ show: true, message: "Заполните все необходимые поля для расчета депозита", type: 'error' });
      termRef.current?.focus();
      return;
    }

    let finalInputs = { ...inputs };
    
    if (calculationMode === 'reverse') {
      const solvedRate = solveForDepositRate(inputs);
      finalInputs.rate = solvedRate;
    }

    const res = calculateDeposit(finalInputs);
    setInputs(finalInputs);
    setResults(res);
    wasCalculated.current = true;
  };

  const handleSave = () => {
    if (!results) return;
    const historyItem: HistoryItem = {
      id: Math.random().toString(36).substr(2, 9),
      type: 'deposit',
      timestamp: Date.now(),
      inputs: { ...inputs },
      results: { ...results },
      label: `Депозит ${formatCurrency(inputs.initialAmount)}`
    };
    onSave(historyItem);
    setToast({ show: true, message: "Текущий расчет по депозиту сохранен в историю расчетов", type: 'info' });
  };

  const scrollToTop = () => {
    const scrollContainer = containerRef.current?.closest('.overflow-y-auto');
    if (scrollContainer) {
      scrollContainer.scrollTo({ top: 0, behavior: 'smooth' });
    }
  };

  const handleReset = () => {
    setInputs({
      initialAmount: 0,
      monthlyAddition: 0,
      rate: 0,
      term: 0,
      capitalization: 'monthly',
      additionPeriod: 'none'
    });
    setResults(null);
    setLast3ManualKeys([]);
    const scrollContainer = containerRef.current?.closest('.overflow-y-auto');
    if (scrollContainer) {
      scrollContainer.scrollTo({ top: 0, behavior: 'smooth' });
    }
  };

  const additionPeriods: { val: AdditionPeriod; label: string }[] = [
    { val: 'none', label: 'Нет' },
    { val: 'monthly', label: 'Раз в месяц' },
    { val: 'quarterly', label: 'Раз в квартал' },
    { val: 'semi-yearly', label: 'Раз в полгода' },
    { val: 'yearly', label: 'Раз в год' },
  ];

  const capitalizationOptions: { val: CapitalizationType; label: string }[] = [
    { val: 'none', label: 'В конце срока' },
    { val: 'daily', label: 'Ежедневно' },
    { val: 'weekly', label: 'Еженедельно' },
    { val: 'monthly', label: 'Ежемесячно' },
    { val: 'quarterly', label: 'Ежеквартально' },
    { val: 'semi-yearly', label: 'Раз в полгода' },
    { val: 'yearly', label: 'Ежегодно' },
  ];

  const getPeriodLabel = (val: AdditionPeriod) => {
    return additionPeriods.find(p => p.val === val)?.label || 'Нет';
  };

  const getCapLabel = (val: CapitalizationType) => {
    return capitalizationOptions.find(p => p.val === val)?.label || 'Нет';
  };

  const isInputsEmpty = inputs.initialAmount === 0 && inputs.rate === 0 && inputs.term === 0 && inputs.monthlyAddition === 0 && inputs.capitalization === 'none' && inputs.additionPeriod === 'none';
  
  const handleShare = async () => {
    if (!results) return;
    
    setToast({ 
      show: true,
      message: 'Создание PDF отчета...', 
      type: 'info' 
    });

    try {
      const typeStr = 'Deposit';
      const modeStr = calculationMode === 'direct' ? 'Direct' : 'Reverse';
      const dateStr = new Date().toISOString().split('T')[0];
      const timeStr = new Date().toLocaleTimeString('en-GB', { hour12: false }).replace(/:/g, '-');
      const filename = `${typeStr}_${modeStr}_${dateStr}_${timeStr}`;
      
      await shareCalculationPDF(filename, 'deposit', inputs, results, settings.skin);
    } catch (err) {
      console.error('Share error:', err);
      // Fallback to text share if PDF fails
      const text = `Депозитный расчет:
Сумма: ${formatCurrency(inputs.initialAmount)}
Ставка: ${inputs.rate}%
Срок: ${inputs.term} мес.
Итог: ${formatCurrency(results.finalAmount)}
Доход: ${formatCurrency(results.totalInterest)}`;

      if (navigator.share) {
        await navigator.share({
          title: 'Депозитный расчет',
          text: text
        });
      } else {
        await navigator.clipboard.writeText(text);
        setToast({ show: true, message: 'Расчет скопирован в буфер', type: 'info' });
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

      {showPeriodModal && (
        <div className="fixed inset-0 z-[110] flex items-center justify-center p-6 sm:p-0">
          <div 
            onClick={() => setShowPeriodModal(false)}
            className="absolute inset-0 bg-black/60 backdrop-blur-sm"
          />
          <div
            className="relative w-full max-w-sm bg-white dark:bg-slate-900 border border-slate-200 dark:border-white/10 p-6 flex flex-col gap-6 shadow-2xl rounded-[24px] backdrop-blur-xl"
          >
            <div className="flex justify-between items-center">
              <h3 className="text-lg font-bold text-slate-900 dark:text-white tracking-tight underline decoration-skin-primary decoration-2 underline-offset-4">Период пополнения</h3>
              <button 
                onClick={() => setShowPeriodModal(false)}
                className="p-2 hover:bg-black/10 dark:hover:bg-white/10 rounded-full transition-colors"
              >
                <X size={20} className="text-slate-500 dark:text-slate-400" />
              </button>
            </div>
            
            <div className="flex flex-col gap-2">
              {additionPeriods.map(p => (
                <button
                  key={p.val}
                  onClick={() => {
                    setInputs(s => ({ ...s, additionPeriod: p.val }));
                    setShowPeriodModal(false);
                    if (p.val !== 'none') {
                      setTimeout(() => {
                        additionRef.current?.focus();
                      }, 50); // Small delay for state update
                    }
                  }}
                  className={`
                    w-full py-4 px-5 rounded-xl text-left font-semibold flex justify-between items-center
                    ${inputs.additionPeriod === p.val 
                      ? 'bg-skin-primary text-white shadow-md' 
                      : 'bg-black/5 dark:bg-white/[0.05] text-slate-700 dark:text-slate-200 hover:bg-black/10 dark:hover:bg-white/10'}
                  `}
                >
                  {p.label}
                  {inputs.additionPeriod === p.val && <div className="w-2 h-2 rounded-full bg-white" />}
                </button>
              ))}
            </div>
          </div>
        </div>
      )}

      {showCapModal && (
        <div className="fixed inset-0 z-[110] flex items-center justify-center p-6 sm:p-0">
          <div 
            onClick={() => setShowCapModal(false)}
            className="absolute inset-0 bg-black/60 backdrop-blur-sm"
          />
          <div
            className="relative w-full max-w-sm bg-white dark:bg-slate-900 border border-slate-200 dark:border-white/10 p-6 flex flex-col gap-6 shadow-2xl rounded-[24px] backdrop-blur-xl"
          >
            <div className="flex justify-between items-center">
              <h3 className="text-lg font-bold text-slate-900 dark:text-white tracking-tight underline decoration-skin-primary decoration-2 underline-offset-4">Частота капитализации</h3>
              <button 
                onClick={() => setShowCapModal(false)}
                className="p-2 hover:bg-black/10 dark:hover:bg-white/10 rounded-full transition-colors"
              >
                <X size={20} className="text-slate-500 dark:text-slate-400" />
              </button>
            </div>
            
            <div className="flex flex-col gap-2 max-h-[60vh] overflow-y-auto pr-2 custom-scrollbar">
              {capitalizationOptions.map(p => (
                <button
                  key={p.val}
                  onClick={() => {
                    setInputs(s => ({ ...s, capitalization: p.val }));
                    setShowCapModal(false);
                  }}
                  className={`
                    w-full py-4 px-5 rounded-xl text-left font-semibold flex justify-between items-center
                    ${inputs.capitalization === p.val 
                      ? 'bg-skin-primary text-white shadow-md' 
                      : 'bg-black/5 dark:bg-white/[0.05] text-slate-700 dark:text-slate-200 hover:bg-black/10 dark:hover:bg-white/10'}
                  `}
                >
                  {p.label}
                  {inputs.capitalization === p.val && <div className="w-2 h-2 rounded-full bg-white" />}
                </button>
              ))}
            </div>
          </div>
        </div>
      )}

      <div className="flex flex-col">
        <div className="flex h-[42px] w-full relative">
          <button 
            tabIndex={-1}
            onClick={() => setCalculationMode('direct')}
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
            onClick={() => setCalculationMode('reverse')}
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
                Обратный расчет позволяет вычислить номинальную и эффективную ставку исходя из указанных параметров депозита.
              </p>
              <p className="text-[13px] italic leading-tight text-skin-primary dark:text-skin-primary/90 font-medium">
                Вы можете заполнить 3 или 4 любых поля на форме, остальные рассчитаются автоматически.
              </p>
              <p className="text-[9px] font-bold uppercase underline tracking-wider text-skin-primary/60 text-center">
                НАЖМИТЕ ЧТОБЫ СКРЫТЬ ПОДСКАЗКУ
              </p>
            </div>
          )}

          <div className="flex flex-col gap-4">
          <NumInput 
            ref={amountRef}
            label="СУММА ДЕПОЗИТА (₽)" 
            value={inputs.initialAmount} 
            onChange={v => {
              if (results) setResults(null);
              updateLast3('initialAmount');
              setInputs(s => ({ ...s, initialAmount: v }));
            }} 
            suffix="₽"
          />

          {calculationMode === 'reverse' && (
            <NumInput 
              ref={reverseFinalAmountRef}
              label="ИТОГОВАЯ СУММА (₽)" 
              value={inputs.reverseFinalAmount || 0} 
              onChange={v => {
                if (results) setResults(null);
                updateLast3('reverseFinalAmount');
                setInputs(s => ({ ...s, reverseFinalAmount: v }));
              }} 
              suffix="₽"
            />
          )}
          
          <div className="flex gap-4">
            <NumInput 
              ref={rateRef}
              label="СТАВКА (%)" 
              value={inputs.rate} 
              onChange={v => {
                if (results) setResults(null);
                updateLast3('rate');
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
                updateLast3('term');
                setInputs(s => ({ ...s, term: v }));
              }} 
              onlyInteger={true}
              suffix="мес."
              maxTotal={3}
            />
          </div>

          <div className="flex gap-4">
            <div className="flex flex-col gap-1.5 w-full">
              <label className="text-[9px] font-semibold text-slate-500 dark:text-slate-300 uppercase tracking-wider ml-1">
                Пополнение
              </label>
              <button
                tabIndex={-1}
                onClick={() => {
                  if (results) setResults(null);
                  setShowPeriodModal(true);
                }}
                className="w-full bg-black/5 dark:bg-white/[0.08] border border-black/10 dark:border-white/20 rounded-xl py-3 px-4 text-left text-slate-900 dark:text-white font-semibold text-sm hover:border-skin-primary/50 transition-all flex justify-between items-center outline-none focus:ring-0"
              >
                {getPeriodLabel(inputs.additionPeriod)}
                <div className="w-1.5 h-1.5 rounded-full bg-skin-primary" />
              </button>
            </div>
            <NumInput 
              ref={additionRef}
              label="СУММА ВЗНОСА (₽)" 
              value={inputs.monthlyAddition} 
              onChange={v => {
                if (results) setResults(null);
                setInputs(s => ({ ...s, monthlyAddition: v }));
              }} 
              suffix="₽"
              disabled={inputs.additionPeriod === 'none'}
              isLast={true}
            />
          </div>

          <div className="flex flex-col gap-1.5 w-full">
            <label className="text-[9px] font-semibold text-slate-500 dark:text-slate-300 uppercase tracking-wider ml-1">
              Капитализация процентов
            </label>
            <button
              tabIndex={-1}
              onClick={() => {
                if (results) setResults(null);
                setShowCapModal(true);
              }}
              className="w-full bg-black/5 dark:bg-white/[0.08] border border-black/10 dark:border-white/20 rounded-xl py-3 px-4 text-left text-slate-900 dark:text-white font-semibold text-sm hover:border-skin-primary/50 transition-all flex justify-between items-center outline-none focus:ring-0"
            >
              {getCapLabel(inputs.capitalization)}
              <div className="w-1.5 h-1.5 rounded-full bg-skin-primary" />
            </button>
          </div>
        </div>

        <div className="flex gap-2 mt-2">
          <Button variant="primary" onClick={handleCalculate} className="flex-1">
            <Calculator size={18} />
            Рассчитать
          </Button>
          <Button 
            variant="secondary" 
            onClick={handleReset} 
            disabled={isInputsEmpty}
            className="aspect-square !p-0 w-[52px] h-[52px] flex-shrink-0"
          >
            <Trash2 size={18} />
          </Button>
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
            {calculationMode === 'direct' ? 'Прямой расчет по депозиту' : 'Обратный расчет по депозиту'}
          </span>
        </div>
      </div>
    )}

    {results && (
      <div className="glass-card mt-[6px] relative overflow-hidden">
        <div className="absolute -top-8 -right-8 w-24 h-24 bg-skin-primary/20 blur-[40px]"></div>
        <div className="flex flex-col gap-4 relative z-10">
          <div className="flex justify-between items-center">
            <span className="text-sm text-slate-400 dark:text-slate-300">Итоговая сумма</span>
            <div className="flex gap-2">
              <span className="text-[10px] px-2 py-0.5 rounded-lg bg-skin-primary/20 text-skin-primary border border-skin-primary/30 font-medium tracking-tight">
                Депозит
              </span>
            </div>
          </div>

          <div className="text-4xl font-bold text-slate-900 dark:text-white tracking-tighter">
            {formatCurrencyCompact(results.finalAmount)} ₽
          </div>

          <div className="h-px bg-black/5 dark:bg-white/10 w-full my-1"></div>

          <div className="grid grid-cols-2 gap-4">
            <div className="flex flex-col">
              <span className="text-[10px] uppercase text-slate-500 dark:text-slate-400 font-bold tracking-widest h-[14px] leading-none">Депозит</span>
              <span className="text-md font-semibold text-slate-700 dark:text-white leading-none mt-1">
                {formatCurrencyCompact(inputs.initialAmount)} ₽
              </span>
            </div>
            <div className="flex flex-col">
              <span className="text-[10px] uppercase text-slate-500 dark:text-slate-400 font-bold tracking-widest h-[14px] leading-none">Ставка / Срок</span>
              <span className="text-md font-semibold text-slate-700 dark:text-white leading-none mt-1">
                {inputs.rate.toFixed(2)}% | {inputs.term} мес.
              </span>
            </div>
          </div>

          <div className="grid grid-cols-2 gap-4">
            <div className="flex flex-col">
              <span className="text-[10px] uppercase text-slate-500 dark:text-slate-400 font-bold tracking-widest h-[14px] leading-none">Пополнения</span>
              <span className="text-md font-semibold text-slate-700 dark:text-white leading-none mt-1">
                {results.totalAdditions === 0 ? '-' : `${formatCurrencyCompact(results.totalAdditions)} ₽`}
              </span>
            </div>
            <div className="flex flex-col">
              <span className="text-[10px] uppercase text-slate-500 dark:text-slate-400 font-bold tracking-widest h-[14px] leading-none">Доход</span>
              <span className="text-md font-semibold text-skin-primary leading-none mt-1">
                {formatCurrencyCompact(results.totalInterest)} ₽
              </span>
            </div>
          </div>

          <div className="h-px bg-black/5 dark:bg-white/10 w-full"></div>
          <p className="text-[11px] text-skin-primary leading-relaxed italic">
            Доходность с учетом сложного процента составит {(results.totalInterest / (inputs.initialAmount + results.totalAdditions) * 100).toFixed(2)}% за весь период.
          </p>

          <div className="flex items-center gap-3 mt-2">
            <button 
              onClick={() => setShowSchedule(true)}
              className="flex-1 h-14 bg-skin-primary text-white rounded-2xl flex items-center justify-center text-sm font-bold active:scale-95 transition-transform shadow-lg shadow-skin-primary/20"
            >
              График начислений
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

    {results && (
      <div 
        onClick={scrollToTop}
        className="flex flex-col items-center gap-2 mt-2 mb-8 cursor-pointer group active:scale-95 transition-transform"
      >
        <div className="w-10 h-10 rounded-full border-2 border-slate-400 dark:border-slate-500 flex items-center justify-center group-hover:border-skin-primary transition-colors">
          <ArrowUp size={20} className="text-slate-400 dark:text-slate-500 group-hover:text-skin-primary transition-colors" />
        </div>
        <span className="text-[10px] font-bold text-slate-400 dark:text-slate-500 group-hover:text-skin-primary transition-colors uppercase tracking-widest">
          к параметрам депозита
        </span>
      </div>
    )}

      <ScheduleModal 
        isOpen={showSchedule}
        onClose={() => setShowSchedule(false)}
        title="График начислений"
        type="deposit"
        data={results?.schedule || []}
        inputs={inputs}
        results={results}
        calculationMode={calculationMode}
      />
    </div>
  );
};
