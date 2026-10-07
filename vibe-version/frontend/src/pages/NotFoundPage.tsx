import { Link } from 'react-router';
import { fi } from '../i18n/fi';

export function NotFoundPage() {
  return (
    <section className="narrow">
      <h1>{fi.notFound.title}</h1>
      <p>{fi.notFound.body}</p>
      <Link to="/" className="btn btn-primary">
        {fi.notFound.home}
      </Link>
    </section>
  );
}
