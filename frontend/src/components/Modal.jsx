import { useEffect, useRef } from 'react'

/**
 * A dialog for a decision that needs confirming.
 *
 * Escape closes it, focus moves into it on open, and clicking the
 * backdrop dismisses it. Without those three things a modal is a trap
 * for anyone not using a mouse.
 */
export default function Modal({ title, onClose, children }) {
  const panel = useRef(null)

  useEffect(() => {
    const onKey = (e) => { if (e.key === 'Escape') onClose() }
    document.addEventListener('keydown', onKey)
    panel.current?.focus()

    // Stop the page behind from scrolling while the dialog is open.
    const previous = document.body.style.overflow
    document.body.style.overflow = 'hidden'

    return () => {
      document.removeEventListener('keydown', onKey)
      document.body.style.overflow = previous
    }
  }, [onClose])

  return (
    <div className="backdrop" onMouseDown={onClose}>
      <div
        className="modal"
        role="dialog"
        aria-modal="true"
        aria-label={title}
        tabIndex={-1}
        ref={panel}
        onMouseDown={(e) => e.stopPropagation()}
      >
        <h2>{title}</h2>
        {children}
      </div>
    </div>
  )
}
