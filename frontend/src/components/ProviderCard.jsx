import { Link } from 'react-router-dom'
import { Tag } from './ui'
import {
  availability, availabilityLabel, canPrescribe,
  credentialLabel, formatWait
} from '../utils'

/**
 * One result in the search list.
 *
 * The left edge carries availability. Whether this person can actually
 * see you is the fact every other directory buries, so here it gets
 * structural weight rather than a badge in a corner - and it is always
 * paired with text, never color alone.
 */
export default function ProviderCard({ provider }) {
  const state = availability(provider)

  return (
    <Link to={`/providers/${provider.id}`} className={`provider provider--${state}`}>
      <h3 className="provider__name">{provider.fullName}</h3>

      <div className="provider__cred">
        {credentialLabel(provider.credential)}
        {canPrescribe(provider.credential) && ' · Can prescribe medication'}
      </div>

      <div className="provider__where">
        {provider.organization} · {provider.county} County
      </div>

      <div className={`provider__status provider__status--${state}`}>
        {availabilityLabel(provider)}
        {state !== 'closed' && (
          <span className="provider__wait"> · {formatWait(provider.typicalWaitDays)}</span>
        )}
      </div>

      <div className="tags">
        {provider.offersTelehealth && <Tag>Telehealth</Tag>}
        {canPrescribe(provider.credential) && <Tag variant="prescriber">Prescriber</Tag>}
      </div>
    </Link>
  )
}
