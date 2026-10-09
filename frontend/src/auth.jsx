import { createContext, useCallback, useContext, useEffect, useState } from 'react'
import { Preferences } from '@capacitor/preferences'
import { api, setAuthToken, setUnauthorizedHandler, ApiError } from './api'
import { isNative } from './native'

/**
 * Sign-in state for the whole app.
 *
 * Token storage: the server issues an opaque random bearer token (it keeps only a hash and can
 * revoke it). On Android the token is kept in Capacitor Preferences (app-private storage that
 * other apps cannot read); in the browser in localStorage. Logout deletes it locally and asks
 * the server to revoke it.
 */
const KEY = 'jalmarg-token'

const store = {
  async get() {
    if (isNative) return (await Preferences.get({ key: KEY })).value
    try { return localStorage.getItem(KEY) } catch { return null }
  },
  async set(v) {
    if (isNative) return Preferences.set({ key: KEY, value: v })
    try { localStorage.setItem(KEY, v) } catch { /* private mode */ }
  },
  async clear() {
    if (isNative) return Preferences.remove({ key: KEY })
    try { localStorage.removeItem(KEY) } catch { /* private mode */ }
  }
}

const AuthContext = createContext(null)

export function AuthProvider({ children }) {
  const [user, setUser] = useState(null)
  const [ready, setReady] = useState(false)
  const [notice, setNotice] = useState('') // e.g. "session expired"

  const dropLocal = useCallback(async (why = '') => {
    setAuthToken(null)
    setUser(null)
    await store.clear()
    if (why) setNotice(why)
  }, [])

  // restore the session on start
  useEffect(() => {
    setUnauthorizedHandler(() => { dropLocal('SESSION_EXPIRED') })
    ;(async () => {
      const t = await store.get()
      if (t) {
        setAuthToken(t)
        try {
          setUser(await api.me())
        } catch (e) {
          // offline or server down: keep the token, try again later; revoked token: drop it
          if (e instanceof ApiError && e.kind === 'AUTH') await dropLocal()
        }
      }
      setReady(true)
    })()
  }, [dropLocal])

  const login = async (email, password) => {
    const res = await api.login(email, password)
    setAuthToken(res.token)
    await store.set(res.token)
    setUser(res.user)
    setNotice('')
    return res.user
  }

  /** Returns true if the server confirmed the session was revoked. Local sign-out always happens. */
  const logout = async () => {
    let revoked = false
    try {
      await api.logout()
      revoked = true
    } catch {
      revoked = false
    }
    await dropLocal()
    return revoked
  }

  const refresh = async () => {
    try { setUser(await api.me()) } catch { /* ignore */ }
  }

  const value = {
    user,
    ready,
    notice,
    clearNotice: () => setNotice(''),
    isAuthority: !!user && (user.role === 'AUTHORITY' || user.role === 'ADMIN'),
    login,
    logout,
    refresh
  }
  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}

export const useAuth = () => useContext(AuthContext)
