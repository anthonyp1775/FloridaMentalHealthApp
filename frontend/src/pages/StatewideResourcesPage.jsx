/**
 * Statewide Resources - the one page in this application whose content
 * is REAL, not sample data. The data itself lives in src/resources.js,
 * which CrisisBanner also reads, so the two can never drift apart.
 *
 * This page is public on purpose. Someone who needs 988 should not have
 * to create an account first, so it sits outside ProtectedRoute.
 */
import { Link } from 'react-router-dom'
import CrisisBanner from '../components/CrisisBanner'
import { useAuth } from '../context/AuthContext'
import { CRISIS_RESOURCES, SUPPORT_RESOURCES } from '../resources'

/** One resource. Phone becomes a tel: link, texting an sms: link. */
function Resource({ resource, urgent = false }) {
  return (
    <li className={urgent ? 'resource resource--urgent' : 'resource'}>
      <h3 className="resource__name">{resource.name}</h3>

      {resource.contact && (
        <p className="resource__contact">
          {resource.tel ? (
            <a href={`tel:${resource.tel}`}>{resource.contact}</a>
          ) : resource.sms ? (
            <a href={`sms:${resource.sms}`}>{resource.contact}</a>
          ) : (
            resource.contact
          )}
        </p>
      )}

      <p className="resource__note">{resource.note}</p>

      {resource.url && (
        <a
          className="small"
          href={resource.url}
          target="_blank"
          rel="noopener noreferrer"
        >
          {resource.url.replace(/^https?:\/\//, '')}
        </a>
      )}
    </li>
  )
}

export default function StatewideResourcesPage() {
  const { isSignedIn } = useAuth()

  return (
    <div className="shell">
      <CrisisBanner />

      <main className="page">
        <div className="page__head">
          <h1>Crisis and statewide help</h1>
          <p>
            These lines are staffed by real organizations and are free to
            call. They are not part of this directory and they do not
            require an account.
          </p>
        </div>

        <h2>If you need help right now</h2>
        <ul className="resources">
          {CRISIS_RESOURCES.map((r) => (
            <Resource key={r.name} resource={r} urgent />
          ))}
        </ul>

        <h2 style={{ marginTop: 'var(--s-7)' }}>Finding ongoing care and support</h2>
        <ul className="resources">
          {SUPPORT_RESOURCES.map((r) => (
            <Resource key={r.name} resource={r} />
          ))}
        </ul>

        <p className="sample-note">
          This application is a directory, not an emergency service, and
          no one monitors it. For an emergency, call{' '}
          <a href="tel:911">911</a>.{' '}
          {isSignedIn
            ? <Link to="/">Back to search</Link>
            : <Link to="/login">Sign in to search the directory</Link>}
        </p>
      </main>
    </div>
  )
}
