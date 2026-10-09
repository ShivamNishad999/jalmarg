import React from 'react'
import ReactDOM from 'react-dom/client'
import { BrowserRouter } from 'react-router-dom'
import 'leaflet/dist/leaflet.css'
import './styles.css'
import App from './App'
import { LangProvider } from './i18n'
import { AuthProvider } from './auth'
import ErrorBoundary from './components/ErrorBoundary'

const inApp = !!window.Capacitor?.isNativePlatform?.()

if ('serviceWorker' in navigator) {
  if (inApp) {
    // The Android app ships its files inside the APK; a service worker there can serve a blank page.
    navigator.serviceWorker.getRegistrations().then((rs) => rs.forEach((r) => r.unregister())).catch(() => {})
  } else {
    import('virtual:pwa-register').then(({ registerSW }) => registerSW({ immediate: true })).catch(() => {})
  }
}

ReactDOM.createRoot(document.getElementById('root')).render(
  <React.StrictMode>
    <ErrorBoundary>
      <LangProvider>
        <AuthProvider>
          <BrowserRouter>
            <App />
          </BrowserRouter>
        </AuthProvider>
      </LangProvider>
    </ErrorBoundary>
  </React.StrictMode>
)
window.__jalmargStarted = true
