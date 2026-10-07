import { useState, type FormEvent } from 'react';
import { ApiError } from '../api/client';
import type { Outcome, Wager } from '../api/types';
import { usePlaceWager } from '../hooks/useWager';
import { errorMessage, fi } from '../i18n/strings.fi';

type Field = 'outcome' | 'amount';
type FieldErrors = Partial<Record<Field, string>>;

/** Server error codes that belong to one form field, so they are shown next to it. */
const FIELD_FOR_CODE: Record<string, Field> = {
  INVALID_WAGER_AMOUNT: 'amount',
  INSUFFICIENT_BALANCE: 'amount',
};

const OUTCOMES: Outcome[] = ['YES', 'NO'];

const inputClass =
  'w-40 rounded border border-slate-300 px-3 py-2 aria-[invalid=true]:border-red-600 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-sky-700';

/**
 * The same rules the backend enforces (FR-019, FR-020), checked first for instant feedback. The
 * amount must be typed as digits only, so "1.5" or "1e3" never reach the server.
 */
function validate(outcome: Outcome | null, amount: string, balanceSisu: number): FieldErrors {
  const errors: FieldErrors = {};
  if (!outcome) {
    errors.outcome = fi.wager.outcomeRequired;
  }
  const trimmed = amount.trim();
  if (!/^\d+$/.test(trimmed) || Number(trimmed) < 1) {
    errors.amount = errorMessage('INVALID_WAGER_AMOUNT');
  } else if (Number(trimmed) > balanceSisu) {
    errors.amount = errorMessage('INSUFFICIENT_BALANCE');
  }
  return errors;
}

/**
 * Wager form for one open prediction (User Story 3; FR-019, FR-020). Shown only to a logged-in
 * user; `balanceSisu` is their current balance, used to reject over-balance wagers before sending.
 */
export default function WagerForm({
  predictionId,
  balanceSisu,
}: {
  predictionId: string;
  balanceSisu: number;
}) {
  const place = usePlaceWager(predictionId);
  const [outcome, setOutcome] = useState<Outcome | null>(null);
  const [amount, setAmount] = useState('');
  const [fieldErrors, setFieldErrors] = useState<FieldErrors>({});
  const [placed, setPlaced] = useState<Wager | null>(null);

  const submit = (event: FormEvent) => {
    event.preventDefault();
    setPlaced(null);
    const errors = validate(outcome, amount, balanceSisu);
    setFieldErrors(errors);
    if (Object.keys(errors).length > 0 || !outcome) {
      return;
    }
    place.mutate(
      { outcome, amountSisu: Number(amount.trim()) },
      {
        onSuccess: (wager) => {
          setPlaced(wager);
          setAmount('');
        },
        onError: (error) => {
          const field = error instanceof ApiError ? FIELD_FOR_CODE[error.code] : undefined;
          if (field) {
            setFieldErrors({ [field]: errorMessage((error as ApiError).code) });
          }
        },
      },
    );
  };

  // Errors tied to a field are shown next to it; anything else goes in the banner.
  const bannerError =
    place.isError && !(place.error instanceof ApiError && FIELD_FOR_CODE[place.error.code])
      ? place.error instanceof ApiError
        ? errorMessage(place.error.code)
        : fi.errors.unknown
      : null;

  const amountHintId = 'wager-amount-hint';
  const amountErrorId = 'wager-amount-error';
  const outcomeErrorId = 'wager-outcome-error';

  return (
    <form onSubmit={submit} noValidate className="flex flex-col gap-4">
      {bannerError && (
        <p role="alert" className="rounded border border-red-300 bg-red-50 px-3 py-2 text-red-900">
          {bannerError}
        </p>
      )}

      <fieldset
        aria-describedby={fieldErrors.outcome ? outcomeErrorId : undefined}
        className="flex flex-col gap-2"
      >
        <legend className="text-sm font-medium">
          {fi.wager.outcome}
          <span aria-hidden="true"> *</span>
        </legend>
        <div className="flex gap-2">
          {OUTCOMES.map((value) => (
            <label
              key={value}
              className="flex cursor-pointer items-center gap-2 rounded border border-slate-300 px-4 py-2 has-[:checked]:border-sky-800 has-[:checked]:bg-sky-50 has-[:focus-visible]:outline-2 has-[:focus-visible]:outline-offset-2 has-[:focus-visible]:outline-sky-700"
            >
              <input
                type="radio"
                name="wager-outcome"
                value={value}
                checked={outcome === value}
                onChange={() => setOutcome(value)}
              />
              {fi.outcome[value]}
            </label>
          ))}
        </div>
        {fieldErrors.outcome && (
          <p id={outcomeErrorId} className="text-sm font-medium text-red-700">
            {fieldErrors.outcome}
          </p>
        )}
      </fieldset>

      <div className="flex flex-col gap-1">
        <label htmlFor="wager-amount" className="text-sm font-medium">
          {fi.wager.amount}
          <span aria-hidden="true"> *</span>
        </label>
        <input
          id="wager-amount"
          name="wager-amount"
          type="text"
          inputMode="numeric"
          autoComplete="off"
          required
          value={amount}
          onChange={(event) => setAmount(event.target.value)}
          aria-invalid={Boolean(fieldErrors.amount)}
          aria-describedby={fieldErrors.amount ? `${amountHintId} ${amountErrorId}` : amountHintId}
          className={inputClass}
        />
        <p id={amountHintId} className="text-sm text-slate-600">
          {fi.wager.amountHint(fi.sisu.amount(balanceSisu))}
        </p>
        {fieldErrors.amount && (
          <p id={amountErrorId} className="text-sm font-medium text-red-700">
            {fieldErrors.amount}
          </p>
        )}
      </div>

      <button
        type="submit"
        disabled={place.isPending}
        className="self-start rounded bg-sky-800 px-4 py-2 font-medium text-white disabled:opacity-50 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-sky-700"
      >
        {place.isPending ? fi.wager.submitting : fi.wager.submit}
      </button>

      <p role="status" className="font-medium text-emerald-900">
        {placed &&
          fi.wager.placed(
            fi.sisu.amount(placed.amountSisu),
            fi.outcome[placed.outcome],
            fi.sisu.amount(placed.potentialPayoutSisu),
          )}
      </p>
    </form>
  );
}
