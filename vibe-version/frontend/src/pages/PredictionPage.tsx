import { useCallback, useEffect, useState, type FormEvent } from 'react';
import { Link, useParams } from 'react-router';
import { api, errorMessage } from '../api';
import { useAuth } from '../auth';
import { ErrorBox, Loading, SuccessBox } from '../components/Feedback';
import { OddsBar } from '../components/OddsBar';
import { StatusBadge } from '../components/StatusBadge';
import { formatDate, formatSisu } from '../format';
import { fi } from '../i18n/fi';
import { useLivePredictions } from '../live';
import type { Prediction, PredictionDetail, Side } from '../types';

export function PredictionPage() {
  const { id = '' } = useParams();
  const [detail, setDetail] = useState<PredictionDetail | null>(null);
  const [error, setError] = useState<string | null>(null);

  const load = useCallback(() => {
    api
      .prediction(id)
      .then((d) => {
        setDetail(d);
        setError(null);
      })
      .catch((e) => setError(errorMessage(e)));
  }, [id]);

  useEffect(load, [load]);

  useLivePredictions((p) => {
    if (p.id === id) {
      setDetail((cur) => (cur ? { ...cur, prediction: p } : cur));
      load(); // pick up the new wager in the recent list
    }
  });

  if (error && !detail) return <ErrorBox message={error} />;
  if (!detail) return <Loading />;
  const p = detail.prediction;

  return (
    <article className="detail" aria-labelledby="q-title">
      <Link to="/" className="back-link">
        {fi.detail.back}
      </Link>
      <div className="card">
        <div className="card-top">
          <StatusBadge status={p.status} outcome={p.outcome} />
          <span className="muted small">
            {fi.detail.createdBy}: <Link to={`/u/${p.creatorName}`}>{p.creatorName}</Link>
          </span>
        </div>
        <h1 id="q-title">{p.question}</h1>
        <OddsBar yesProbability={p.yesProbability} large />
        <dl className="facts">
          <div>
            <dt>{fi.list.pool}</dt>
            <dd>{formatSisu(p.totalPool)}</dd>
          </div>
          <div>
            <dt>{fi.side.YES}</dt>
            <dd>{formatSisu(p.yesPool)}</dd>
          </div>
          <div>
            <dt>{fi.side.NO}</dt>
            <dd>{formatSisu(p.noPool)}</dd>
          </div>
          <div>
            <dt>{p.status === 'OPEN' ? fi.list.closes : fi.list.closed}</dt>
            <dd>
              <time dateTime={p.closesAt}>{formatDate(p.closesAt)}</time>
            </dd>
          </div>
        </dl>
        {p.description && (
          <section>
            <h2>{fi.detail.description}</h2>
            <p className="prewrap">{p.description}</p>
          </section>
        )}
        <section>
          <h2>{fi.detail.criteria}</h2>
          <p className="prewrap">{p.criteria}</p>
        </section>
      </div>

      <div className="detail-side">
        <WagerForm prediction={p} />
        <ResolvePanel prediction={p} />
        <section className="card" aria-labelledby="recent-title">
          <h2 id="recent-title">{fi.detail.recentWagers}</h2>
          {detail.recentWagers.length === 0 ? (
            <p className="muted">{fi.detail.noWagers}</p>
          ) : (
            <ul className="plain-list">
              {detail.recentWagers.map((w) => (
                <li key={w.id}>
                  <Link to={`/u/${w.username}`}>{w.username}</Link>{' '}
                  <span className={w.side === 'YES' ? 'yes-text' : 'no-text'}>{fi.side[w.side]}</span>{' '}
                  {formatSisu(w.amount)}
                  <span className="muted small"> · {formatDate(w.createdAt)}</span>
                </li>
              ))}
            </ul>
          )}
        </section>
      </div>
    </article>
  );
}

function quote(stake: number, side: Side, p: Prediction): number {
  if (!Number.isInteger(stake) || stake <= 0) return 0;
  const sidePool = (side === 'YES' ? p.yesPool : p.noPool) + stake;
  return Math.floor((stake * (p.totalPool + stake)) / sidePool);
}

function WagerForm({ prediction: p }: { prediction: Prediction }) {
  const { user, setBalance } = useAuth();
  const [side, setSide] = useState<Side>('YES');
  const [amount, setAmount] = useState('100');
  const [error, setError] = useState<string | null>(null);
  const [success, setSuccess] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  if (p.status !== 'OPEN') {
    return p.status === 'CLOSED' ? <p className="card muted">{fi.wager.closedNote}</p> : null;
  }
  if (!user) {
    return (
      <p className="card">
        <Link to="/login">{fi.wager.loginPrompt}</Link>
      </p>
    );
  }

  const stake = Number(amount);
  const valid = /^\d+$/.test(amount) && stake > 0;

  const submit = async (e: FormEvent) => {
    e.preventDefault();
    setSuccess(null);
    if (!valid) return setError(fi.wager.invalidAmount);
    if (stake > user.balance) return setError(fi.wager.tooMuch);
    setBusy(true);
    setError(null);
    try {
      const res = await api.wager(p.id, side, stake);
      setBalance(res.balance);
      setSuccess(fi.wager.success(formatSisu(stake), fi.side[side]));
    } catch (err) {
      setError(errorMessage(err));
    } finally {
      setBusy(false);
    }
  };

  return (
    <form className="card" onSubmit={submit} aria-labelledby="wager-title" noValidate>
      <h2 id="wager-title">{fi.wager.title}</h2>
      <fieldset className="side-picker">
        <legend>{fi.wager.sideLegend}</legend>
        {(['YES', 'NO'] as Side[]).map((s) => (
          <label key={s} className={`side-option side-${s.toLowerCase()} ${side === s ? 'selected' : ''}`}>
            <input type="radio" name="side" value={s} checked={side === s} onChange={() => setSide(s)} />
            {fi.side[s]}
          </label>
        ))}
      </fieldset>
      <div className="field">
        <label htmlFor="amount">{fi.wager.amount}</label>
        <input
          id="amount"
          inputMode="numeric"
          value={amount}
          onChange={(e) => setAmount(e.target.value.trim())}
          aria-describedby="amount-hint"
          aria-invalid={!valid}
        />
        <p id="amount-hint" className="hint">
          {fi.wager.amountHint(formatSisu(user.balance))}
        </p>
      </div>
      <p className="quote" aria-live="polite">
        {fi.wager.potential}: <strong>{formatSisu(valid ? quote(stake, side, p) : 0)}</strong>
      </p>
      <ErrorBox message={error} />
      <SuccessBox message={success} />
      <button type="submit" className="btn btn-primary btn-block" disabled={busy}>
        {busy ? fi.wager.submitting : fi.wager.submit}
      </button>
    </form>
  );
}

function ResolvePanel({ prediction: p }: { prediction: Prediction }) {
  const { user } = useAuth();
  const [error, setError] = useState<string | null>(null);
  const [success, setSuccess] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  const allowed = user && (user.id === p.creatorId || user.role === 'ADMIN');
  if (!allowed || p.status !== 'CLOSED') return success ? <SuccessBox message={success} /> : null;

  const resolve = async (outcome: Side) => {
    if (!window.confirm(fi.resolve.confirm(fi.side[outcome]))) return;
    setBusy(true);
    setError(null);
    try {
      await api.resolve(p.id, outcome);
      setSuccess(fi.resolve.done);
    } catch (err) {
      setError(errorMessage(err));
    } finally {
      setBusy(false);
    }
  };

  return (
    <section className="card" aria-labelledby="resolve-title">
      <h2 id="resolve-title">{fi.resolve.title}</h2>
      <p className="muted">{fi.resolve.help}</p>
      <ErrorBox message={error} />
      <div className="row">
        {(['YES', 'NO'] as Side[]).map((s) => (
          <button key={s} type="button" className="btn btn-outline" disabled={busy} onClick={() => resolve(s)}>
            {fi.resolve.as(fi.side[s])}
          </button>
        ))}
      </div>
    </section>
  );
}
