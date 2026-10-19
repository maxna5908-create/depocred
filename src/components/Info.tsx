/**
 * @license
 * SPDX-License-Identifier: Apache-2.0
 */

import React from 'react';
import { useTheme } from './ThemeContext';
import { AppSkin } from '../types';
import { GlassCard } from './ui/Common';
import { Shield, Sparkles, Smartphone, Moon, Sun, Mail, Info as InfoIcon, Globe, Share2, Star } from 'lucide-react';
import { BUILD_VERSION } from '../lib/version';

export const Info: React.FC = () => {
  const { settings, setTheme, setSkin, setShowHints } = useTheme();

  const skins: { id: AppSkin, color: string }[] = [
    { id: 'emerald', color: '#10b981' },
    { id: 'blue', color: '#3b82f6' },
    { id: 'amber', color: '#f59e0b' },
    { id: 'rose', color: '#f43f5e' },
    { id: 'violet', color: '#8b5cf6' },
  ];

  return (
    <div className="flex flex-col gap-8 p-4 pb-32">
      <div className="flex flex-col gap-4">
        <h2 className="text-xl font-bold px-1 text-slate-900 dark:text-white">Настройки</h2>
        
        <div className="glass-card flex flex-col gap-6">
          <div className="flex flex-col gap-3">
            <span className="text-[10px] font-bold text-slate-500 dark:text-slate-400 uppercase tracking-widest ml-1">Тема оформления</span>
            <div className="grid grid-cols-2 p-1 glass rounded-xl">
              <button 
                onClick={() => setTheme('light')}
                className={`py-3 rounded-lg transition-all flex items-center justify-center gap-2 ${settings.theme === 'light' ? 'bg-skin-primary text-white shadow-sm' : 'text-slate-400 dark:text-white'}`}
              >
                <Sun size={18} />
                <span className="text-sm font-semibold">Светлая</span>
              </button>
              <button 
                onClick={() => setTheme('dark')}
                className={`py-3 rounded-lg transition-all flex items-center justify-center gap-2 ${settings.theme === 'dark' ? 'bg-skin-primary text-white shadow-sm' : 'text-slate-400 dark:text-white'}`}
              >
                <Moon size={18} />
                <span className="text-sm font-semibold">Темная</span>
              </button>
            </div>
          </div>

          <div className="flex flex-col gap-3">
            <span className="text-[10px] font-bold text-slate-500 dark:text-slate-400 uppercase tracking-widest ml-1">Цветовая схема</span>
            <div className="flex justify-between px-2">
              {skins.map(skin => (
                <button
                  key={skin.id}
                  onClick={() => setSkin(skin.id)}
                  style={{ backgroundColor: skin.color }}
                  className={`w-10 h-10 rounded-xl transition-all active:scale-75 ${settings.skin === skin.id ? 'ring-4 ring-neutral-200 dark:ring-white/10 scale-110' : 'opacity-40'}`}
                />
              ))}
            </div>
          </div>

          <div className="flex flex-col gap-3 pt-2">
            <div className="flex items-center gap-3 px-1">
              <button 
                onClick={() => setShowHints(!settings.showHints)}
                className={`w-12 h-6 rounded-full transition-all relative flex-shrink-0 ${settings.showHints ? 'bg-skin-primary' : 'bg-slate-200 dark:bg-white/10'}`}
              >
                <div className={`absolute top-1 w-4 h-4 rounded-full bg-white shadow-sm transition-all ${settings.showHints ? 'left-7' : 'left-1'}`} />
              </button>
              <span className="text-[10px] font-bold text-slate-500 dark:text-slate-400 uppercase tracking-widest">Подсказки в приложении</span>
            </div>
          </div>
        </div>
      </div>

      <div className="flex flex-col gap-5">
        <h2 className="text-xl font-bold px-1 text-slate-900 dark:text-white">О приложении</h2>
        
        <div className="flex flex-col gap-4">
          <div className="glass px-6 py-5 rounded-2xl">
            <p className="text-sm text-slate-500 dark:text-slate-300">
              <span className="font-bold text-slate-900 dark:text-white">DepoCred</span> - это современный финансовый инструмент для быстрого расчета кредитных и депозитных условий.
            </p>
          </div>

          <div className="glass px-6 py-5 rounded-2xl flex items-center gap-4">
            <div className="p-3 bg-emerald-500/10 text-emerald-500 rounded-lg border border-emerald-500/20">
              <Shield size={22} />
            </div>
            <div className="flex flex-col">
              <h3 className="font-bold text-slate-900 dark:text-white uppercase text-xs tracking-wider">Конфиденциальность</h3>
              <p className="text-[13px] text-slate-500 dark:text-slate-300 mt-0.5">Все расчеты хранятся локально на вашем устройстве. Мы не собираем финансовые данные.</p>
            </div>
          </div>

          <div className="glass px-6 py-5 rounded-2xl flex items-center gap-4">
            <div className="p-3 bg-blue-500/10 text-blue-500 rounded-lg border border-blue-500/20">
              <Sparkles size={22} />
            </div>
            <div className="flex flex-col">
              <h3 className="font-bold text-slate-900 dark:text-white uppercase text-xs tracking-wider">Умные алгоритмы</h3>
              <p className="text-[13px] text-slate-500 dark:text-slate-300 mt-0.5">Точные расчеты аннуитетных и дифференцированных платежей, а также сложного банковского процента.</p>
            </div>
          </div>

          <div className="glass px-6 py-5 rounded-2xl flex items-center gap-4">
            <div className="p-3 bg-violet-500/10 text-violet-500 rounded-lg border border-violet-500/20">
              <Smartphone size={22} />
            </div>
            <div className="flex flex-col">
              <h3 className="font-bold text-slate-900 dark:text-white uppercase text-xs tracking-wider">Платформа</h3>
              <p className="text-[13px] text-slate-500 dark:text-slate-300 mt-0.5">Полная адаптация под смартфоны. Удобный ввод и наглядная детализация расчетов. Возможность сохранять и делиться расчетами.</p>
            </div>
          </div>
        </div>
      </div>

      <div className="mt-4 pt-4 border-t border-skin-primary/20">
        <div className="flex flex-col gap-1">
          <a href="https://depocred.ru" className="flex items-center gap-3 text-neutral-500 dark:text-neutral-400 hover:text-skin-primary transition-colors p-2 px-3">
            <Globe size={20} />
            <span className="font-medium">depocred.ru</span>
          </a>
          <a href="mailto:support@depocred.ru" className="flex items-center gap-3 text-neutral-500 dark:text-neutral-400 hover:text-skin-primary transition-colors p-2 px-3">
            <Mail size={20} />
            <span className="font-medium">support@depocred.ru</span>
          </a>
          <button 
            onClick={async () => {
              try {
                const { Share } = await import('@capacitor/share');
                await Share.share({
                  title: 'DepoCred',
                  text: 'DepoCred - современный финансовый калькулятор для кредитов и депозитов.',
                  url: 'https://depocred.ru',
                  dialogTitle: 'Поделиться приложением',
                });
              } catch (err) {
                console.error('Share failed:', err);
              }
            }}
            className="flex items-center gap-3 text-neutral-500 dark:text-neutral-400 hover:text-skin-primary transition-colors p-2 px-3"
          >
            <Share2 size={20} />
            <span className="font-medium">Поделиться приложением</span>
          </button>
          <button className="flex items-center gap-3 text-neutral-500 dark:text-neutral-400 hover:text-skin-primary transition-colors p-2 px-3">
            <Star size={20} />
            <span className="font-medium">Поставить оценку</span>
          </button>
        </div>
      </div>
      
      <div className="border-b border-skin-primary/20 pb-4"></div>
      
      <div className="text-center text-[10px] text-neutral-400 dark:text-neutral-300 font-bold uppercase tracking-[0.2em] mt-4 flex flex-col gap-1.5 leading-relaxed">
        <div className="opacity-60">Версия 1.0.{BUILD_VERSION}</div>
        <div className="text-skin-primary opacity-40 font-black tracking-[0.5em]">• • •</div>
        <div>Сделано с ❤️ для вашей финансовой свободы</div>
      </div>
    </div>
  );
};
