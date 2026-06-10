import { FormEvent, useEffect, useState } from 'react'
import { Link, useLocation } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import { askHelp, getHelpQuestions, getHelpTopics, type HelpAskResponse, type HelpQuestion, type HelpTopic } from '../api/help'
import { Card } from '../components/Card'
import { Button } from '../components/Button'
import { PageShell, PageWidth, SectionLabel } from '../components/PageHeader'
import { ArrowLeftIcon, HelpIcon, SearchIcon, SparkleIcon } from '../components/Icons'
import { useLanguageStore } from '../store/languageStore'

export default function HelpPage() {
  const { t } = useTranslation()
  const location = useLocation()
  const lang = useLanguageStore((s) => s.lang)
  const [query, setQuery] = useState('')
  const [question, setQuestion] = useState('')
  const [topics, setTopics] = useState<HelpTopic[]>([])
  const [recent, setRecent] = useState<HelpQuestion[]>([])
  const [answer, setAnswer] = useState<HelpAskResponse | null>(null)
  const [loadingTopics, setLoadingTopics] = useState(true)
  const [loadingRecent, setLoadingRecent] = useState(true)
  const [asking, setAsking] = useState(false)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    let cancelled = false
    setLoadingTopics(true)
    const handle = window.setTimeout(async () => {
      try {
        const data = await getHelpTopics(query, location.pathname)
        if (!cancelled) setTopics(data)
      } catch {
        if (!cancelled) setError(t('help.errorLoad'))
      } finally {
        if (!cancelled) setLoadingTopics(false)
      }
    }, 180)
    return () => {
      cancelled = true
      window.clearTimeout(handle)
    }
  }, [query, location.pathname, t])

  useEffect(() => {
    let cancelled = false
    setLoadingRecent(true)
    const handle = window.setTimeout(async () => {
      try {
        const data = await getHelpQuestions(query)
        if (!cancelled) setRecent(data)
      } catch {
        if (!cancelled) setRecent([])
      } finally {
        if (!cancelled) setLoadingRecent(false)
      }
    }, 180)
    return () => {
      cancelled = true
      window.clearTimeout(handle)
    }
  }, [query])

  async function handleAsk(e: FormEvent) {
    e.preventDefault()
    const q = question.trim()
    if (!q) return
    setAsking(true)
    setError(null)
    try {
      const data = await askHelp(q, lang, location.pathname)
      setAnswer(data)
      setRecent(await getHelpQuestions(query))
    } catch {
      setError(t('help.errorAsk'))
    } finally {
      setAsking(false)
    }
  }

  function askFromTopic(topic: HelpTopic) {
    setQuestion(topic.title)
    setAnswer(null)
  }

  function openRecent(item: HelpQuestion) {
    setQuestion(item.question)
    setAnswer({
      answer: item.answer,
      source: item.source,
      topics: [],
      actions: [],
    })
  }

  function sourceLabel(source: HelpAskResponse['source']) {
    if (source === 'OPENAI') return t('help.sourceAssistant')
    if (source === 'CACHE') return t('help.sourceSaved')
    return t('help.sourceGuide')
  }

  return (
    <PageShell title={t('help.title')} subtitle={t('help.subtitle')} bodyClassName="py-4 tablet:py-6">
      <PageWidth>
        <div className="grid grid-cols-1 desktop:grid-cols-[minmax(0,1fr)_22rem] gap-4 items-start">
          <section className="space-y-4 min-w-0">
            <Card size="lg" className="p-4 tablet:p-5">
              <form onSubmit={handleAsk} className="space-y-3">
                <label className="block text-sm font-medium text-base" htmlFor="help-question">
                  {t('help.askLabel')}
                </label>
                <div className="flex flex-col tablet:flex-row gap-2">
                  <input
                    id="help-question"
                    type="search"
                    value={question}
                    onChange={(e) => setQuestion(e.target.value)}
                    placeholder={t('help.askPlaceholder')}
                    className="min-w-0 flex-1 border border-line bg-surface text-base rounded-xl px-3 py-2 focus:outline-none focus:border-primary-400"
                  />
                  <Button variant="deep" type="submit" disabled={asking || !question.trim()} leftIcon={<SparkleIcon className="w-4 h-4" />}>
                    {asking ? t('help.asking') : t('help.askButton')}
                  </Button>
                </div>
              </form>
            </Card>

            {error && (
              <div className="rounded-xl border border-danger/30 bg-danger-soft px-4 py-3 text-sm text-danger">
                {error}
              </div>
            )}

            {answer && (
              <Card size="lg" className="p-4 tablet:p-5">
                <div className="flex items-center justify-between gap-3 mb-3">
                  <div className="flex items-center gap-2">
                    <span className="grid place-items-center w-9 h-9 rounded-full bg-brand-gradient-strong text-on-brand">
                      <HelpIcon className="w-4 h-4" />
                    </span>
                    <div>
                      <h2 className="text-base font-semibold text-base">{t('help.answerTitle')}</h2>
                      <p className="text-xs text-muted">{sourceLabel(answer.source)}</p>
                    </div>
                  </div>
                </div>
                <p className="whitespace-pre-line text-sm leading-6 text-secondary">{answer.answer}</p>
                {answer.actions.length > 0 && (
                  <div className="flex flex-wrap gap-2 mt-4">
                    {answer.actions.slice(0, 3).map((a) => (
                      <ActionLink key={a.route} to={a.route} label={a.label} />
                    ))}
                  </div>
                )}
              </Card>
            )}

            <section>
              <SectionLabel className="mb-3">{t('help.topicsHeading')}</SectionLabel>
              {loadingTopics ? (
                <p className="text-sm text-muted">{t('common.loading')}</p>
              ) : topics.length === 0 ? (
                <p className="text-sm text-muted">{t('help.empty')}</p>
              ) : (
                <ul className="grid grid-cols-1 tablet:grid-cols-2 gap-3">
                  {topics.map((topic) => (
                    <li key={topic.id}>
                      <TopicCard topic={topic} onAsk={() => askFromTopic(topic)} />
                    </li>
                  ))}
                </ul>
              )}
            </section>
          </section>

          <aside className="space-y-4">
            <Card size="lg" className="p-4">
              <label className="block text-sm font-medium text-base mb-2" htmlFor="help-search">
                {t('help.searchLabel')}
              </label>
              <div className="relative">
                <SearchIcon className="absolute start-3 top-1/2 -translate-y-1/2 w-4 h-4 text-muted" />
                <input
                  id="help-search"
                  type="search"
                  value={query}
                  onChange={(e) => setQuery(e.target.value)}
                  placeholder={t('help.searchPlaceholder')}
                  className="w-full border border-line bg-surface text-base rounded-xl ps-9 pe-3 py-2 focus:outline-none focus:border-primary-400"
                />
              </div>
            </Card>

            <Card size="lg" className="p-4">
              <SectionLabel className="mb-3">{t('help.quickHeading')}</SectionLabel>
              <div className="flex flex-col gap-2">
                <ActionLink to="/dashboard" label={t('nav.home')} />
                <ActionLink to="/academy" label={t('nav.academy')} />
                <ActionLink to="/groups" label={t('nav.myBubbles')} />
                <ActionLink to="/experts" label={t('nav.experts')} />
                <ActionLink to="/settings" label={t('nav.settings')} />
              </div>
            </Card>

            <Card size="lg" className="p-4">
              <SectionLabel className="mb-3">{t('help.recentHeading')}</SectionLabel>
              {loadingRecent ? (
                <p className="text-sm text-muted">{t('common.loading')}</p>
              ) : recent.length === 0 ? (
                <p className="text-sm text-muted">{t('help.recentEmpty')}</p>
              ) : (
                <ul className="space-y-2">
                  {recent.map((item) => (
                    <li key={item.id}>
                      <button
                        type="button"
                        onClick={() => openRecent(item)}
                        className="w-full text-start rounded-lg border border-line bg-surface px-3 py-2 hover:bg-surface-hover bubble-pop"
                      >
                        <span className="block text-sm font-medium text-base line-clamp-2">{item.question}</span>
                        <span className="block text-xs text-muted mt-1">{sourceLabel(item.source)}</span>
                      </button>
                    </li>
                  ))}
                </ul>
              )}
            </Card>
          </aside>
        </div>
      </PageWidth>
    </PageShell>
  )
}

function TopicCard({ topic, onAsk }: { topic: HelpTopic; onAsk: () => void }) {
  const { t } = useTranslation()
  return (
    <Card size="md" className="p-4 h-full flex flex-col gap-3">
      <div>
        <div className="text-xs text-bubble-magenta font-semibold mb-1">{topic.category}</div>
        <h3 className="text-base font-semibold text-base">{topic.title}</h3>
        <p className="text-sm text-muted mt-1 line-clamp-3">{topic.summary}</p>
      </div>

      <ol className="space-y-1 text-sm text-secondary">
        {topic.steps.slice(0, 3).map((step, idx) => (
          <li key={step} className="flex gap-2">
            <span className="text-xs font-semibold text-muted pt-0.5">{idx + 1}</span>
            <span>{step}</span>
          </li>
        ))}
      </ol>

      <div className="mt-auto flex flex-wrap gap-2 pt-1">
        {topic.actions.slice(0, 2).map((a) => (
          <ActionLink key={a.route} to={a.route} label={a.label} />
        ))}
        <button
          type="button"
          onClick={onAsk}
          className="inline-flex items-center justify-center rounded-full px-3 py-1.5 text-xs font-medium text-secondary hover:bg-surface-hover bubble-pop"
        >
          {t('help.askThis')}
        </button>
      </div>
    </Card>
  )
}

function ActionLink({ to, label }: { to: string; label: string }) {
  return (
    <Link
      to={to}
      className="inline-flex items-center justify-center gap-1.5 rounded-full border border-line bg-surface px-3 py-1.5 text-xs font-medium text-base hover:border-line-strong hover:bg-surface-hover bubble-pop"
    >
      {label}
      <ArrowLeftIcon className="w-3.5 h-3.5 rotate-180 rtl:rotate-0" />
    </Link>
  )
}
