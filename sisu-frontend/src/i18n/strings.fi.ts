// All user-visible copy (FR-031, Constitution III). Components import from here; no hardcoded text.

export const fi = {
  app: {
    name: 'Sisu Market',
    skipToContent: 'Siirry sisältöön',
    mainNavigation: 'Päävalikko',
    loading: 'Ladataan…',
  },
  nav: {
    predictions: 'Ennusteet',
    createPrediction: 'Luo ennuste',
    leaderboard: 'Tulostaulukko',
    profile: 'Oma profiili',
    login: 'Kirjaudu sisään',
    register: 'Luo tili',
    logout: 'Kirjaudu ulos',
  },
  home: {
    title: 'Ennusta tulevaisuutta sisulla',
    intro:
      'Sisu Market on ennustemarkkina, jossa panostat leikkirahaa kyllä/ei-kysymyksiin ja kerrytät sisua osumalla oikeaan.',
  },
  notFound: {
    title: 'Sivua ei löytynyt',
    body: 'Etsimääsi sivua ei ole olemassa.',
    backHome: 'Takaisin etusivulle',
  },
  sisu: {
    unit: 'sisua',
    /** "1 250 sisua" – Finnish grouping with a non-breaking space. */
    amount: (amount: number) => `${new Intl.NumberFormat('fi-FI').format(amount)} sisua`,
    disclaimer:
      'Sisu on leikkirahaa. Sillä ei ole rahallista arvoa, eikä sitä voi ostaa tai vaihtaa rahaksi.',
  },
  outcome: {
    YES: 'Kyllä',
    NO: 'Ei',
  },
  predictionStatus: {
    OPEN: 'Avoin',
    CLOSED: 'Suljettu',
    RESOLVED: 'Ratkaistu',
    CANCELLED: 'Peruttu',
  },
  live: {
    reconnecting: 'Yhteys katkesi, yhdistetään uudelleen…',
  },
  errors: {
    NETWORK_ERROR: 'Palvelimeen ei saatu yhteyttä. Tarkista verkkoyhteys ja yritä uudelleen.',
    VALIDATION_FAILED: 'Tarkista lomakkeen tiedot.',
    UNAUTHENTICATED: 'Kirjaudu sisään jatkaaksesi.',
    FORBIDDEN: 'Sinulla ei ole oikeutta tähän toimintoon.',
    NOT_FOUND: 'Kohdetta ei löytynyt.',
    INSUFFICIENT_BALANCE: 'Saldosi ei riitä tähän panokseen.',
    USERNAME_TAKEN: 'Käyttäjätunnus on jo käytössä.',
    INVALID_CREDENTIALS: 'Virheellinen käyttäjätunnus tai salasana.',
    TOO_MANY_ATTEMPTS: 'Liian monta epäonnistunutta yritystä. Yritä uudelleen myöhemmin.',
    PREDICTION_NOT_OPEN: 'Tämä ennuste ei enää ota vastaan panoksia.',
    PREDICTION_NOT_CLOSED: 'Ennustetta ei voi ratkaista tässä tilassa.',
    UNSUPPORTED_MEDIA_TYPE: 'Pyyntö hylättiin.',
    INTERNAL_ERROR: 'Jokin meni vikaan. Yritä hetken kuluttua uudelleen.',
    unknown: 'Jokin meni vikaan. Yritä uudelleen.',
  },
} as const;

type ErrorCode = keyof typeof fi.errors;

/** Finnish message for an API error code, falling back to a generic message for unknown codes. */
export function errorMessage(code: string): string {
  return code in fi.errors && code !== 'unknown'
    ? (fi.errors[code as ErrorCode] as string)
    : fi.errors.unknown;
}
