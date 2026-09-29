import { fi } from '../i18n/strings.fi';

interface OddsBarProps {
  oddsYesPercent: number;
  oddsNoPercent: number;
}

/**
 * The pari-mutuel yes/no split (FR-034) as a two-colour bar with the percentages as text, so the
 * odds never rely on colour alone (WCAG 2.1 AA). The bar itself is decorative.
 */
export default function OddsBar({ oddsYesPercent, oddsNoPercent }: OddsBarProps) {
  const yes = fi.odds.percent(oddsYesPercent);
  const no = fi.odds.percent(oddsNoPercent);

  return (
    <div role="group" aria-label={fi.odds.summary(yes, no)} className="flex flex-col gap-1">
      <div aria-hidden="true" className="flex h-2.5 overflow-hidden rounded-full bg-slate-200">
        <div className="bg-emerald-600" style={{ width: `${oddsYesPercent}%` }} />
        <div className="bg-rose-600" style={{ width: `${oddsNoPercent}%` }} />
      </div>
      <div aria-hidden="true" className="flex justify-between text-sm font-medium">
        <span className="text-emerald-800">
          {fi.outcome.YES} {yes}
        </span>
        <span className="text-rose-800">
          {fi.outcome.NO} {no}
        </span>
      </div>
    </div>
  );
}
