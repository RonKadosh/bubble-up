import React from 'react'
import ReactDOM from 'react-dom/client'
// Brand fonts, self-hosted (bundled woff2 — no CDN call). Rubik = headings /
// buttons / bubble names; Assistant = body. Both cover Hebrew + Latin; Arabic
// falls through to the system Arabic UI fonts in index.css.
import '@fontsource-variable/rubik'
import '@fontsource-variable/assistant'
import App from './App'
import './index.css'

ReactDOM.createRoot(document.getElementById('root')!).render(
  <React.StrictMode>
    <App />
  </React.StrictMode>
)
