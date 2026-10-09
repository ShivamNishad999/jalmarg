import React from 'react'

/** Shows the error on screen instead of a blank page, with a reload button. */
export default class ErrorBoundary extends React.Component {
  constructor(props) {
    super(props)
    this.state = { error: null }
  }

  static getDerivedStateFromError(error) {
    return { error }
  }

  render() {
    if (!this.state.error) return this.props.children
    return (
      <div style={{ padding: 24, fontFamily: 'sans-serif', color: '#12222e' }}>
        <h2>JalMarg could not open this screen</h2>
        <p>Please send a screenshot of this message to the team.</p>
        <pre style={{ whiteSpace: 'pre-wrap', background: '#f1f1f1', padding: 12, borderRadius: 8, fontSize: 12 }}>
          {String(this.state.error && (this.state.error.stack || this.state.error.message || this.state.error))}
        </pre>
        <button onClick={() => { window.location.hash = ''; window.location.href = '/' }} style={{ padding: '10px 18px', borderRadius: 999 }}>
          Reload
        </button>
      </div>
    )
  }
}
