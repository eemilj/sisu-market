import { useState, type FormEvent } from 'react';
import { useNavigate } from 'react-router';
import { api, errorMessage } from '../api';
import { ErrorBox } from '../components/Feedback';
import { fi } from '../i18n/fi';

type Field = 'question' | 'criteria' | 'closesAt';

/** Value for a datetime-local input, in the browser's local time. */
function toLocalInput(d: Date): string {
  const off = d.getTimezoneOffset() * 60_000;
  return new Date(d.getTime() - off).toISOString().slice(0, 16);
}

export function CreatePage() {
  const navigate = useNavigate();
  const [question, setQuestion] = useState('');
  const [description, setDescription] = useState('');
  const [criteria, setCriteria] = useState('');
  const [closesAt, setClosesAt] = useState(toLocalInput(new Date(Date.now() + 7 * 24 * 3600_000)));
  const [fieldErrors, setFieldErrors] = useState<Partial<Record<Field, string>>>({});
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  const submit = async (e: FormEvent) => {
    e.preventDefault();
    const errs: Partial<Record<Field, string>> = {};
    if (!question.trim()) errs.question = fi.create.required;
    if (!criteria.trim()) errs.criteria = fi.create.required;
    const close = new Date(closesAt);
    if (!closesAt || Number.isNaN(close.getTime())) errs.closesAt = fi.create.required;
    else if (close.getTime() < Date.now() + 5 * 60_000) errs.closesAt = fi.create.pastDate;
    setFieldErrors(errs);
    if (Object.keys(errs).length) return;

    setBusy(true);
    setError(null);
    try {
      const p = await api.createPrediction({
        question: question.trim(),
        description: description.trim(),
        criteria: criteria.trim(),
        closesAt: close.toISOString(),
      });
      navigate(`/p/${p.id}`);
    } catch (err) {
      setError(errorMessage(err));
      setBusy(false);
    }
  };

  const describedBy = (f: Field, hint?: boolean) =>
    [hint ? `${f}-hint` : null, fieldErrors[f] ? `${f}-error` : null].filter(Boolean).join(' ') || undefined;

  return (
    <section className="narrow" aria-labelledby="create-title">
      <h1 id="create-title">{fi.create.title}</h1>
      <form className="card" onSubmit={submit} noValidate>
        <div className="field">
          <label htmlFor="question">{fi.create.question}</label>
          <input
            id="question"
            value={question}
            maxLength={200}
            onChange={(e) => setQuestion(e.target.value)}
            aria-invalid={!!fieldErrors.question}
            aria-describedby={describedBy('question', true)}
            required
          />
          <p id="question-hint" className="hint">
            {fi.create.questionHint}
          </p>
          {fieldErrors.question && (
            <p id="question-error" className="field-error">
              {fieldErrors.question}
            </p>
          )}
        </div>
        <div className="field">
          <label htmlFor="description">{fi.create.description}</label>
          <textarea id="description" rows={4} maxLength={2000} value={description} onChange={(e) => setDescription(e.target.value)} />
        </div>
        <div className="field">
          <label htmlFor="criteria">{fi.create.criteria}</label>
          <textarea
            id="criteria"
            rows={3}
            maxLength={1000}
            value={criteria}
            onChange={(e) => setCriteria(e.target.value)}
            aria-invalid={!!fieldErrors.criteria}
            aria-describedby={describedBy('criteria', true)}
            required
          />
          <p id="criteria-hint" className="hint">
            {fi.create.criteriaHint}
          </p>
          {fieldErrors.criteria && (
            <p id="criteria-error" className="field-error">
              {fieldErrors.criteria}
            </p>
          )}
        </div>
        <div className="field">
          <label htmlFor="closesAt">{fi.create.closesAt}</label>
          <input
            id="closesAt"
            type="datetime-local"
            value={closesAt}
            onChange={(e) => setClosesAt(e.target.value)}
            aria-invalid={!!fieldErrors.closesAt}
            aria-describedby={describedBy('closesAt', true)}
            required
          />
          <p id="closesAt-hint" className="hint">
            {fi.create.closesAtHint}
          </p>
          {fieldErrors.closesAt && (
            <p id="closesAt-error" className="field-error">
              {fieldErrors.closesAt}
            </p>
          )}
        </div>
        <ErrorBox message={error} />
        <button type="submit" className="btn btn-primary" disabled={busy}>
          {busy ? fi.create.submitting : fi.create.submit}
        </button>
      </form>
    </section>
  );
}
