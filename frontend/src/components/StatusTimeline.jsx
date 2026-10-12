import { formatDateTime, statusLabel } from '../utils'

/**
 * The referral's audit trail.
 *
 * This earns its connecting line: the history genuinely is a sequence,
 * and each row says who moved the referral, from what, to what, and
 * when. The backend writes the status change and its history row in the
 * same transaction, so there is never a status without a row explaining
 * it - this component is just showing that guarantee.
 */
export default function StatusTimeline({ history = [] }) {
  if (history.length === 0) {
    return <p className="muted small">No status changes recorded yet.</p>
  }

  return (
    <ol className="timeline">
      {history.map((entry, i) => (
        <li key={i}>
          <div className="timeline__status">
            {entry.fromStatus
              ? `${statusLabel(entry.fromStatus)} → ${statusLabel(entry.toStatus)}`
              : statusLabel(entry.toStatus)}
          </div>

          {entry.note && <div className="small">{entry.note}</div>}

          <div className="timeline__meta">
            {formatDateTime(entry.createdAt)}
            {entry.changedBy && ` · ${entry.changedBy}`}
          </div>
        </li>
      ))}
    </ol>
  )
}
