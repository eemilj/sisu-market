import { useState, type FormEvent, type ReactNode } from 'react';
import { Link, useNavigate } from 'react-router';
import { ApiError } from '../api/client';
import { useAuth } from '../hooks/useAuth';
import { useCreatePrediction } from '../hooks/usePredictions';
import { errorMessage, fi } from '../i18n/strings.fi';

type Field = 'question' | 'description' | 'resolutionCriteria' | 'closingAt';
type FieldErrors = Partial<Record<Field, string>>;

/** Server error codes that belong to one form field, so they are shown next to it. */
const FIELD_FOR_CODE: Record<string, Field> = {
  QUESTION_REQUIRED: 'question',
  QUESTION_TOO_LONG: 'question',
  DESCRIPTION_TOO_LONG: 'description',
  RESOLUTION_CRITERIA_REQUIRED: 'resolutionCriteria',
  RESOLUTION_CRITERIA_TOO_LONG: 'resolutionCriteria',
  CLOSING_DATE_NOT_IN_FUTURE: 'closingAt',
};

/** Maximum lengths after trimming (FR-038); mirrors `PredictionService` on the backend. */
const MAX_LENGTH = {
  question: 200,
  description: 2000,
  resolutionCriteria: 1000,
} as const;

const inputClass =
  'rounded border border-slate-300 px-3 py-2 aria-[invalid=true]:border-red-600 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-sky-700';

/** `<input type="datetime-local">` value for a moment, in the browser's local time zone. */
function toLocalInputValue(date: Date): string {
  const local = new Date(date.getTime() - date.getTimezoneOffset() * 60_000);
  return local.toISOString().slice(0, 16);
}

interface Draft {
  question: string;
  description: string;
  resolutionCriteria: string;
  closingAt: string;
}

/** The same rules the backend enforces (FR-013, FR-014), checked first for instant feedback. */
function validate({ question, description, resolutionCriteria, closingAt }: Draft): FieldErrors {
  const errors: FieldErrors = {};
  if (!question.trim()) {
    errors.question = errorMessage('QUESTION_REQUIRED');
  } else if (question.trim().length > MAX_LENGTH.question) {
    errors.question = errorMessage('QUESTION_TOO_LONG');
  }
  if (description.trim().length > MAX_LENGTH.description) {
    errors.description = errorMessage('DESCRIPTION_TOO_LONG');
  }
  if (!resolutionCriteria.trim()) {
    errors.resolutionCriteria = errorMessage('RESOLUTION_CRITERIA_REQUIRED');
  } else if (resolutionCriteria.trim().length > MAX_LENGTH.resolutionCriteria) {
    errors.resolutionCriteria = errorMessage('RESOLUTION_CRITERIA_TOO_LONG');
  }
  const closing = closingAt ? new Date(closingAt) : null;
  if (!closing || Number.isNaN(closing.getTime()) || closing.getTime() <= Date.now()) {
    errors.closingAt = errorMessage('CLOSING_DATE_NOT_IN_FUTURE');
  }
  return errors;
}

/**
 * Create-a-prediction form (User Story 4; FR-012 through FR-014). Only for logged-in users; on
 * success the new prediction's detail page opens.
 */
export default function CreatePrediction() {
  const navigate = useNavigate();
  const { isLoggedIn, isLoading } = useAuth();
  const create = useCreatePrediction();
  const [question, setQuestion] = useState('');
  const [description, setDescription] = useState('');
  const [resolutionCriteria, setResolutionCriteria] = useState('');
  const [closingAt, setClosingAt] = useState('');
  const [fieldErrors, setFieldErrors] = useState<FieldErrors>({});

  if (isLoading) {
    return <p aria-live="polite">{fi.app.loading}</p>;
  }

  if (!isLoggedIn) {
    return (
      <section aria-labelledby="create-title" className="mx-auto max-w-2xl">
        <h1 id="create-title" className="text-2xl font-bold">
          {fi.createPrediction.title}
        </h1>
        <p className="mt-2">{fi.createPrediction.loginRequired}</p>
        <p className="mt-4 flex flex-wrap gap-2">
          <Link
            to="/login"
            className="rounded bg-sky-800 px-4 py-2 font-medium text-white focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-sky-700"
          >
            {fi.nav.login}
          </Link>
          <Link
            to="/register"
            className="rounded border border-sky-800 px-4 py-2 font-medium text-sky-800 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-sky-700"
          >
            {fi.nav.register}
          </Link>
        </p>
      </section>
    );
  }

  const submit = (event: FormEvent) => {
    event.preventDefault();
    const errors = validate({ question, description, resolutionCriteria, closingAt });
    setFieldErrors(errors);
    if (Object.keys(errors).length > 0) {
      return;
    }
    create.mutate(
      {
        question: question.trim(),
        description: description.trim(),
        resolutionCriteria: resolutionCriteria.trim(),
        closingAt: new Date(closingAt).toISOString(),
      },
      {
        onSuccess: (created) =>
          navigate(`/predictions/${encodeURIComponent(created.id)}`, { replace: true }),
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
    create.isError && !(create.error instanceof ApiError && FIELD_FOR_CODE[create.error.code])
      ? create.error instanceof ApiError
        ? errorMessage(create.error.code)
        : fi.errors.unknown
      : null;

  return (
    <section aria-labelledby="create-title" className="mx-auto max-w-2xl">
      <h1 id="create-title" className="text-2xl font-bold">
        {fi.createPrediction.title}
      </h1>
      <p className="mt-2 text-slate-700">{fi.createPrediction.intro}</p>

      <form onSubmit={submit} noValidate className="mt-6 flex flex-col gap-5">
        {bannerError && (
          <p
            role="alert"
            className="rounded border border-red-300 bg-red-50 px-3 py-2 text-red-900"
          >
            {bannerError}
          </p>
        )}

        <FormField
          id="prediction-question"
          label={fi.createPrediction.question}
          hint={fi.createPrediction.questionHint}
          error={fieldErrors.question}
          length={question.trim().length}
          maxLength={MAX_LENGTH.question}
          required
        >
          {(props) => (
            <input
              {...props}
              type="text"
              value={question}
              onChange={(event) => setQuestion(event.target.value)}
              className={inputClass}
            />
          )}
        </FormField>

        <FormField
          id="prediction-description"
          label={fi.createPrediction.description}
          hint={fi.createPrediction.descriptionHint}
          error={fieldErrors.description}
          length={description.trim().length}
          maxLength={MAX_LENGTH.description}
        >
          {(props) => (
            <textarea
              {...props}
              rows={4}
              value={description}
              onChange={(event) => setDescription(event.target.value)}
              className={inputClass}
            />
          )}
        </FormField>

        <FormField
          id="prediction-criteria"
          label={fi.createPrediction.resolutionCriteria}
          hint={fi.createPrediction.resolutionCriteriaHint}
          error={fieldErrors.resolutionCriteria}
          length={resolutionCriteria.trim().length}
          maxLength={MAX_LENGTH.resolutionCriteria}
          required
        >
          {(props) => (
            <textarea
              {...props}
              rows={3}
              value={resolutionCriteria}
              onChange={(event) => setResolutionCriteria(event.target.value)}
              className={inputClass}
            />
          )}
        </FormField>

        <FormField
          id="prediction-closing"
          label={fi.createPrediction.closingAt}
          hint={fi.createPrediction.closingAtHint}
          error={fieldErrors.closingAt}
          required
        >
          {(props) => (
            <input
              {...props}
              type="datetime-local"
              min={toLocalInputValue(new Date())}
              value={closingAt}
              onChange={(event) => setClosingAt(event.target.value)}
              className={inputClass}
            />
          )}
        </FormField>

        <button
          type="submit"
          disabled={create.isPending}
          className="self-start rounded bg-sky-800 px-4 py-2 font-medium text-white disabled:opacity-50 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-sky-700"
        >
          {create.isPending ? fi.createPrediction.submitting : fi.createPrediction.submit}
        </button>
      </form>
    </section>
  );
}

interface ControlProps {
  id: string;
  name: string;
  required?: boolean;
  'aria-invalid': boolean;
  'aria-describedby': string;
}

/**
 * Label, hint and error wiring shared by every field, so each control is fully announced. With
 * `maxLength`, a character counter joins the hint. It deliberately does not cap typing: a hard
 * `maxLength` attribute would silently cut off pasted text.
 */
function FormField({
  id,
  label,
  hint,
  error,
  required,
  length,
  maxLength,
  children,
}: {
  id: string;
  label: string;
  hint: string;
  error?: string;
  required?: boolean;
  length?: number;
  maxLength?: number;
  children: (props: ControlProps) => ReactNode;
}) {
  const overLimit = maxLength !== undefined && (length ?? 0) > maxLength;
  const hintId = `${id}-hint`;
  const errorId = `${id}-error`;
  return (
    <div className="flex flex-col gap-1">
      <label htmlFor={id} className="text-sm font-medium">
        {label}
        {required && <span aria-hidden="true"> *</span>}
      </label>
      {children({
        id,
        name: id,
        required,
        'aria-invalid': Boolean(error),
        'aria-describedby': error ? `${hintId} ${errorId}` : hintId,
      })}
      <p id={hintId} className="flex justify-between gap-4 text-sm text-slate-600">
        <span>{hint}</span>
        {maxLength !== undefined && (
          <span className={overLimit ? 'shrink-0 font-medium text-red-700' : 'shrink-0'}>
            {fi.createPrediction.characterCount(length ?? 0, maxLength)}
          </span>
        )}
      </p>
      {error && (
        <p id={errorId} className="text-sm font-medium text-red-700">
          {error}
        </p>
      )}
    </div>
  );
}
