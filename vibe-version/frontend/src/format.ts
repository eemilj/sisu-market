import { fi } from './i18n/fi';

const numberFmt = new Intl.NumberFormat('fi-FI');
const percentFmt = new Intl.NumberFormat('fi-FI', { style: 'percent', maximumFractionDigits: 0 });
const dateFmt = new Intl.DateTimeFormat('fi-FI', { dateStyle: 'medium', timeStyle: 'short' });

export const formatSisu = (n: number) => fi.sisu(numberFmt.format(n));
export const formatNumber = (n: number) => numberFmt.format(n);
export const formatPercent = (p: number) => percentFmt.format(p);
export const formatDate = (iso: string) => dateFmt.format(new Date(iso));
