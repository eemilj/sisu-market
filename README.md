# sisu-market
Faux Finnish based prediction market with fake money (sisu "points").

## Deployment

### HTTPS

Sisu Market requires HTTPS for all client-server traffic wherever it is exposed (constitution
principle V). The backend and the SPA speak plain HTTP inside the deployment, so:

- Terminate TLS at a reverse proxy or ingress in front of both the backend and the SPA, and redirect
  plain HTTP to HTTPS.
- Have the proxy send `Strict-Transport-Security` (HSTS).
- The session cookie is `httpOnly`, `Secure` and `SameSite=Strict`, so login only works over HTTPS in
  production.
- Plain `http://localhost` from `docker compose up` is the only supported plain-HTTP setup; browsers
  treat `localhost` as a secure context, so the `Secure` cookie still works there.

See `specs/001-sisu-prediction-market/research.md` section 10 for the decision and alternatives.

### Bootstrap resolver

Set `SISU_RESOLVER_USERNAME` and `SISU_RESOLVER_PASSWORD` in the environment (there is deliberately
no default password) so the backend provisions the resolver account at startup.
