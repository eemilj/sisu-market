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
  auth: {
    registerTitle: 'Luo tili',
    registerIntro: 'Valitse käyttäjätunnus ja salasana. Saat heti aloitussaldon sisua.',
    loginTitle: 'Kirjaudu sisään',
    loginIntro: 'Kirjaudu sisään nähdäksesi saldosi.',
    username: 'Käyttäjätunnus',
    password: 'Salasana',
    passwordHint: 'Vähintään 8 merkkiä.',
    submitRegister: 'Luo tili',
    submitLogin: 'Kirjaudu sisään',
    submitting: 'Hetki…',
    toLogin: 'Onko sinulla jo tili? Kirjaudu sisään.',
    toRegister: 'Eikö sinulla ole vielä tiliä? Luo tili.',
    /** Shown under the form while the cooldown from too many attempts lasts. */
    retryAfterMinutes: (minutes: number) => `Yritä uudelleen noin ${minutes} minuutin kuluttua.`,
    balanceLabel: 'Saldosi',
    loggedInAs: (username: string) => `Kirjautuneena: ${username}`,
  },
  home: {
    title: 'Ennusta tulevaisuutta sisulla',
    intro:
      'Sisu Market on ennustemarkkina, jossa panostat leikkirahaa kyllä/ei-kysymyksiin ja kerrytät sisua osumalla oikeaan.',
  },
  leaderboard: {
    title: 'Tulostaulukko',
    intro: 'Käyttäjät parhaan sisu-saldon mukaan.',
    rank: 'Sija',
    user: 'Käyttäjä',
    balance: 'Saldo',
    empty: 'Ei käyttäjiä vielä.',
    loadFailed: 'Tulostaulukon lataaminen epäonnistui.',
  },
  predictions: {
    listTitle: 'Ennusteet',
    listIntro: 'Selaa kyllä/ei-kysymyksiä ja niiden tämänhetkisiä kertoimia.',
    browse: 'Selaa ennusteita',
    filterLabel: 'Näytä ennusteet tilan mukaan',
    filterAll: 'Kaikki',
    empty: 'Tällä suodattimella ei löytynyt ennusteita.',
    loadFailed: 'Ennusteiden lataaminen epäonnistui.',
    retry: 'Yritä uudelleen',
    closingAt: 'Sulkeutumisaika',
    totalWagered: 'Panostettu yhteensä',
    status: 'Tila',
    description: 'Kuvaus',
    resolutionCriteria: 'Ratkaisuperuste',
    creator: 'Luoja',
    deletedCreator: 'Poistettu käyttäjä',
    winningOutcome: 'Toteutunut lopputulos',
    backToList: 'Takaisin ennusteisiin',
    notFoundTitle: 'Ennustetta ei löytynyt',
    notFoundBody: 'Ennustetta ei ole olemassa, tai osoite on virheellinen.',
  },
  createPrediction: {
    title: 'Luo ennuste',
    intro:
      'Kirjoita kyllä/ei-kysymys, jonka lopputulos selviää myöhemmin. Muut voivat panostaa sisua kumpaan tahansa vaihtoehtoon sulkeutumisaikaan asti.',
    loginRequired: 'Kirjaudu sisään luodaksesi ennusteen.',
    question: 'Kysymys',
    questionHint: 'Kyllä/ei-kysymys, esim. "Sataako Helsingissä juhannuspäivänä?"',
    description: 'Kuvaus',
    descriptionHint: 'Vapaaehtoinen taustatieto kysymyksestä.',
    resolutionCriteria: 'Ratkaisuperuste',
    resolutionCriteriaHint: 'Miten ja mistä lähteestä lopputulos ratkaistaan.',
    closingAt: 'Sulkeutumisaika',
    closingAtHint: 'Panoksia voi asettaa tähän hetkeen asti.',
    submit: 'Luo ennuste',
    submitting: 'Luodaan…',
    /** "12 / 200 merkkiä" */
    characterCount: (length: number, max: number) => `${length} / ${max} merkkiä`,
  },
  wager: {
    title: 'Panosta',
    intro: 'Valitse lopputulos ja panoksen suuruus. Panos vähennetään saldostasi heti.',
    loginRequired: 'Kirjaudu sisään panostaaksesi tähän ennusteeseen.',
    notOpen: 'Tämä ennuste ei enää ota vastaan panoksia.',
    outcome: 'Lopputulos',
    amount: 'Panos (sisua)',
    /** "Saldosi: 1 000 sisua" */
    amountHint: (balance: string) => `Kokonaisluku, vähintään 1. Saldosi: ${balance}.`,
    submit: 'Aseta panos',
    submitting: 'Asetetaan…',
    outcomeRequired: 'Valitse kyllä tai ei.',
    /** Confirmation after a successful wager. */
    placed: (amount: string, outcome: string, payout: string) =>
      `Panos asetettu: ${amount}, ${outcome}. Mahdollinen voitto nyt: ${payout}.`,
  },
  profile: {
    title: 'Oma profiili',
    loginRequired: 'Kirjaudu sisään nähdäksesi profiilisi.',
    openWagersTitle: 'Avoimet panokset',
    openWagersIntro:
      'Mahdollinen voitto lasketaan nykyisistä panospotteista ja muuttuu, kun muut panostavat.',
    noOpenWagers: 'Sinulla ei ole avoimia panoksia.',
    loadFailed: 'Panosten lataaminen epäonnistui.',
    yourPick: 'Valintasi',
    stake: 'Panos',
    potentialPayout: 'Mahdollinen voitto',
    placedAt: 'Asetettu',
    unknownPrediction: 'Tuntematon ennuste',
  },
  odds: {
    title: 'Kertoimet',
    /** "63,2 %" */
    percent: (value: number) =>
      `${new Intl.NumberFormat('fi-FI', { minimumFractionDigits: 1, maximumFractionDigits: 1 }).format(value)} %`,
    /** Screen-reader summary of the whole bar. */
    summary: (yes: string, no: string) => `Kertoimet: kyllä ${yes}, ei ${no}`,
  },
  date: {
    /** "30.9.2026 klo 14.00" */
    dateTime: (iso: string) =>
      new Intl.DateTimeFormat('fi-FI', { dateStyle: 'short', timeStyle: 'short' }).format(
        new Date(iso),
      ),
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
    PREDICTION_NOT_FOUND: 'Ennustetta ei löytynyt.',
    QUESTION_REQUIRED: 'Kirjoita kysymys.',
    QUESTION_TOO_LONG: 'Kysymys saa olla enintään 200 merkkiä.',
    DESCRIPTION_TOO_LONG: 'Kuvaus saa olla enintään 2000 merkkiä.',
    RESOLUTION_CRITERIA_TOO_LONG: 'Ratkaisuperuste saa olla enintään 1000 merkkiä.',
    RESOLUTION_CRITERIA_REQUIRED: 'Kerro, miten lopputulos ratkaistaan.',
    CLOSING_DATE_NOT_IN_FUTURE: 'Sulkeutumisajan on oltava tulevaisuudessa.',
    INSUFFICIENT_BALANCE: 'Saldosi ei riitä tähän panokseen.',
    INVALID_WAGER_AMOUNT: 'Panoksen on oltava positiivinen kokonaisluku.',
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
