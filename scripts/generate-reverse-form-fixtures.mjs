// Execute the original PWA effect and solvers as an independent native-form oracle.
import { readFileSync, writeFileSync } from 'node:fs';
const component = readFileSync('src/components/DepositCalculator.tsx', 'utf8');
const calculator = readFileSync('src/lib/calculators.ts', 'utf8');
const depositSource = calculator.slice(calculator.indexOf('export function calculateDeposit('), calculator.indexOf('export function formatCurrency('))
  .replace('export function calculateDeposit(inputs: DepositInputs): DepositResults', 'function calculateDeposit(inputs)')
  .replace('const schedule: DepositScheduleItem[]', 'const schedule');
const calculateDeposit = new Function(`${depositSource}; return calculateDeposit;`)();
const solverStart = component.indexOf('  // Solvers for the 4 parameters');
const effectStart = component.indexOf('  useEffect(() => {', solverStart);
const effectEnd = component.indexOf('  }, [inputs.initialAmount', effectStart);
const source = (component.slice(solverStart, effectStart) + component.slice(effectStart + '  useEffect(() => {'.length, effectEnd))
  .replaceAll(': DepositInputs', '').replaceAll(': number', '')
  .replaceAll('(Object.keys(values) as Array<keyof typeof values>)', 'Object.keys(values)')
  .replace('let targetKey: keyof typeof values | null', 'let targetKey').replaceAll('targetKey!', 'targetKey');
const run = new Function('calculateDeposit', 'inputs', 'last3ManualKeys', 'setInputs', 'calculationMode', source);
const names = { amount: 'initialAmount', rate: 'rate', term: 'term', final: 'reverseFinalAmount', addition: 'monthlyAddition', period: 'additionPeriod', cap: 'capitalization' };
const primary = ['amount', 'rate', 'term', 'final'];
const native = key => Object.keys(names).find(k => names[k] === key);
const scenarios = [
  [['amount','100000'],['rate','12'],['term','12'],['final',''],['final','120000'],['rate','15'],['amount',''],['amount','100000']],
  [['amount','100000'],['rate','12'],['term','12'],['rate',''],['final','120000'],['term','12'],['amount','100000']],
  [['amount','100000'],['final','120000'],['term','12'],['period','monthly'],['addition','1000'],['cap','daily'],['period','none']],
  [['amount','100000'],['final','120000'],['term','12'],['final','1'],['final','120000']],
];
const values = { amount:'100000', final:'112682.44', rate:'12', term:'12' };
for (const unknown of primary) scenarios.push(primary.filter(k => k !== unknown).map(k => [k, values[k]]));
const fixtures = scenarios.map(edits => {
  let inputs = { initialAmount:0, rate:0, term:0, reverseFinalAmount:0, monthlyAddition:0, additionPeriod:'none', capitalization:'monthly' };
  let manual = [];
  return edits.map(([key, text]) => {
    const name = names[key];
    if (primary.includes(key)) manual = [...manual.filter(k => k !== name), name].slice(-3);
    inputs = { ...inputs, [name]: ['period','cap'].includes(key) ? text : Number(text) };
    for (let i=0;i<8;i++) {
      let changed = false;
      run(calculateDeposit, inputs, manual, update => { const next=update(inputs); changed=JSON.stringify(next)!==JSON.stringify(inputs); inputs=next; }, 'reverse');
      if (!changed) break;
    }
    return { key, text, expected:Object.fromEntries(primary.map(k => [k, inputs[names[k]] || 0])), manual:manual.map(native) };
  });
});
writeFileSync('android/app/src/test/resources/pwa-reverse-forms.json', JSON.stringify({ source:'Unchanged DepositCalculator.tsx solvers/useEffect', fixtures }, null, 2));
console.log(`${fixtures.length} PWA interaction sequences, ${fixtures.flat().length} field transitions`);
