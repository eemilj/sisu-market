const sisuFormat = new Intl.NumberFormat('fi-FI');
const dateFormat = new Intl.DateTimeFormat('fi-FI', { day: 'numeric', month: 'numeric', year: 'numeric' });

const dateTimeFormat = new Intl.DateTimeFormat('fi-FI', { dateStyle: 'short', timeStyle: 'short' });

export const formatSignedSisu = (amount: number) => `${amount > 0 ? '+' : amount < 0 ? '−' : ''}${formatSisu(Math.abs(amount))}`;
export const formatDateTime = (iso: string) => dateTimeFormat.format(new Date(iso));

/** 0.4237 -> "42.4%" */
export const formatProbability = (p: number) => `${(p * 100).toFixed(1)}%`;

export const formatSisu = (amount: number) => `${sisuFormat.format(amount)} Sisu`;
export const formatDate = (iso: string) => dateFormat.format(new Date(iso));

export const categoryLabels = {
  POLITICS: 'Politics',
  SPORTS: 'Sports',
  ECONOMY: 'Economy',
  WEATHER: 'Weather',
  CULTURE: 'Culture',
} as const;
