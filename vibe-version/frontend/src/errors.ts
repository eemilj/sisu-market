import { formatDateTime, formatSisu, outcomeLabels } from './format';
import type { Outcome } from './api';

/** One field problem as reported by the API (bean validation). */
export interface FieldError {
  field: string;
  /** Constraint name, e.g. "Size", "NotBlank", "Pattern". */
  rule: string;
  params?: { min?: number; max?: number; value?: number };
}

export interface ErrorBody {
  status?: number;
  code?: string;
  message?: string;
  params?: Record<string, unknown>;
  fieldErrors?: FieldError[];
}

const NETWORK = 'Palvelimeen ei saatu yhteyttä. Tarkista verkkoyhteys ja yritä uudelleen.';

/** "45 sekunnin" / "15 minuutin" (genitive, for "… kuluttua"). */
function waitTime(seconds: unknown): string {
  const s = typeof seconds === 'number' && seconds > 0 ? seconds : 60;
  if (s < 60) return `${Math.ceil(s)} sekunnin`;
  return `${Math.ceil(s / 60)} minuutin`;
}

/** Finnish, actionable text for each API error code. Keys must match the backend's ErrorCode enum. */
const codeMessages: Record<string, (p: Record<string, unknown>) => string> = {
  MALFORMED_BODY: () => 'Pyyntö oli virheellinen. Päivitä sivu ja yritä uudelleen.',
  INVALID_LIMIT: () => 'Sivua ei voitu ladata. Päivitä sivu.',
  INVALID_CURSOR: () => 'Vanhempia tietoja ei voitu ladata. Päivitä sivu.',
  INVALID_FILTER: () => 'Suodatin ei kelpaa. Valitse suodatin uudelleen.',
  USERNAME_TAKEN: () => 'Käyttäjätunnus on jo käytössä. Valitse toinen tunnus.',
  EMAIL_TAKEN: () => 'Tällä sähköpostiosoitteella on jo tili. Kirjaudu sisään tai käytä toista osoitetta.',
  ACCOUNT_EXISTS: () => 'Käyttäjätunnus tai sähköpostiosoite on jo käytössä. Kokeile toista tai kirjaudu sisään.',
  TOO_MANY_LOGIN_ATTEMPTS: (p) =>
    `Liian monta epäonnistunutta kirjautumisyritystä. Tilisi on turvallisuussyistä hetken lukittuna – yritä uudelleen ${waitTime(p.retryAfterSeconds)} kuluttua.`,
  TOO_MANY_REGISTRATIONS: (p) =>
    `Tästä verkosta on luotu juuri liian monta tiliä. Yritä uudelleen ${waitTime(p.retryAfterSeconds)} kuluttua.`,
  INTERNAL_ERROR: (p) =>
    `Palvelimella tapahtui odottamaton virhe. Yritä hetken kuluttua uudelleen.${p.errorId ? ` Jos virhe toistuu, kerro ylläpidolle virhetunnus ${String(p.errorId).slice(0, 8)}.` : ''}`,
  PAYLOAD_TOO_LARGE: () => 'Lähettämäsi tiedot olivat liian suuret. Lyhennä tekstiä ja yritä uudelleen.',
  AMOUNT_TOO_LARGE: () => 'Panos on aivan liian suuri. Kirjoita pienempi summa.',
  INVALID_CREDENTIALS: () =>
    'Käyttäjätunnus tai salasana on väärin. Tarkista kirjoitusasu (myös Caps Lock) ja yritä uudelleen.',
  SESSION_INVALID: () => 'Istuntosi on vanhentunut. Kirjaudu uudelleen sisään.',
  USER_NOT_FOUND: () => 'Käyttäjää ei löytynyt. Tarkista käyttäjätunnus.',
  PREDICTION_NOT_FOUND: () => 'Ennustetta ei löytynyt. Palaa ennusteiden listaan.',
  CLOSING_DATE_INVALID: () => 'Sulkeutumisaika ei kelpaa. Valitse päivä ja kellonaika uudelleen.',
  CLOSING_TOO_SOON: (p) =>
    `Sulkeutumisajan pitää olla vähintään tunnin päässä${p.earliest ? ` (aikaisintaan ${formatDateTime(String(p.earliest))})` : ''}. Valitse myöhäisempi aika.`,
  CLOSING_TOO_FAR: (p) =>
    `Sulkeutumisajan pitää olla viiden vuoden sisällä${p.latest ? ` (viimeistään ${formatDateTime(String(p.latest))})` : ''}. Valitse aikaisempi aika.`,
  AMOUNT_NOT_POSITIVE: () => 'Panoksen pitää olla vähintään 1 Sisu.',
  AMOUNT_NOT_WHOLE: () => 'Panoksen pitää olla kokonaisluku – Sisuja ei voi jakaa osiin.',
  SIDE_INVALID: () => 'Valitse KYLLÄ tai EI.',
  QUOTE_PARAMS_REQUIRED: () => 'Valitse KYLLÄ tai EI ja kirjoita panoksen summa.',
  INSUFFICIENT_BALANCE: (p) =>
    typeof p.balance === 'number' && typeof p.required === 'number'
      ? `Saldosi ei riitä: sinulla on ${formatSisu(p.balance)}, mutta panos on ${formatSisu(p.required)}. Pienennä panosta.`
      : 'Saldosi ei riitä tähän panokseen. Pienennä panosta.',
  PREDICTION_CLOSED: () => 'Tämä ennuste on jo sulkeutunut, joten siihen ei voi enää panostaa.',
  ODDS_MOVED: (p) =>
    typeof p.payoutIfWin === 'number' && typeof p.minPayout === 'number'
      ? `Kertoimet muuttuivat: voitto olisi nyt ${formatSisu(p.payoutIfWin)} eikä ${formatSisu(p.minPayout)}. Tarkista uusi voittosumma ja vahvista panos uudelleen.`
      : 'Kertoimet muuttuivat. Tarkista uusi voittosumma ja vahvista panos uudelleen.',
  ODDS_CHANGED_RETRY: () => 'Kertoimet muuttuivat juuri panostaessasi. Yritä uudelleen.',
  REQUEST_ID_REUSED: () => 'Tämä panos on jo käsitelty. Päivitä sivu nähdäksesi tilanteen.',
  RESOLVE_NOT_ALLOWED: () => 'Vain ylläpitäjä tai ennusteen luoja voi ratkaista tämän ennusteen.',
  RESOLVE_NOT_ALLOWED_HAS_STAKE: () =>
    'Olet panostanut tähän ennusteeseen, joten ylläpitäjän pitää ratkaista se puolueettomasti.',
  PREDICTION_STILL_OPEN: (p) =>
    `Ennuste on vielä auki${p.closesAt ? ` ${formatDateTime(String(p.closesAt))} asti` : ''}. Sen voi ratkaista vasta, kun se on sulkeutunut.`,
  ALREADY_RESOLVED: (p) =>
    `Ennuste on jo ratkaistu${p.outcome ? ` (${outcomeLabels[p.outcome as Outcome]})` : ''}. Samaa ennustetta ei voi ratkaista kahdesti.`,
};

const fieldLabels: Record<string, string> = {
  username: 'Käyttäjätunnus',
  email: 'Sähköpostiosoite',
  password: 'Salasana',
  question: 'Kysymys',
  description: 'Kuvaus',
  resolutionCriteria: 'Ratkaisuperusteet',
  category: 'Aihealue',
  closesAt: 'Sulkeutumisaika',
  amount: 'Panos',
  side: 'Puoli',
  outcome: 'Lopputulos',
};

/** Specific wording where a generic rule message would be unhelpful. */
const fieldRuleMessages: Record<string, (f: FieldError) => string> = {
  'username:Pattern': () =>
    'Käyttäjätunnuksessa voi olla 3–20 merkkiä: kirjaimia a–z, numeroita ja alaviivoja (ei välilyöntejä eikä ääkkösiä).',
  'email:Email': () => 'Tarkista sähköpostiosoite. Sen pitää olla muotoa nimi@esimerkki.fi.',
  'password:MaxBytes': () =>
    'Salasana on liian pitkä: ääkköset ja erikoismerkit vievät enemmän tilaa. Lyhennä salasanaa (enintään 72 tavua, esim. 36 ä-kirjainta).',
  'password:SameAsUsername': () => 'Salasana ei saa olla sama kuin käyttäjätunnus. Valitse toinen salasana.',
  'question:Pattern': () => 'Kysymyksen pitää olla yhdellä rivillä, eikä siinä saa olla rivinvaihtoja tai ohjausmerkkejä.',
  'description:Pattern': () => 'Kuvauksessa on kiellettyjä ohjausmerkkejä. Kirjoita teksti uudelleen tai liitä se ilman muotoiluja.',
  'resolutionCriteria:Pattern': () =>
    'Ratkaisuperusteissa on kiellettyjä ohjausmerkkejä. Kirjoita teksti uudelleen tai liitä se ilman muotoiluja.',
  'amount:Digits': () => 'Panoksen pitää olla kokonaisluku – Sisuja ei voi jakaa osiin.',
  'amount:Positive': () => 'Panoksen pitää olla vähintään 1 Sisu.',
  'side:NotNull': () => 'Valitse KYLLÄ tai EI.',
  'outcome:NotNull': () => 'Valitse lopputulos: KYLLÄ tai EI.',
  'category:NotNull': () => 'Valitse aihealue.',
  'resolutionCriteria:NotBlank': () =>
    'Kirjoita ratkaisuperusteet: mistä lähteestä ja millä ehdolla ennuste ratkeaa.',
};

/** Finnish message for one field problem. */
export function fieldErrorMessage(f: FieldError): string {
  const specific = fieldRuleMessages[`${f.field}:${f.rule}`];
  if (specific) return specific(f);
  const label = fieldLabels[f.field] ?? f.field;
  const { min, max } = f.params ?? {};
  switch (f.rule) {
    case 'NotBlank':
    case 'NotNull':
      return `${label} on pakollinen tieto.`;
    case 'Size':
      if (min != null && max != null && min > 0) return `${label}: pituuden pitää olla ${min}–${max} merkkiä.`;
      if (max != null) return `${label} saa olla enintään ${max} merkkiä.`;
      return `${label}: tarkista pituus.`;
    default:
      return `${label}: tarkista arvo.`;
  }
}

/** Builds the Finnish user message for an error response. */
export function describeError(status: number, body: ErrorBody | null): string {
  if (status === 0) return NETWORK;
  if (body?.code === 'VALIDATION_FAILED' && body.fieldErrors?.length) {
    return body.fieldErrors.map(fieldErrorMessage).join(' ');
  }
  const byCode = body?.code ? codeMessages[body.code] : undefined;
  if (byCode) return byCode(body?.params ?? {});
  if (status === 401) return 'Kirjaudu sisään jatkaaksesi. Jos olit jo kirjautunut, istuntosi on vanhentunut.';
  if (status === 403) return 'Sinulla ei ole oikeutta tähän toimintoon.';
  if (status === 404) return 'Pyydettyä sisältöä ei löytynyt.';
  if (status === 413) return codeMessages.PAYLOAD_TOO_LARGE({});
  if (status === 429) return 'Liian monta pyyntöä. Odota hetki ja yritä uudelleen.';
  if (status >= 500) return 'Palvelimella tapahtui virhe. Yritä hetken kuluttua uudelleen.';
  return 'Jotain meni pieleen. Yritä uudelleen.';
}
