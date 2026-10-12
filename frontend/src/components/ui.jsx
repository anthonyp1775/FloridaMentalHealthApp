/**
 * Small presentational pieces, kept in one file because each is a few
 * lines and splitting them would mean more imports than code.
 */

export function Button({ variant = 'primary', size, block, children, ...props }) {
  const classes = [
    'btn',
    variant === 'quiet' && 'btn--quiet',
    variant === 'danger' && 'btn--danger',
    size === 'small' && 'btn--small',
    block && 'btn--block'
  ].filter(Boolean).join(' ')

  return <button className={classes} {...props}>{children}</button>
}

export function Field({ label, hint, error, children, id }) {
  return (
    <div className="field">
      {label && <label htmlFor={id}>{label}</label>}
      {children}
      {hint && !error && <div className="field__hint">{hint}</div>}
      {error && <div className="field__error" role="alert">{error}</div>}
    </div>
  )
}

export function Input({ id, ...props }) {
  return <input id={id} className="input" {...props} />
}

export function Textarea({ id, ...props }) {
  return <textarea id={id} className="textarea" {...props} />
}

/** A select whose empty option reads as a real choice, not a placeholder. */
export function Select({ id, options = [], anyLabel = 'Any', ...props }) {
  return (
    <select id={id} className="select" {...props}>
      <option value="">{anyLabel}</option>
      {options.map((o) => (
        <option key={o.id} value={o.id}>{o.name}</option>
      ))}
    </select>
  )
}

export function Checkbox({ id, label, ...props }) {
  return (
    <div className="check">
      <input id={id} type="checkbox" {...props} />
      <label htmlFor={id}>{label}</label>
    </div>
  )
}

export function Tag({ children, variant }) {
  return (
    <span className={['tag', variant && `tag--${variant}`].filter(Boolean).join(' ')}>
      {children}
    </span>
  )
}

export function Card({ children, ...props }) {
  return <div className="card" {...props}>{children}</div>
}

export function Spinner({ label = 'Loading' }) {
  return (
    <div className="loading">
      <span className="spinner" aria-hidden="true" />
      <span>{label}</span>
    </div>
  )
}

/**
 * Errors explain what happened and what to do. They do not apologize
 * and they are never vague.
 */
export function Notice({ tone = 'neutral', children }) {
  const cls = {
    neutral: 'notice',
    error: 'notice notice--error',
    ok: 'notice notice--ok',
    waitlist: 'notice notice--waitlist'
  }[tone]

  return <div className={cls} role={tone === 'error' ? 'alert' : 'status'}>{children}</div>
}

/** An empty screen is an invitation to act, not a dead end. */
export function Empty({ title, children }) {
  return (
    <div className="empty">
      <h2>{title}</h2>
      {children}
    </div>
  )
}
