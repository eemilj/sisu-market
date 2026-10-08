// Must run before React touches the DOM.
import './domPatch';
import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import { BrowserRouter } from 'react-router-dom';
import { AuthProvider } from './auth';
import { LiveProvider } from './live';
import ErrorBoundary from './components/ErrorBoundary';
import App from './App';
import './styles.css';

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    {/* Outermost safety net: covers the header and providers too. Pages have their own, resettable one. */}
    <ErrorBoundary resetKey="root">
    <BrowserRouter>
      <AuthProvider>
        <LiveProvider>
          <App />
        </LiveProvider>
      </AuthProvider>
    </BrowserRouter>
    </ErrorBoundary>
  </StrictMode>,
);
