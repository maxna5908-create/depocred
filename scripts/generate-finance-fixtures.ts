// Independent oracle: expected outputs come directly from the unchanged PWA.
import { calculateLoan, calculateDeposit } from '../src/lib/calculators';
import { writeFileSync, mkdirSync } from 'node:fs';
const loans: any[] = [];
for (const paymentType of ['annuity', 'differentiated'] as const) {
  for (const term of [1, 12, 60, 360, 999]) {
    for (const rate of [0, 12, 49.9]) {
      const input = { amount: 750000, rate, term, paymentType, downPayment: 50000, fixedCommission: rate === 0 ? 0 : 2500, percentCommission: rate === 0 ? 0 : 1.5 };
      const result = calculateLoan(input);
      loans.push({ input, result });
    }
  }
}
const deposits: any[] = [];
for (const capitalization of ['none','daily','weekly','monthly','quarterly','semi-yearly','yearly'] as const) {
  for (const additionPeriod of ['none','monthly','quarterly','semi-yearly','yearly'] as const) {
    for (const term of [1, 7, 12, 37]) {
      const input = { initialAmount: 250000, rate: 13.5, term, monthlyAddition: 7500, capitalization, additionPeriod };
      deposits.push({ input, result: calculateDeposit(input) });
    }
  }
}
mkdirSync('android/app/src/test/resources', { recursive: true });
writeFileSync('android/app/src/test/resources/pwa-parity.json', JSON.stringify({ source: 'src/lib/calculators.ts at dc9687a', loans, deposits }));
console.log(`${loans.length} loan + ${deposits.length} deposit fixtures generated from the PWA`);
