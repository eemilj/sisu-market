import { useState, type FormEvent } from 'react';
import { Link, Navigate, useLocation, useNavigate } from 'react-router-dom';
import { api, type Category } from '../api';
import { useAuth } from '../auth';
import { categoryLabels } from '../format';

const HOUR_MS = 60 * 60 * 1000;
const MAX_YEARS = 5;

/** Formats a Date as the local "YYYY-MM-DDTHH:mm" string that <input type="datetime-local"> expects. */
function toLocalInputValue(date: Date): string {
  const pad = (n: number) => String(n).padStart(2, '0');
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}T${pad(date.getHours())}:${pad(date.getMinutes())}`;
}

interface Fields {
  question: string;
  description: string;
  resolutionCriteria: string;
  category: Category | '';
  closesAt: string;
}

/** Mirrors the backend rules so most mistakes are caught before submitting. The server still has the final say. */
function validate(f: Fields, now: Date): string[] {
  const errors: string[] = [];
  const question = f.question.trim();
  if (question.length < 10 || question.length > 200) errors.push('Question must be 10-200 characters');
  if (!f.description.trim()) errors.push('Description is required');
  const criteria = f.resolutionCriteria.trim();
  if (criteria.length < 10 || criteria.length > 1000) errors.push('Resolution criteria must be 10-1000 characters');
  if (!f.category) errors.push('Category is required');

  if (!f.closesAt) {
    errors.push('Closing date is required');
  } else {
    const closes = new Date(f.closesAt);
    if (Number.isNaN(closes.getTime())) {
      errors.push('Closing date is not a valid date');
    } else if (closes.getTime() < now.getTime() + HOUR_MS) {
      errors.push('Closing date must be at least 1 hour in the future');
    } else {
      const max = new Date(now);
      max.setFullYear(max.getFullYear() + MAX_YEARS);
      if (closes > max) errors.push(`Closing date must be within ${MAX_YEARS} years`);
    }
  }
  return errors;
}

export default function CreatePredictionPage() {
  const { user, loading } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();
  const [fields, setFields] = useState<Fields>({
    question: '',
    description: '',
    resolutionCriteria: '',
    category: '',
    closesAt: '',
  });
  const [errors, setErrors] = useState<string[]>([]);
  const [submitting, setSubmitting] = useState(false);

  if (loading) return <p className="muted">Loading…</p>;
  if (!user) return <Navigate to="/login" replace state={{ from: location.pathname }} />;

  const set = <K extends keyof Fields>(key: K) => (e: { target: { value: string } }) =>
    setFields((f) => ({ ...f, [key]: e.target.value }));

  async function handleSubmit(e: FormEvent) {
    e.preventDefault();
    const problems = validate(fields, new Date());
    setErrors(problems);
    if (problems.length > 0) return;

    setSubmitting(true);
    try {
      const created = await api.createPrediction({
        question: fields.question.trim(),
        description: fields.description.trim(),
        resolutionCriteria: fields.resolutionCriteria.trim(),
        category: fields.category as Category,
        // datetime-local is the user's local time; toISOString() converts it to UTC with a 'Z' offset.
        closesAt: new Date(fields.closesAt).toISOString(),
      });
      navigate('/', { state: { created } });
    } catch (err) {
      setErrors([err instanceof Error ? err.message : 'Something went wrong']);
      setSubmitting(false);
    }
  }

  const now = new Date();
  const max = new Date(now);
  max.setFullYear(max.getFullYear() + MAX_YEARS);

  return (
    <div className="form-wrap">
      <form className="form-card form-card-wide" onSubmit={handleSubmit} noValidate>
        <h1>New prediction</h1>
        <p className="muted form-intro">
          Ask a yes/no question about the future. Others will trade on it with Sisu until it closes.
        </p>

        {errors.length > 0 && (
          <div className="alert" role="alert">
            {errors.length === 1 ? (
              errors[0]
            ) : (
              <ul>
                {errors.map((msg) => (
                  <li key={msg}>{msg}</li>
                ))}
              </ul>
            )}
          </div>
        )}

        <label>
          Question
          <input
            value={fields.question}
            onChange={set('question')}
            maxLength={200}
            placeholder="Will Tampere's tram reach Lentävänniemi before 2027?"
            autoFocus
            required
          />
          <small>{fields.question.trim().length}/200, minimum 10 characters. Phrase it so the answer is yes or no.</small>
        </label>

        <label>
          Description
          <textarea
            value={fields.description}
            onChange={set('description')}
            maxLength={2000}
            rows={3}
            placeholder="Background and context for traders."
            required
          />
        </label>

        <label>
          Resolution criteria
          <textarea
            value={fields.resolutionCriteria}
            onChange={set('resolutionCriteria')}
            maxLength={1000}
            rows={3}
            placeholder="Resolves YES if … according to … Otherwise NO."
            required
          />
          <small>Name the source and what exactly counts as YES.</small>
        </label>

        <div className="form-row">
          <label>
            Category
            <select value={fields.category} onChange={set('category')} required>
              <option value="" disabled>
                Choose…
              </option>
              {(Object.keys(categoryLabels) as Category[]).map((c) => (
                <option key={c} value={c}>
                  {categoryLabels[c]}
                </option>
              ))}
            </select>
          </label>

          <label>
            Closes at
            <input
              type="datetime-local"
              value={fields.closesAt}
              onChange={set('closesAt')}
              min={toLocalInputValue(new Date(now.getTime() + HOUR_MS))}
              max={toLocalInputValue(max)}
              required
            />
            <small>Your local time, at least 1 hour from now</small>
          </label>
        </div>

        <div className="form-actions">
          <Link to="/" className="btn btn-ghost">
            Cancel
          </Link>
          <button className="btn btn-primary" type="submit" disabled={submitting}>
            {submitting ? 'Creating…' : 'Create prediction'}
          </button>
        </div>
      </form>
    </div>
  );
}
