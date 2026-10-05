import { useCallback, useEffect, useRef, useState } from 'react'

/**
 * Runs an async call and tracks loading / error / data.
 *
 * Every screen needs the same three states, and getting them subtly
 * wrong - showing a spinner forever, or stale data under a new error -
 * is the usual source of confusing UI.
 */
export function useFetch(fn, deps = [], { skip = false } = {}) {
  const [data, setData] = useState(null)
  const [error, setError] = useState(null)
  const [loading, setLoading] = useState(!skip)

  // Guards against a slow response overwriting a newer one.
  const latest = useRef(0)

  const run = useCallback(async () => {
    const call = ++latest.current
    setLoading(true)
    setError(null)
    try {
      const result = await fn()
      if (call === latest.current) setData(result)
    } catch (e) {
      if (call === latest.current) setError(e)
    } finally {
      if (call === latest.current) setLoading(false)
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, deps)

  useEffect(() => {
    if (!skip) run()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [run, skip])

  return { data, error, loading, reload: run, setData }
}

/** Delays a value so typing does not fire a request per keystroke. */
export function useDebounce(value, ms = 350) {
  const [debounced, setDebounced] = useState(value)

  useEffect(() => {
    const timer = setTimeout(() => setDebounced(value), ms)
    return () => clearTimeout(timer)
  }, [value, ms])

  return debounced
}
