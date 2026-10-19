/**
 * @license
 * SPDX-License-Identifier: Apache-2.0
 */

import { jsPDF } from 'jspdf';
import autoTable from 'jspdf-autotable';
import { Capacitor } from '@capacitor/core';
import { Share } from '@capacitor/share';
import { Filesystem, Directory } from '@capacitor/filesystem';
import { LoanInputs, LoanResults, DepositInputs, DepositResults, AppSkin } from '../types';
import { formatCurrency } from './calculators';
import { RobotoBase64 } from './font';

const SKIN_COLORS = {
  emerald: [16, 185, 129] as [number, number, number],
  blue: [59, 130, 246] as [number, number, number],
  amber: [245, 158, 11] as [number, number, number],
  rose: [244, 63, 94] as [number, number, number],
  violet: [139, 92, 246] as [number, number, number]
};

export async function shareCalculationPDF(
  filename: string,
  type: 'loan' | 'deposit',
  inputs: LoanInputs | DepositInputs,
  results: LoanResults | DepositResults,
  skin: AppSkin
): Promise<void> {
  const pdf = new jsPDF({
    orientation: 'p',
    unit: 'mm',
    format: 'a4'
  });
  
  pdf.addFileToVFS('Roboto-Regular.ttf', RobotoBase64);
  pdf.addFont('Roboto-Regular.ttf', 'Roboto', 'normal');
  pdf.setFont('Roboto');
  
  const primaryColor = SKIN_COLORS[skin] || SKIN_COLORS.emerald;
  const fontName = 'Roboto';
  
  const pageWidth = pdf.internal.pageSize.getWidth();
  const margin = 15;

  // Header
  pdf.setTextColor(15, 23, 42);
  pdf.setFontSize(22);
  const titleText = type === 'loan' ? 'Расчет по кредиту' : 'Расчет по депозиту';
  pdf.text(titleText, pageWidth / 2, 25, { align: 'center' });

  pdf.setDrawColor(primaryColor[0], primaryColor[1], primaryColor[2]);
  pdf.setLineWidth(1);
  pdf.line(margin, 35, pageWidth - margin, 35);

  let currentY = 45;

  // 1. Parameters
  pdf.setFontSize(14);
  pdf.setTextColor(primaryColor[0], primaryColor[1], primaryColor[2]);
  pdf.text(type === 'loan' ? 'Параметры кредита:' : 'Параметры депозита:', margin, currentY);
  currentY += 5;

  const paramData: string[][] = [];
  if (type === 'loan') {
    const lValue = inputs as LoanInputs;
    const lRes = results as LoanResults;
    paramData.push(['Сумма кредита:', formatCurrency(lValue.amount)]);
    paramData.push(['Ставка и срок:', `${lValue.rate.toFixed(2)}% | ${lValue.term} мес.`]);
    
    // Commission handling
    const hasFixed = lValue.fixedCommission > 0;
    const hasPercent = lValue.percentCommission > 0;
    if (hasFixed || hasPercent) {
      let commissionStr = '';
      if (hasFixed && hasPercent) {
        commissionStr = `(${formatCurrency(lValue.fixedCommission)} + ${lValue.percentCommission}%) = ${formatCurrency(lRes.totalCommission)}`;
      } else if (hasFixed) {
        commissionStr = formatCurrency(lValue.fixedCommission);
      } else {
        commissionStr = `${lValue.percentCommission}% = ${formatCurrency(lRes.totalCommission)}`;
      }
      paramData.push(['Комиссия:', commissionStr]);
    }
    
    paramData.push(['Тип платежа:', lValue.paymentType === 'annuity' ? 'аннуитетный' : 'дифференцированный']);
    
    // Payment sum handling
    let paymentStr = '';
    if (Array.isArray(lRes.monthlyPayment)) {
      const first = lRes.monthlyPayment[0];
      const last = lRes.monthlyPayment[lRes.monthlyPayment.length - 1];
      paymentStr = `от ${formatCurrency(first)} до ${formatCurrency(last)}`;
    } else {
      paymentStr = formatCurrency(lRes.monthlyPayment);
    }
    paramData.push(['Сумма платежа:', paymentStr]);
  } else {
    const dValue = inputs as DepositInputs;
    paramData.push(['Сумма вклада:', formatCurrency(dValue.initialAmount)]);
    paramData.push(['Ставка и срок:', `${dValue.rate.toFixed(2)}% | ${dValue.term} мес.`]);
    
    if (dValue.additionPeriod !== 'none' && dValue.monthlyAddition > 0) {
      let suffix = '';
      if (dValue.additionPeriod === 'monthly') suffix = ' / мес.';
      if (dValue.additionPeriod === 'quarterly') suffix = ' / кварт.';
      if (dValue.additionPeriod === 'semi-yearly') suffix = ' / полгода';
      if (dValue.additionPeriod === 'yearly') suffix = ' / год';
      paramData.push(['Пополнения:', `${formatCurrency(dValue.monthlyAddition)}${suffix}`]);
    }

    const capLabels: Record<string, string> = {
      none: 'в конце срока',
      daily: 'ежедневная',
      weekly: 'еженедельная',
      monthly: 'ежемесячная',
      quarterly: 'ежеквартальная',
      'semi-yearly': 'раз в полгода',
      yearly: 'ежегодная'
    };
    paramData.push(['Капитализация процентов:', capLabels[dValue.capitalization] || 'нет']);
  }

  autoTable(pdf, {
    startY: currentY,
    margin: { left: margin, right: margin },
    body: paramData,
    theme: 'plain',
    styles: { font: fontName, fontStyle: 'normal', fontSize: 10, cellPadding: 2 },
    didParseCell: (data) => {
      if (data.column.index === 0) data.cell.styles.textColor = [100, 116, 139];
      if (data.column.index === 1) {
        data.cell.styles.halign = 'right';
      }
    }
  });

  currentY = (pdf as any).lastAutoTable.finalY + 10;

  // 2. Results
  pdf.setFontSize(14);
  pdf.setTextColor(primaryColor[0], primaryColor[1], primaryColor[2]);
  pdf.text('Результаты расчета:', margin, currentY);
  currentY += 5;

  const resultData: string[][] = [];
  if (type === 'loan') {
    const lValue = inputs as LoanInputs;
    const lRes = results as LoanResults;
    resultData.push(['Переплата:', formatCurrency(lRes.totalInterest)]);
    resultData.push(['Сумма возврата:', formatCurrency(lRes.totalRepayment)]);
    resultData.push(['Номинальная ставка:', `${lValue.rate.toFixed(2)}%`]);
    if (lRes.effectiveRate !== undefined) {
      resultData.push(['Эффективная ставка:', `${lRes.effectiveRate.toFixed(2)}%`]);
    }
  } else {
    const dValue = inputs as DepositInputs;
    const dRes = results as DepositResults;
    
    resultData.push(['Итоговая сумма:', formatCurrency(dRes.finalAmount)]);
    resultData.push(['Доход:', formatCurrency(dRes.totalInterest)]);
    resultData.push(['Пополнения:', formatCurrency(dRes.totalAdditions)]);
    resultData.push(['Ставка:', `${dValue.rate.toFixed(2)}%`]);
    
    // Calculate APY (Effective Annual Rate)
    const r = dValue.rate / 100;
    let n = 0;
    if (dValue.capitalization === 'daily') n = 365;
    if (dValue.capitalization === 'weekly') n = 52;
    if (dValue.capitalization === 'monthly') n = 12;
    if (dValue.capitalization === 'quarterly') n = 4;
    if (dValue.capitalization === 'semi-yearly') n = 2;
    if (dValue.capitalization === 'yearly') n = 1;
    
    let apy = dValue.rate;
    if (n > 0) {
      apy = (Math.pow(1 + r/n, n) - 1) * 100;
    }
    resultData.push(['Эффективная ставка (сложный банковский процент):', `${apy.toFixed(2)}%`]);
  }

  autoTable(pdf, {
    startY: currentY,
    margin: { left: margin, right: margin },
    body: resultData,
    theme: 'plain',
    styles: { font: fontName, fontStyle: 'normal', fontSize: 10, cellPadding: 2 },
    didParseCell: (data) => {
      if (data.column.index === 0) data.cell.styles.textColor = [100, 116, 139];
      if (data.column.index === 1) {
        data.cell.styles.halign = 'right';
      }
    }
  });

  currentY = (pdf as any).lastAutoTable.finalY + 10;

  // 3. Schedule
  if (currentY > 240) {
    pdf.addPage();
    currentY = 20;
  }

  pdf.setFontSize(14);
  pdf.setTextColor(15, 23, 42);
  pdf.text(type === 'loan' ? 'График платежей:' : 'График начислений:', margin, currentY);
  currentY += 5;

  let head: string[][] = [];
  let body: string[][] = [];

  const formatValue = (v: number) => {
    return v === 0 ? '-' : formatCurrency(v);
  };

  if (type === 'loan') {
    head = [['Месяц', 'Платёж', 'Долг', 'Проценты', 'Остаток']];
    body = (results.schedule as any[]).map(item => [
      String(item.month),
      formatValue(item.payment),
      formatValue(item.principal),
      formatValue(item.interest),
      formatValue(item.remainingBalance)
    ]);
  } else {
    head = [['Месяц', 'Проценты', 'Взнос', 'Баланс']];
    body = (results.schedule as any[]).map(item => [
      String(item.month),
      formatValue(item.interest),
      formatValue(item.addition),
      formatValue(item.balance)
    ]);
  }

  autoTable(pdf, {
    startY: currentY,
    margin: { left: margin, right: margin, bottom: 20 },
    head: head,
    body: body,
    theme: 'striped',
    headStyles: { fillColor: primaryColor as [number, number, number], font: fontName, fontStyle: 'normal', halign: 'center' },
    styles: { font: fontName, fontStyle: 'normal', fontSize: 8, cellPadding: 2 },
    didParseCell: (data) => {
      if (data.section === 'body' && data.column.index > 0) data.cell.styles.halign = 'right';
      if (data.section === 'body' && data.column.index === 0) data.cell.styles.halign = 'center';
    }
  });

  const pageCount = pdf.getNumberOfPages();
  for (let i = 1; i <= pageCount; i++) {
    pdf.setPage(i);
    pdf.setFont(fontName, 'normal');
    pdf.setFontSize(8);
    pdf.setTextColor(150, 150, 150);
    const footerY = pdf.internal.pageSize.getHeight() - 10;
    pdf.text('DepoCred — финансовый калькулятор кредитов и вкладов', margin, footerY);
    if (pageCount > 1) {
      const pageNumberStr = `${i} из ${pageCount} стр.`;
      const textWidth = pdf.getStringUnitWidth(pageNumberStr) * 8 / pdf.internal.scaleFactor;
      const pageWidth = pdf.internal.pageSize.getWidth();
      pdf.text(pageNumberStr, pageWidth - margin - textWidth, footerY);
    }
  }

  const fullFileName = `${filename}.pdf`;

  let shareText = '';
  if (type === 'loan') {
    const lIn = inputs as LoanInputs;
    
    shareText = `DepoCred — финансовый калькулятор кредитов и вкладов\n` +
                `Кредит: ${formatCurrency(lIn.amount)}  |  ${lIn.rate.toFixed(2)}%  |  ${lIn.term} мес.`;
  } else {
    const dIn = inputs as DepositInputs;
    shareText = `DepoCred — финансовый калькулятор кредитов и вкладов\n` +
                `Депозит: ${formatCurrency(dIn.initialAmount)}  |  ${dIn.rate.toFixed(2)}%  |  ${dIn.term} мес.`;
  }

  if (Capacitor.isNativePlatform()) {
    const pdfBase64 = pdf.output('datauristring').split(',')[1];
    
    await Filesystem.writeFile({
      path: fullFileName,
      data: pdfBase64,
      directory: Directory.Cache
    });
    
    const fileUri = await Filesystem.getUri({
      path: fullFileName,
      directory: Directory.Cache
    });

    try {
      await Share.share({
        title: shareText ? shareText.substring(0, 50) + '...' : 'Отчет',
        url: fileUri.uri
        // Описание (text) намеренно убрано для нативных платформ, так как 
        // совместная отправка текста и файла приводит к повисанию сторонних приложений (WhatsApp/Telegram) 
        // на Android из-за конфликта Intent типов (MIME type mismatch: text/plain vs application/pdf).
      });
    } catch (err) {
      console.error('Share plugin error:', err);
      throw err;
    }
  } else {
    const blob = pdf.output('blob');
    const file = new File([blob], fullFileName, { type: 'application/pdf' });
    
    if (navigator.share && navigator.canShare && navigator.canShare({ files: [file] })) {
      try {
        await navigator.share({ 
          files: [file],
          title: 'Отчет PDF'
        });
      } catch (err) {
        if ((err as Error).name !== 'AbortError') pdf.save(fullFileName);
      }
    } else {
      pdf.save(fullFileName);
    }
  }
}
