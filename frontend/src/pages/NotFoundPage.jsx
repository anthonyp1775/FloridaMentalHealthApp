import { Link } from 'react-router-dom'
import CrisisBanner from '../components/CrisisBanner'
import { Empty } from '../components/ui'

export default function NotFoundPage() {
  return (
    <div className="shell">
      <CrisisBanner />
      <main className="page">
        <Empty title="That page does not exist">
          <p>
            The link may be out of date. <Link to="/">Search for a provider</Link>{' '}
            or see <Link to="/resources">statewide resources</Link>.
          </p>
        </Empty>
      </main>
    </div>
  )
}
