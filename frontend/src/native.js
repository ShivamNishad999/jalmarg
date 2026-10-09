// Android app (Capacitor) integration. Every function is a safe no-op in a normal browser.
import { Capacitor } from '@capacitor/core'
import { App } from '@capacitor/app'
import { StatusBar, Style } from '@capacitor/status-bar'
import { SplashScreen } from '@capacitor/splash-screen'
import { Network } from '@capacitor/network'

export const isNative = Capacitor.isNativePlatform()

/**
 * Called once from App.jsx.
 * - status bar in the app's navy colour
 * - hides the splash screen once React has drawn
 * - Android back button: go back a screen, exit only from the home map
 */
export function initNative(navigate, getPath) {
  if (!isNative) return () => {}
  StatusBar.setStyle({ style: Style.Dark }).catch(() => {})
  StatusBar.setBackgroundColor({ color: '#0b3b5c' }).catch(() => {})
  SplashScreen.hide().catch(() => {})

  const sub = App.addListener('backButton', () => {
    if (getPath() === '/') App.exitApp()
    else navigate(-1)
  })
  return () => { sub.then((s) => s.remove()).catch(() => {}) }
}

/** Subscribes to online/offline changes. Works in the browser too. */
export function watchNetwork(onChange) {
  if (isNative) {
    Network.getStatus().then((s) => onChange(s.connected)).catch(() => {})
    const sub = Network.addListener('networkStatusChange', (s) => onChange(s.connected))
    return () => { sub.then((s) => s.remove()).catch(() => {}) }
  }
  const on = () => onChange(true)
  const off = () => onChange(false)
  onChange(navigator.onLine)
  window.addEventListener('online', on)
  window.addEventListener('offline', off)
  return () => {
    window.removeEventListener('online', on)
    window.removeEventListener('offline', off)
  }
}

/** Native GPS with a proper Android permission prompt. Throws Error('denied') if refused. */
export async function nativePosition() {
  const { Geolocation } = await import('@capacitor/geolocation')
  let perm = await Geolocation.checkPermissions()
  if (perm.location !== 'granted') perm = await Geolocation.requestPermissions({ permissions: ['location'] })
  if (perm.location !== 'granted') throw new Error('denied')
  const p = await Geolocation.getCurrentPosition({ enableHighAccuracy: true, timeout: 15000 })
  return { lat: p.coords.latitude, lng: p.coords.longitude, accuracy: p.coords.accuracy }
}
