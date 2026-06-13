import i18n from 'i18next'
import { initReactI18next } from 'react-i18next'
import ar from './ar.json'
import en from './en.json'
import he from './he.json'

export type Language = 'en' | 'he' | 'ar'

export const SUPPORTED_LANGUAGES: { code: Language; label: string; dir: 'ltr' | 'rtl' }[] = [
  { code: 'en', label: 'English', dir: 'ltr' },
  { code: 'he', label: 'עברית', dir: 'rtl' },
  { code: 'ar', label: 'العربية', dir: 'rtl' },
]

export function dirOf(lang: Language): 'ltr' | 'rtl' {
  return SUPPORTED_LANGUAGES.find((l) => l.code === lang)?.dir ?? 'ltr'
}

i18n
  .use(initReactI18next)
  .init({
    resources: {
      ar: { translation: ar },
      en: { translation: en },
      he: { translation: he },
    },
    lng: 'en',
    fallbackLng: 'en',
    interpolation: { escapeValue: false }, // React already escapes
    returnObjects: true,
  })

export default i18n
