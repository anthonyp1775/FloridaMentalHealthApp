/**
 * Crisis resources, on every screen including before login.
 *
 * A directory cannot help someone in an acute crisis, and this app does
 * not pretend otherwise. The bar is steady and dark rather than red and
 * alarming - someone in distress needs a number that is always in the
 * same place, not a warning.
 *
 * Content comes from src/resources.js, the same module the resources
 * page renders, so the two can never drift apart.
 */
import { Link } from 'react-router-dom'
import { CRISIS_RESOURCES } from '../resources'

export default function CrisisBanner() {
  const lifeline = CRISIS_RESOURCES[0]
  const textLine = CRISIS_RESOURCES[1]

  return (
    <div className="crisis">
      <div className="crisis__inner">
        <span>
          <strong>In crisis?</strong>{' '}
          <a href={`tel:${lifeline.tel}`}>Call or text 988</a>
        </span>
        <span>
          <a href={`sms:${textLine.sms}`}>Text HOME to 741741</a>
        </span>
        <span className="crisis__note">
          This directory is not an emergency service. For an emergency, call{' '}
          <a href="tel:911">911</a>.
        </span>
        <Link to="/resources">All resources</Link>
      </div>
    </div>
  )
}
