/**
 * @license
 * SPDX-License-Identifier: Apache-2.0
 */

export type CalculationType = 'loan' | 'deposit';

export type LoanPaymentType = 'annuity' | 'differentiated' | null;

export interface LoanInputs {
  amount: number;
  rate: number;
  term: number; // months
  fixedCommission: number;
  percentCommission: number;
  paymentType: LoanPaymentType;
  downPayment?: number;
  reversePayment?: number;
  reverseTotalRepayment?: number;
}

export interface LoanScheduleItem {
  month: number;
  payment: number;
  principal: number;
  interest: number;
  remainingBalance: number;
}

export interface LoanResults {
  monthlyPayment: number | number[]; // number for annuity, array for diff
  totalInterest: number;
  totalCommission: number;
  totalRepayment: number;
  overpaymentPercent: number;
  amountReceived: number;
  schedule: LoanScheduleItem[];
  effectiveRate?: number;
}

export type CapitalizationType = 'none' | 'daily' | 'weekly' | 'monthly' | 'quarterly' | 'semi-yearly' | 'yearly';

export type AdditionPeriod = 'none' | 'monthly' | 'quarterly' | 'semi-yearly' | 'yearly';

export interface DepositInputs {
  initialAmount: number;
  monthlyAddition: number; // Keep name for compatibility, though it's technically generic addition amount now
  rate: number;
  term: number; // months
  capitalization: CapitalizationType;
  additionPeriod: AdditionPeriod;
  reverseFinalAmount?: number;
}

export interface DepositScheduleItem {
  month: number;
  interest: number;
  addition: number;
  balance: number;
}

export interface DepositResults {
  finalAmount: number;
  totalInterest: number;
  totalAdditions: number;
  schedule: DepositScheduleItem[];
}

export interface HistoryItem {
  id: string;
  type: CalculationType;
  timestamp: number;
  inputs: LoanInputs | DepositInputs;
  results: LoanResults | DepositResults;
  label: string;
}

export type AppSkin = 'emerald' | 'blue' | 'amber' | 'rose' | 'violet';

export interface AppSettings {
  theme: 'light' | 'dark' | 'system';
  skin: AppSkin;
  showHints: boolean;
}
