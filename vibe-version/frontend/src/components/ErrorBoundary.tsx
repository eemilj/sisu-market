import { Component, type ErrorInfo, type ReactNode } from 'react';

interface Props {
  /** Changes on every navigation; a new value clears the error so other pages keep working. */
  resetKey: string;
  children: ReactNode;
}

interface State {
  error: Error | null;
}

/**
 * Last line of defence: if any page throws while rendering, show a usable Finnish page with a way out instead
 * of React unmounting everything and leaving a blank screen.
 */
export default class ErrorBoundary extends Component<Props, State> {
  state: State = { error: null };

  static getDerivedStateFromError(error: Error): State {
    return { error };
  }

  componentDidCatch(error: Error, info: ErrorInfo) {
    console.error('Sivun näyttäminen epäonnistui', error, info.componentStack);
  }

  componentDidUpdate(prev: Props) {
    if (this.state.error && prev.resetKey !== this.props.resetKey) this.setState({ error: null });
  }

  render() {
    if (!this.state.error) return this.props.children;
    return (
      <div className="error-page" role="alert">
        <h1>Hups, tätä sivua ei voitu näyttää</h1>
        <p className="muted">
          Sivulla tapahtui odottamaton virhe. Tietosi ovat tallessa – voit palata etusivulle tai ladata sivun uudelleen.
        </p>
        <div className="error-actions">
          <a className="btn btn-primary" href="/">Siirry etusivulle</a>
          <button className="btn btn-ghost" onClick={() => window.location.reload()}>Lataa sivu uudelleen</button>
        </div>
      </div>
    );
  }
}
