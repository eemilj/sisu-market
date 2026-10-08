import type { Category, Outcome, PredictionStatus, TransactionType, WagerStatus } from './api';

// ---- numbers & dates (Finnish conventions: "1 000", "42,4 %", "9.10.2026 klo 14.05") ----

const integerFormat = new Intl.NumberFormat('fi-FI');
const percentFormat = new Intl.NumberFormat('fi-FI', { style: 'percent', minimumFractionDigits: 1, maximumFractionDigits: 1 });
const wholePercentFormat = new Intl.NumberFormat('fi-FI', { style: 'percent', maximumFractionDigits: 0 });
const dateFormat = new Intl.DateTimeFormat('fi-FI', { day: 'numeric', month: 'numeric', year: 'numeric' });
const dateTimeFormat = new Intl.DateTimeFormat('fi-FI', {
  day: 'numeric', month: 'numeric', year: 'numeric', hour: '2-digit', minute: '2-digit',
});

/** "1 Sisu", "2 Sisua", "1 000 Sisua" (partitive after any number but one). */
export const formatSisu = (amount: number) => `${integerFormat.format(amount)} ${Math.abs(amount) === 1 ? 'Sisu' : 'Sisua'}`;
export const formatSignedSisu = (amount: number) => `${amount > 0 ? '+' : amount < 0 ? '−' : ''}${formatSisu(Math.abs(amount))}`;
export const formatNumber = (n: number) => integerFormat.format(n);
/** 0.4237 -> "42,4 %" */
export const formatProbability = (p: number) => percentFormat.format(p);
/** 42 -> "42 %" */
export const formatPercent = (whole: number) => wholePercentFormat.format(whole / 100);
export const formatDate = (iso: string | Date) => dateFormat.format(new Date(iso));
export const formatDateTime = (iso: string | Date) => dateTimeFormat.format(new Date(iso));
const timeFormat = new Intl.DateTimeFormat('fi-FI', { hour: '2-digit', minute: '2-digit' });
const dayKey = (d: Date) => `${d.getFullYear()}-${d.getMonth()}-${d.getDate()}`;

/**
 * When something closes, as precisely as is useful: "tänään klo 15.30", "huomenna klo 9.00", or
 * "12.11.2026 klo 18.00" within a week, otherwise just the date.
 */
export function formatWhen(iso: string, now = new Date()): string {
  const at = new Date(iso);
  const tomorrow = new Date(now);
  tomorrow.setDate(now.getDate() + 1);
  const time = `klo ${timeFormat.format(at)}`;
  if (dayKey(at) === dayKey(now)) return `tänään ${time}`;
  if (dayKey(at) === dayKey(tomorrow)) return `huomenna ${time}`;
  const yesterday = new Date(now);
  yesterday.setDate(now.getDate() - 1);
  if (dayKey(at) === dayKey(yesterday)) return `eilen ${time}`;
  if (Math.abs(at.getTime() - now.getTime()) < 7 * 24 * 3600_000) return `${dateFormat.format(at)} ${time}`;
  return dateFormat.format(at);
}

/** "x.yy×" with a Finnish decimal comma. */
export const formatMultiple = (x: number) => `${x.toLocaleString('fi-FI', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}×`;

/** Picks the singular or plural form: plural(2, 'panos', 'panosta'). */
export const plural = (n: number, one: string, many: string) => `${formatNumber(n)} ${n === 1 ? one : many}`;

// ---- domain labels ----

export const categoryLabels: Record<Category, string> = {
  POLITICS: 'Politiikka',
  SPORTS: 'Urheilu',
  ECONOMY: 'Talous',
  WEATHER: 'Sää',
  CULTURE: 'Kulttuuri',
};

export const outcomeLabels: Record<Outcome, string> = { YES: 'KYLLÄ', NO: 'EI' };

export const statusLabels: Record<PredictionStatus, string> = { OPEN: 'Avoin', CLOSED: 'Suljettu', RESOLVED: 'Ratkaistu' };

export const transactionLabels: Record<TransactionType, string> = {
  SIGNUP_BONUS: 'Tervetuliaisbonus',
  OPENING_BALANCE: 'Aloitussaldo',
  WAGER: 'Panos',
  WAGER_REFUND: 'Panoksen palautus',
  PAYOUT: 'Voitonmaksu',
};

export const wagerStatusLabels: Record<WagerStatus, string> = {
  OPEN: 'Avoin',
  WON: 'Voitettu',
  LOST: 'Hävitty',
  REFUNDED: 'Palautettu',
};
