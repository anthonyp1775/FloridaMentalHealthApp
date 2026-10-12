import { createContext, useCallback, useContext, useEffect, useMemo, useState } from 'react'

const ThemeContext = createContext(null)

const THEME_KEY = 'fmh.theme'

/**
 * Light or dark, remembered per browser.
 *
 * This is an accessibility feature more than a preference one. Light
 * sensitivity is common - including as a side effect of several
 * psychiatric medications - and someone reading a provider directory at
 * 2am should not have to take a white page to do it.
 *
 * The system setting is the default, so the app matches the rest of the
 * device until someone deliberately overrides it. The override is what
 * gets stored; an untouched install keeps following the system.
 */
export function ThemeProvider({ children }) {
  const [theme, setTheme] = useState(() => {
    try {
      const stored = localStorage.getItem(THEME_KEY)
      if (stored === 'light' || stored === 'dark') return stored
    } catch {
      // Private browsing can throw on localStorage. Fall through.
    }
    return window.matchMedia?.('(prefers-color-scheme: dark)').matches
      ? 'dark'
      : 'light'
  })

  // The stylesheet keys off this attribute, so CSS owns all the colors.
  useEffect(() => {
    document.documentElement.setAttribute('data-theme', theme)
    try {
      localStorage.setItem(THEME_KEY, theme)
    } catch {
      // Not being able to remember the choice is survivable.
    }
  }, [theme])

  const toggle = useCallback(
    () => setTheme((t) => (t === 'dark' ? 'light' : 'dark')), [])

  const value = useMemo(() => ({ theme, toggle }), [theme, toggle])

  return <ThemeContext.Provider value={value}>{children}</ThemeContext.Provider>
}

export function useTheme() {
  const ctx = useContext(ThemeContext)
  if (!ctx) throw new Error('useTheme must be used inside ThemeProvider')
  return ctx
}
