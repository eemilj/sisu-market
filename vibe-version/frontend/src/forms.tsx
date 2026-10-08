import { useState, type ReactNode } from 'react';
import { ApiError } from './api';

interface InputProps {
  id: string;
  'aria-invalid'?: true;
  'aria-describedby'?: string;
}

interface FieldProps {
  id: string;
  label: string;
  hint?: ReactNode;
  error?: string;
  /** Renders the input; spread the given props onto it so label, hint and error are linked to it. */
  children: (input: InputProps) => ReactNode;
}

/** Label + input + hint + error, wired up for screen readers. */
export function Field({ id, label, hint, error, children }: FieldProps) {
  const hintId = `${id}-hint`;
  const errorId = `${id}-error`;
  const describedBy = [error && errorId, hint && hintId].filter(Boolean).join(' ') || undefined;
  return (
    <div className={`field ${error ? 'has-error' : ''}`}>
      <label htmlFor={id}>{label}</label>
      {children({ id, 'aria-invalid': error ? true : undefined, 'aria-describedby': describedBy })}
      {error && <p className="field-error" id={errorId}>{error}</p>}
      {hint && <small id={hintId}>{hint}</small>}
    </div>
  );
}

/*
 * Errors revealed on blur must not move the layout while a pointer is pressed: pressing "Submit" blurs the
 * focused field, and if its error appeared immediately the button would shift away before the release, so the
 * click (and the submit) would be lost. Such reveals wait until the press is over.
 */
let pointerIsDown = false;
const afterRelease: Array<() => void> = [];
if (typeof window !== 'undefined') {
  window.addEventListener('pointerdown', () => { pointerIsDown = true; }, true);
  const release = () => {
    pointerIsDown = false;
    // Next task: after the click event that follows pointerup.
    setTimeout(() => afterRelease.splice(0).forEach((fn) => fn()));
  };
  window.addEventListener('pointerup', release, true);
  window.addEventListener('pointercancel', release, true);
}
const whenPointerReleased = (fn: () => void) => (pointerIsDown ? afterRelease.push(fn) : fn());

/**
 * Tracks which errors to show: a field's client-side error appears once the user has left the field or tried
 * to submit; server-reported field errors appear immediately and disappear when that field is edited.
 */
export function useFormErrors<F extends string>(formId: string, fields: readonly F[], clientErrors: Partial<Record<F, string>>) {
  const [submitted, setSubmitted] = useState(false);
  const [touched, setTouched] = useState<Partial<Record<F, boolean>>>({});
  const [server, setServer] = useState<Partial<Record<F, string>>>({});
  const [formError, setFormError] = useState<string | null>(null);

  const idFor = (f: F) => `${formId}-${f}`;
  const errorFor = (f: F): string | undefined => server[f] ?? (submitted || touched[f] ? clientErrors[f] : undefined);
  const focus = (f: F | undefined) => f && document.getElementById(idFor(f))?.focus();

  return {
    idFor,
    errorFor,
    formError,
    /** Spread onto an input: marks it touched on blur and drops its server error when edited. */
    track: (f: F) => ({
      onBlur: () => whenPointerReleased(() => setTouched((t) => ({ ...t, [f]: true }))),
      onInput: () => setServer((s) => {
        if (!(f in s)) return s;
        const next = { ...s };
        delete next[f];
        return next;
      }),
    }),
    /** Call at the start of submit. Returns false (and focuses the first problem) if the form is invalid. */
    validate(): boolean {
      setSubmitted(true);
      setFormError(null);
      const first = fields.find((f) => clientErrors[f]);
      focus(first);
      return !first;
    },
    /** Shows a failed submit: field errors next to their inputs, anything else at the top of the form. */
    fail(err: unknown) {
      if (err instanceof ApiError && err.fieldErrors.length > 0) {
        const byField = err.fieldMessages() as Partial<Record<F, string>>;
        setServer(byField);
        const known = fields.filter((f) => byField[f]);
        // Field errors for fields this form doesn't show still need to be visible somewhere.
        if (known.length < Object.keys(byField).length) setFormError(err.message);
        focus(known[0]);
      } else {
        setFormError(err instanceof Error ? err.message : 'Jotain meni pieleen. Yritä uudelleen.');
      }
    },
    /** Marks a specific field as wrong, e.g. after a 409 "username taken". */
    failField(f: F, message: string) {
      setServer((s) => ({ ...s, [f]: message }));
      focus(f);
    },
  };
}
