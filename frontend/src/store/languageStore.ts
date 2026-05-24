import { create } from 'zustand'
import { persist } from 'zustand/middleware'
import i18n, { dirOf, type Language } from '../i18n'

interface LanguageState {
  lang: Language
  setLang: (l: Language) => void
}

export const useLanguageStore = create<LanguageState>()(
  persist(
    (set) => ({
      lang: 'en',
      setLang: (lang) => {
        i18n.changeLanguage(lang)
        applyLangAttrs(lang)
        set({ lang })
      },
    }),
    { name: 'bubbleup-lang' }
  )
)

/** Apply lang + dir to <html>. Tailwind's `rtl:`/`ltr:` variants read this attribute. */
export function applyLangAttrs(lang: Language) {
  const root = document.documentElement
  root.lang = lang
  root.dir = dirOf(lang)
}
