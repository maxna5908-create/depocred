/**
 * @license
 * SPDX-License-Identifier: Apache-2.0
 */

import React from 'react';
import { motion, AnimatePresence } from 'motion/react';
import { AlertCircle, Info, CheckCircle, X } from 'lucide-react';

export const Toast: React.FC<{ 
  message: string; 
  isVisible: boolean; 
  onClose: () => void;
  type?: 'error' | 'success' | 'info';
}> = ({ message, isVisible, onClose, type = 'error' }) => {
  React.useEffect(() => {
    if (isVisible) {
      const timer = setTimeout(onClose, 3000);
      return () => clearTimeout(timer);
    }
  }, [isVisible, onClose]);

  const styles = {
    error: {
      border: 'border-red-500',
      bg: 'bg-red-500/10',
      icon: <AlertCircle size={20} className="text-red-500" />
    },
    success: {
      border: 'border-emerald-500',
      bg: 'bg-emerald-500/10',
      icon: <AlertCircle size={20} className="text-emerald-500" /> // Using AlertCircle as placeholder or I should import more
    },
    info: {
      border: 'border-blue-500',
      bg: 'bg-blue-500/10',
      icon: <Info size={20} className="text-blue-500" />
    }
  };

  const currentStyle = styles[type];

  return (
    <AnimatePresence>
      {isVisible && (
        <motion.div
          initial={{ y: -100, opacity: 0 }}
          animate={{ y: 'calc(env(safe-area-inset-top, 0px) + 12px)', opacity: 1 }}
          exit={{ y: -100, opacity: 0 }}
          className="fixed top-0 left-1/2 -translate-x-1/2 z-[200] w-full max-w-lg md:max-w-2xl lg:max-w-[800px] px-4 pointer-events-none"
        >
          <div className={`glass py-4 px-6 rounded-2xl border-l-4 ${currentStyle.border} bg-white/95 dark:bg-slate-900/95 dark:border-white/10 shadow-2xl flex items-center gap-4 pointer-events-auto`}>
            <div className={`${currentStyle.bg} p-2 rounded-lg`}>
              {currentStyle.icon}
            </div>
            <span className="text-sm text-slate-800 dark:text-white tracking-tight">
              {message}
            </span>
          </div>
        </motion.div>
      )}
    </AnimatePresence>
  );
};

interface GlassCardProps {
  children: React.ReactNode;
  className?: string;
  id?: string;
  style?: React.CSSProperties;
}

export const GlassCard: React.FC<GlassCardProps> = ({ children, className = '', id, style }) => {
  return (
    <div 
      id={id}
      className={`glass-card ${className}`}
      style={style}
    >
      {children}
    </div>
  );
};

interface NumInputProps {
  label: string;
  value: number;
  onChange: (val: number) => void;
  prefix?: string;
  suffix?: string;
  placeholder?: string;
  id?: string;
  onlyInteger?: boolean;
  disabled?: boolean;
  maxBeforeDecimal?: number;
  maxTotal?: number;
  isLast?: boolean;
  placeholderClassName?: string;
  isCalculated?: boolean;
  onClear?: () => void;
  showClear?: boolean;
  onFocus?: (e: React.FocusEvent<HTMLInputElement>) => void;
  onBlur?: (e: React.FocusEvent<HTMLInputElement>) => void;
}

export const NumInput = React.forwardRef<HTMLInputElement, NumInputProps>(({ 
  label, value, onChange, prefix, suffix, placeholder, id, onlyInteger, disabled, 
  maxBeforeDecimal, maxTotal, isLast, placeholderClassName, isCalculated, onClear, showClear, onFocus, onBlur
}, ref) => {
  const localInputRef = React.useRef<HTMLInputElement>(null);
  const inputRef = (ref as React.RefObject<HTMLInputElement>) || localInputRef;
  const measureRef = React.useRef<HTMLSpanElement>(null);
  const [suffixOffset, setSuffixOffset] = React.useState(0);

  const formatValue = (val: number) => {
    if (val === 0) return '';
    const parts = val.toString().split('.');
    parts[0] = parts[0].replace(/\B(?=(\d{3})+(?!\d))/g, " ");
    return parts.join('.');
  };

  const [displayValue, setDisplayValue] = React.useState(formatValue(value));

  // Measure text width to position suffix
  React.useLayoutEffect(() => {
    if (measureRef.current) {
      setSuffixOffset(measureRef.current.offsetWidth);
    }
  }, [displayValue]);

  React.useEffect(() => {
    // Only update if it's not a user typing session that we are currently handling
    // or if the underlying numeric value changed externally.
    const currentFormatted = formatValue(value);
    if (currentFormatted !== displayValue && !inputRef.current?.matches(':focus')) {
      setDisplayValue(currentFormatted);
    }
  }, [value]);

  const handleChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    const input = e.target;
    let inputVal = input.value;
    let raw = inputVal.replace(/\s/g, '').replace(',', '.');
    const startPos = input.selectionStart || 0;
    
    // Allow empty input
    if (raw === '') {
      onChange(0);
      setDisplayValue('');
      return;
    }

    // Validation logic
    if (onlyInteger) {
      if (!/^\d+$/.test(raw)) return;
    } else {
      if (!/^\d*\.?\d*$/.test(raw)) return;
    }

    // New constraints
    const partsCheck = raw.split('.');
    if (maxBeforeDecimal && partsCheck[0].length > maxBeforeDecimal) return;
    if (maxTotal && raw.replace('.', '').length > maxTotal) return;

    // Decimal constraints (always 2 max)
    if (raw.includes('.')) {
      const parts = raw.split('.');
      if (parts[1] && parts[1].length > 2) {
        raw = `${parts[0]}.${parts[1].substring(0, 2)}`;
      }
    }
    
    if (raw.replace('.', '').length > 15) return;

    // Apply formatting to the raw string to preserve trailing dots/separators
    const parts = raw.split('.');
    parts[0] = parts[0].replace(/\B(?=(\d{3})+(?!\d))/g, " ");
    const formatted = parts.join('.');
    
    // Maintain cursor position relative to digits
    const charsBefore = inputVal.substring(0, startPos).replace(/\s/g, '').length;
    
    setDisplayValue(formatted);
    
    const numericValue = Number(raw);
    if (!isNaN(numericValue)) {
      onChange(numericValue);
    }

    // Schedule cursor fix
    requestAnimationFrame(() => {
      if (inputRef.current) {
        const val = inputRef.current.value;
        let newPos = val.length; 
        let count = 0;
        for (let i = 0; i < val.length; i++) {
          if (val[i] !== ' ') {
            count++;
          }
          if (count === charsBefore) {
            newPos = i + 1;
            break;
          }
        }
        inputRef.current.setSelectionRange(newPos, newPos);
      }
    });
  };

  const handleBlur = (e: React.FocusEvent<HTMLInputElement>) => {
    setDisplayValue(formatValue(value));
    if (onBlur) onBlur(e);
  };

  const handleKeyDown = (e: React.KeyboardEvent<HTMLInputElement>) => {
    if (e.key === 'Enter') {
      if (isLast) {
        inputRef.current?.blur();
      } else {
        const focusable = Array.from(document.querySelectorAll('input, select, textarea, button, [tabindex]:not([tabindex="-1"])')) as HTMLElement[];
        const index = focusable.indexOf(inputRef.current!);
        if (index > -1 && focusable[index + 1]) {
          focusable[index + 1].focus();
          e.preventDefault();
        }
      }
    }
  };

  return (
    <div className="flex flex-col gap-1.5 w-full" id={id}>
      <label className="text-[9px] font-semibold text-slate-500 dark:text-slate-300 uppercase tracking-wider ml-1">
        {label}
      </label>
      <div className="relative group flex items-center">
        {prefix && (
          <span className="absolute left-4 top-1/2 -translate-y-1/2 text-skin-primary font-medium z-10">
            {prefix}
          </span>
        )}
        
        <input
          ref={inputRef}
          type="text"
          inputMode={onlyInteger ? "numeric" : "decimal"}
          autoComplete="off"
          autoCorrect="off"
          autoCapitalize="off"
          spellCheck="false"
          value={displayValue}
          onChange={handleChange}
          onFocus={onFocus}
          onBlur={handleBlur}
          onKeyDown={handleKeyDown}
          placeholder={placeholder}
          disabled={disabled}
          tabIndex={disabled ? -1 : 0}
          enterKeyHint={isLast ? "done" : "next"}
          className={`
            w-full bg-black/5 dark:bg-white/[0.08] border border-black/10 dark:border-white/20
            focus:border-skin-primary focus:ring-0
            rounded-xl py-2.5 transition-all outline-none text-slate-900 dark:text-white font-semibold text-base
            ${placeholderClassName || 'placeholder:text-slate-400 dark:placeholder:text-slate-500 placeholder:font-medium placeholder:text-[16px]'}
            ${prefix ? 'pl-8' : 'pl-4'}
            pr-12
            ${disabled ? 'opacity-50 grayscale cursor-not-allowed' : ''}
            ${isCalculated ? 'text-skin-primary dark:text-skin-primary border-skin-primary/40 bg-skin-primary/5' : ''}
          `}
        />

        {showClear && value !== 0 && !disabled && (
          <div
            role="button"
            onClick={onClear}
            tabIndex={-1}
            className="absolute right-3 top-1/2 -translate-y-1/2 p-1.5 rounded-full hover:bg-black/5 dark:hover:bg-white/10 text-skin-primary hover:opacity-80 transition-colors cursor-pointer"
          >
            <X size={14} />
          </div>
        )}

        {displayValue && suffix && (
          <span 
            className="absolute pointer-events-none text-slate-400 dark:text-slate-400 font-medium text-xs bottom-[13px]"
            style={{ 
              left: `${suffixOffset + (prefix ? 32 : 16) + 4}px`,
              transition: 'left 0.1s ease-out'
            }}
          >
            {suffix}
          </span>
        )}

        {/* Measuring helper */}
        <span 
          ref={measureRef} 
          className="absolute opacity-0 pointer-events-none whitespace-pre font-semibold text-base"
          style={{ visibility: 'hidden' }}
        >
          {displayValue}
        </span>
      </div>
    </div>
  );
});

export const Button: React.FC<{
  children: React.ReactNode;
  onClick?: () => void;
  variant?: 'primary' | 'secondary' | 'ghost';
  className?: string;
  id?: string;
  disabled?: boolean;
}> = ({ children, onClick, variant = 'primary', className = '', id, disabled }) => {
  const base = "px-6 py-3.5 rounded-xl font-bold transition-all active:scale-95 flex items-center justify-center gap-2 overflow-hidden relative group";
  const variants = {
    primary: "bg-skin-primary text-white shadow-lg shadow-skin-primary/20 hover:brightness-110",
    secondary: "glass text-slate-900 dark:text-white hover:bg-black/5 dark:hover:bg-white/10",
    ghost: "bg-transparent text-slate-500 dark:text-white hover:text-slate-900 dark:hover:text-white hover:bg-black/5 dark:hover:bg-white/5",
  };

  const disabledStyles = disabled ? "opacity-40 grayscale cursor-not-allowed pointer-events-none active:scale-100" : "";

  return (
    <button 
      id={id} 
      onClick={onClick} 
      disabled={disabled}
      className={`${base} ${variants[variant]} ${disabledStyles} ${className}`}
    >
      {children}
    </button>
  );
};
