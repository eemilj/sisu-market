import { fi } from '../i18n/strings.fi';

/** FR-032: sisu has no real-world value and cannot be cashed out; shown on every page (SC-008). */
export default function SisuDisclaimer() {
  return (
    <p className="text-center text-sm text-slate-700" data-testid="sisu-disclaimer">
      {fi.sisu.disclaimer}
    </p>
  );
}
