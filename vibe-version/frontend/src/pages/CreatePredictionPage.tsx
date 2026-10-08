import { useState, type FormEvent } from 'react';
import { Link, Navigate, useLocation, useNavigate } from 'react-router-dom';
import { api, ApiError, type Category } from '../api';
import { useAuth } from '../auth';
import { categoryLabels, formatDateTime } from '../format';
import { Field, useFormErrors } from '../forms';

const HOUR_MS = 60 * 60 * 1000;
const MAX_YEARS = 5;
const FIELDS = ['question', 'description', 'resolutionCriteria', 'category', 'closesAt'] as const;

/** Formats a Date as the local "YYYY-MM-DDTHH:mm" string that <input type="datetime-local"> expects. */
function toLocalInputValue(date: Date): string {
  const pad = (n: number) => String(n).padStart(2, '0');
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}T${pad(date.getHours())}:${pad(date.getMinutes())}`;
}

function lengthProblem(value: string, min: number, max: number, empty: string, what: string): string | undefined {
  const n = value.trim().length;
  if (n === 0) return empty;
  if (n < min) return `${what} on liian lyhyt: vähintään ${min} merkkiä (nyt ${n}).`;
  if (n > max) return `${what} on liian pitkä: enintään ${max} merkkiä (nyt ${n}).`;
  return undefined;
}

function closingProblem(value: string, now: Date): string | undefined {
  if (!value) return 'Valitse päivä ja kellonaika, jolloin panostaminen päättyy.';
  const closes = new Date(value);
  if (Number.isNaN(closes.getTime())) return 'Päivämäärä ei kelpaa. Valitse se kalenterista.';
  const earliest = new Date(now.getTime() + HOUR_MS);
  if (closes < now) return 'Sulkeutumisaika on jo mennyt. Valitse tuleva ajankohta.';
  if (closes < earliest) return `Sulkeutumisajan pitää olla vähintään tunnin päässä (aikaisintaan ${formatDateTime(earliest)}).`;
  const latest = new Date(now);
  latest.setFullYear(latest.getFullYear() + MAX_YEARS);
  if (closes > latest) return `Sulkeutumisajan pitää olla viiden vuoden sisällä (viimeistään ${formatDateTime(latest)}).`;
  return undefined;
}

export default function CreatePredictionPage() {
  const { user, loading } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();
  const [question, setQuestion] = useState('');
  const [description, setDescription] = useState('');
  const [criteria, setCriteria] = useState('');
  const [category, setCategory] = useState<Category | ''>('');
  const [closesAt, setClosesAt] = useState('');
  const [submitting, setSubmitting] = useState(false);

  const now = new Date();
  const form = useFormErrors('create', FIELDS, {
    question: lengthProblem(question, 10, 200, 'Kirjoita kysymys, johon voi vastata KYLLÄ tai EI.', 'Kysymys'),
    description: lengthProblem(description, 1, 2000, 'Kirjoita lyhyt kuvaus taustoista.', 'Kuvaus'),
    resolutionCriteria: lengthProblem(criteria, 10, 1000,
      'Kerro, mistä lähteestä ja millä ehdolla ennuste ratkeaa KYLLÄ.', 'Ratkaisuperuste'),
    category: category ? undefined : 'Valitse aihealue.',
    closesAt: closingProblem(closesAt, now),
  });

  if (loading) return <p className="muted">Ladataan…</p>;
  if (!user) return <Navigate to="/login" replace state={{ from: location.pathname }} />;

  async function submit(e: FormEvent) {
    e.preventDefault();
    if (!form.validate()) return;
    setSubmitting(true);
    try {
      const created = await api.createPrediction({
        question: question.trim(),
        description: description.trim(),
        resolutionCriteria: criteria.trim(),
        category: category as Category,
        // datetime-local is the user's local time; toISOString() converts it to UTC with a 'Z' offset.
        closesAt: new Date(closesAt).toISOString(),
      });
      navigate('/', { state: { created } });
    } catch (err) {
      setSubmitting(false);
      if (err instanceof ApiError && err.code?.startsWith('CLOSING_')) form.failField('closesAt', err.message);
      else form.fail(err);
    }
  }

  const max = new Date(now);
  max.setFullYear(max.getFullYear() + MAX_YEARS);

  return (
    <div className="form-wrap">
      <form className="form-card form-card-wide" onSubmit={submit} noValidate>
        <h1>Uusi ennuste</h1>
        <p className="muted form-intro">
          Kysy tulevaisuudesta kysymys, johon vastataan KYLLÄ tai EI. Muut voivat panostaa siihen Sisuilla, kunnes ennuste sulkeutuu.
        </p>
        {form.formError && <p className="alert" role="alert">{form.formError}</p>}

        <Field id={form.idFor('question')} label="Kysymys" error={form.errorFor('question')}
               hint={`${question.trim().length}/200 merkkiä. Muotoile niin, että vastaus on KYLLÄ tai EI.`}>
          {(input) => (
            <input {...input} {...form.track('question')} value={question} onChange={(e) => setQuestion(e.target.value)}
                   maxLength={200} placeholder="Ulottuuko Tampereen ratikka Lentävänniemeen ennen vuotta 2027?" autoFocus />
          )}
        </Field>

        <Field id={form.idFor('description')} label="Kuvaus" error={form.errorFor('description')}
               hint="Taustaa ja asiayhteys panostajille.">
          {(input) => (
            <textarea {...input} {...form.track('description')} value={description}
                      onChange={(e) => setDescription(e.target.value)} maxLength={2000} rows={3} />
          )}
        </Field>

        <Field id={form.idFor('resolutionCriteria')} label="Ratkaisuperusteet" error={form.errorFor('resolutionCriteria')}
               hint="Nimeä lähde ja kerro tarkasti, mikä lasketaan KYLLÄ-vastaukseksi.">
          {(input) => (
            <textarea {...input} {...form.track('resolutionCriteria')} value={criteria}
                      onChange={(e) => setCriteria(e.target.value)} maxLength={1000} rows={3}
                      placeholder="KYLLÄ, jos … lähteen … mukaan. Muuten EI." />
          )}
        </Field>

        <div className="form-row">
          <Field id={form.idFor('category')} label="Aihealue" error={form.errorFor('category')}>
            {(input) => (
              <select {...input} {...form.track('category')} value={category}
                      onChange={(e) => setCategory(e.target.value as Category)}>
                <option value="" disabled>Valitse…</option>
                {(Object.keys(categoryLabels) as Category[]).map((c) => (
                  <option key={c} value={c}>{categoryLabels[c]}</option>
                ))}
              </select>
            )}
          </Field>

          <Field id={form.idFor('closesAt')} label="Sulkeutuu" error={form.errorFor('closesAt')}
                 hint="Oma aikavyöhykkeesi. Vähintään tunnin päästä.">
            {(input) => (
              <input {...input} {...form.track('closesAt')} type="datetime-local" value={closesAt}
                     onChange={(e) => setClosesAt(e.target.value)}
                     min={toLocalInputValue(new Date(now.getTime() + HOUR_MS))} max={toLocalInputValue(max)} />
            )}
          </Field>
        </div>

        <div className="form-actions">
          <Link to="/" className="btn btn-ghost">Peruuta</Link>
          <button className="btn btn-primary" type="submit" disabled={submitting}>
            {submitting ? 'Luodaan…' : 'Luo ennuste'}
          </button>
        </div>
      </form>
    </div>
  );
}
