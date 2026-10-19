/**
 * @license
 * SPDX-License-Identifier: Apache-2.0
 */

import { 
  LoanInputs, 
  LoanResults, 
  LoanScheduleItem,
  DepositInputs, 
  DepositResults, 
  DepositScheduleItem,
  CapitalizationType 
} from '../types';

/**
 * Loan Calculation (Annuity & Differentiated)
 */
export function calculateLoan(inputs: LoanInputs): LoanResults {
  const { amount, rate, term, fixedCommission, percentCommission, paymentType, downPayment = 0 } = inputs;
  const P = Math.max(0, amount - downPayment);
  const i = rate / 100 / 12; // Monthly rate
  const n = term;
  const schedule: LoanScheduleItem[] = [];

  if (paymentType === 'annuity') {
    // A = P * (i * (1 + i)^n) / ((1 + i)^n - 1)
    const monthlyPayment = i > 0 
      ? P * (i * Math.pow(1 + i, n)) / (Math.pow(1 + i, n) - 1)
      : P / n;
    
    let remainingBalance = P;
    let totalInterest = 0;

    for (let month = 1; month <= n; month++) {
      const interestForMonth = remainingBalance * i;
      const principalForMonth = monthlyPayment - interestForMonth;
      remainingBalance -= principalForMonth;
      totalInterest += interestForMonth;

      schedule.push({
        month,
        payment: monthlyPayment,
        principal: principalForMonth,
        interest: interestForMonth,
        remainingBalance: Math.max(0, remainingBalance)
      });
    }

    const baseTotal = monthlyPayment * n;
    const commissionAmount = fixedCommission + (P * percentCommission / 100);
    const totalRepayment = baseTotal;
    
    return {
      monthlyPayment,
      totalInterest: totalInterest + commissionAmount,
      totalCommission: commissionAmount,
      totalRepayment,
      overpaymentPercent: ((totalInterest + commissionAmount) / P) * 100,
      amountReceived: P - commissionAmount,
      schedule,
      effectiveRate: (() => {
        const Prec = P - commissionAmount;
        if (Prec <= 0 || monthlyPayment <= 0) return (Math.pow(1 + i, 12) - 1) * 100;
        
        // Solve Prec = M * (1 - (1+r)^-n) / r
        let r = i > 0 ? i : 0.01;
        for (let j = 0; j < 20; j++) {
          const r_term = Math.pow(1 + r, -n);
          const f = monthlyPayment * (1 - r_term) / r - Prec;
          const df = monthlyPayment * ( (1 - r_term)/(-r*r) + (n * Math.pow(1+r, -n-1))/r );
          const nextR = r - f / df;
          if (Math.abs(nextR - r) < 0.000001) {
            r = nextR;
            break;
          }
          r = nextR;
        }
        return (Math.pow(1 + Math.max(0, r), 12) - 1) * 100;
      })()
    };
  } else {
    // Differentiated
    const monthlyPrincipal = P / n;
    const payments: number[] = [];
    let remainingBalance = P;
    let totalInterest = 0;

    for (let month = 1; month <= n; month++) {
      const interestForMonth = remainingBalance * i;
      const payment = monthlyPrincipal + interestForMonth;
      payments.push(payment);
      totalInterest += interestForMonth;
      remainingBalance -= monthlyPrincipal;

      schedule.push({
        month,
        payment,
        principal: monthlyPrincipal,
        interest: interestForMonth,
        remainingBalance: Math.max(0, remainingBalance)
      });
    }

    const commissionAmount = fixedCommission + (P * percentCommission / 100);
    const totalRepayment = P + totalInterest;

    return {
      monthlyPayment: payments,
      totalInterest: totalInterest + commissionAmount,
      totalCommission: commissionAmount,
      totalRepayment,
      overpaymentPercent: ((totalInterest + commissionAmount) / P) * 100,
      amountReceived: P - commissionAmount,
      schedule,
      effectiveRate: (() => {
        const Prec = P - commissionAmount;
        if (Prec <= 0 || payments[0] <= 0) return (Math.pow(1 + i, 12) - 1) * 100;
        
        // Solve Prec = Sum( Mk / (1+r)^k )
        let r = i > 0 ? i : 0.01;
        for (let j = 0; j < 20; j++) {
          let f = -Prec;
          let df = 0;
          for (let k = 1; k <= n; k++) {
            const disc = Math.pow(1 + r, k);
            f += payments[k-1] / disc;
            df -= k * payments[k-1] / (disc * (1 + r));
          }
          const nextR = r - f / df;
          if (Math.abs(nextR - r) < 0.000001) {
            r = nextR;
            break;
          }
          r = nextR;
        }
        return (Math.pow(1 + Math.max(0, r), 12) - 1) * 100;
      })()
    };
  }
}

/**
 * Deposit Calculation (Compound Interest)
 */
export function calculateDeposit(inputs: DepositInputs): DepositResults {
  const { initialAmount, monthlyAddition, rate, term, capitalization, additionPeriod } = inputs;
  
  let finalAmount = initialAmount;
  let totalAdditions = 0;
  const schedule: DepositScheduleItem[] = [];
  
  const daysInMonths = [31, 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31];
  const annualRate = rate / 100;
  const dailyRate = annualRate / 365;

  // Addition period months
  let additionStep = 0;
  if (additionPeriod === 'monthly') additionStep = 1;
  if (additionPeriod === 'quarterly') additionStep = 3;
  if (additionPeriod === 'semi-yearly') additionStep = 6;
  if (additionPeriod === 'yearly') additionStep = 12;

  // Capitalization period
  let capStepMonths = 0;
  if (capitalization === 'monthly') capStepMonths = 1;
  if (capitalization === 'quarterly') capStepMonths = 3;
  if (capitalization === 'semi-yearly') capStepMonths = 6;
  if (capitalization === 'yearly') capStepMonths = 12;

  let capStepDays = 0;
  if (capitalization === 'daily') capStepDays = 1;
  if (capitalization === 'weekly') capStepDays = 7;

  let accumulatedInterest = 0;
  let currentDay = 0;

  for (let month = 1; month <= term; month++) {
    let addition = 0;
    if (additionStep > 0 && month % additionStep === 0) {
      addition = monthlyAddition;
      finalAmount += addition;
      totalAdditions += addition;
    }

    const daysInThisMonth = daysInMonths[(month - 1) % 12];
    let capitalizedThisMonth = 0;

    if (capStepDays > 0) {
      // Day-by-day calculation for daily/weekly capitalization
      for (let day = 1; day <= daysInThisMonth; day++) {
        currentDay++;
        const interestThisDay = finalAmount * dailyRate;
        accumulatedInterest += interestThisDay;
        
        if (currentDay % capStepDays === 0) {
          finalAmount += accumulatedInterest;
          capitalizedThisMonth += accumulatedInterest;
          accumulatedInterest = 0;
        }
      }
    } else {
      // Month-based calculation for monthly, quarterly, yearly or none
      const interestThisMonth = finalAmount * annualRate * (daysInThisMonth / 365);
      accumulatedInterest += interestThisMonth;

      if (capStepMonths > 0 && month % capStepMonths === 0) {
        finalAmount += accumulatedInterest;
        capitalizedThisMonth = accumulatedInterest;
        accumulatedInterest = 0;
      }
    }

    schedule.push({
      month,
      interest: capitalizedThisMonth,
      addition,
      balance: finalAmount
    });
  }

  // Handle leftovers at the end of the term (regardless of capitalization frequency)
  if (accumulatedInterest > 0) {
    const leftover = accumulatedInterest;
    finalAmount += leftover;
    if (schedule.length > 0) {
      schedule[schedule.length - 1].interest += leftover;
      schedule[schedule.length - 1].balance = finalAmount;
    }
  }

  const totalInterest = finalAmount - (initialAmount + totalAdditions);

  return {
    finalAmount,
    totalInterest,
    totalAdditions,
    schedule
  };
}

export function formatCurrency(value: number): string {
  return new Intl.NumberFormat('ru-RU', {
    style: 'currency',
    currency: 'RUB',
    maximumFractionDigits: 0,
  }).format(value).replace('RUB', '₽').trim();
}

export function formatCurrencyCompact(value: number): string {
  return new Intl.NumberFormat('ru-RU', {
    maximumFractionDigits: 0,
  }).format(value);
}
