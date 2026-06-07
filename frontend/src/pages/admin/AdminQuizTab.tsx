import { useEffect, useState } from 'react'
import { useTranslation } from 'react-i18next'
import {
  createOption,
  createQuestion,
  deleteOption,
  deleteQuestion,
  listQuiz,
  QuizOption,
  QuizQuestion,
  QuizQuestionDetail,
  setQuestionActive,
  updateOption,
  updateQuestion,
} from '../../api/admin'
import { errorBody } from '../../api/errors'
import { Button } from '../../components/Button'
import { Card } from '../../components/Card'
import AdminModal from './components/AdminModal'

const WEIGHT_FIELDS = [
  'weightLeader',
  'weightPlanner',
  'weightExpert',
  'weightCreative',
  'weightCommunicator',
  'weightTeamPlayer',
  'weightChallenger',
] as const

type WeightField = (typeof WEIGHT_FIELDS)[number]

export default function AdminQuizTab() {
  const { t } = useTranslation()
  const [items, setItems] = useState<QuizQuestionDetail[]>([])
  const [err, setErr] = useState<string | null>(null)
  const [creatingQ, setCreatingQ] = useState(false)
  const [editingQ, setEditingQ] = useState<QuizQuestion | null>(null)
  const [editingO, setEditingO] = useState<QuizOption | null>(null)
  const [addingOption, setAddingOption] = useState<string | null>(null)

  async function load() {
    try {
      setItems(await listQuiz())
      setErr(null)
    } catch (e) {
      setErr(errorBody(e)?.message ?? t('admin.quiz.errorLoad'))
    }
  }

  useEffect(() => {
    load()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  return (
    <div className="flex flex-col gap-4">
      <Card size="lg" className="p-4 flex flex-col tablet:flex-row tablet:items-center tablet:justify-between gap-3">
        <div>
          <h2 className="text-base font-semibold text-base">{t('admin.quiz.title')}</h2>
          <p className="text-xs text-muted">{t('admin.quiz.subtitle')}</p>
        </div>
        <Button type="button" variant="ghost" size="sm" className="admin-action-button" onClick={() => setCreatingQ(true)}>
          {t('admin.quiz.addQuestion')}
        </Button>
      </Card>

      {err && <p className="text-danger text-sm">{err}</p>}

      <ul className="flex flex-col gap-3">
        {items.map(({ question, options }) => (
          <li key={question.id}>
            <Card size="lg" className="p-4">
              <div className="flex flex-col desktop:flex-row desktop:items-start desktop:justify-between gap-3">
                <div className="min-w-0">
                  <div className="flex flex-wrap items-center gap-2">
                    <h3 className="text-base font-semibold text-base">{question.textEn}</h3>
                    <StatusBadge active={question.active} />
                  </div>
                  {question.textHe && (
                    <p className="text-sm text-muted mt-1" dir="rtl">{question.textHe}</p>
                  )}
                  <p className="text-xs text-muted mt-1">
                    {t('admin.quiz.orderSummary', { order: question.orderIndex, count: options.length })}
                  </p>
                </div>

                <div className="flex flex-wrap items-center gap-2 shrink-0">
                  <Button
                    type="button"
                    variant="secondary"
                    size="xs"
                    onClick={async () => {
                      await setQuestionActive(question.id, !question.active)
                      load()
                    }}
                  >
                    {question.active ? t('admin.quiz.deactivate') : t('admin.quiz.activate')}
                  </Button>
                  <Button type="button" variant="secondary" size="xs" onClick={() => setEditingQ(question)}>
                    {t('common.edit')}
                  </Button>
                  <Button
                    type="button"
                    variant="danger"
                    size="xs"
                    onClick={async () => {
                      if (!window.confirm(t('admin.quiz.confirmDeleteQuestion'))) return
                      await deleteQuestion(question.id)
                      load()
                    }}
                  >
                    {t('common.delete')}
                  </Button>
                </div>
              </div>

              <ul className="mt-4 flex flex-col gap-2">
                {options.map((option) => (
                  <li
                    key={option.id}
                    className="rounded-2xl border border-line bg-base px-3 py-3 text-sm"
                  >
                    <div className="flex flex-col desktop:flex-row desktop:items-start desktop:justify-between gap-3">
                      <div className="min-w-0">
                        <p className="font-medium text-base">{option.textEn}</p>
                        {option.textHe && <p className="text-xs text-muted mt-0.5" dir="rtl">{option.textHe}</p>}
                        <div className="mt-2 flex flex-wrap gap-1">
                          {WEIGHT_FIELDS.map((field) => (
                            <span key={field} className="text-[11px] rounded-full bg-surface-muted text-secondary px-2 py-0.5">
                              {t(`admin.quiz.weights.${field}`)}: {option[field].toFixed(2)}
                            </span>
                          ))}
                        </div>
                      </div>
                      <div className="flex gap-2 shrink-0">
                        <Button type="button" variant="secondary" size="xs" onClick={() => setEditingO(option)}>
                          {t('common.edit')}
                        </Button>
                        <Button
                          type="button"
                          variant="danger"
                          size="xs"
                          onClick={async () => {
                            if (!window.confirm(t('admin.quiz.confirmDeleteOption'))) return
                            await deleteOption(option.id)
                            load()
                          }}
                        >
                          {t('common.delete')}
                        </Button>
                      </div>
                    </div>
                  </li>
                ))}
              </ul>

              <Button type="button" variant="ghost" size="xs" className="mt-3" onClick={() => setAddingOption(question.id)}>
                {t('admin.quiz.addOption')}
              </Button>
            </Card>
          </li>
        ))}
        {items.length === 0 && (
          <li>
            <Card size="lg" className="p-8 text-center text-muted">{t('admin.quiz.empty')}</Card>
          </li>
        )}
      </ul>

      {creatingQ && (
        <QuestionEditorModal
          onClose={() => setCreatingQ(false)}
          onSave={async (body) => {
            await createQuestion(body)
            setCreatingQ(false)
            load()
          }}
        />
      )}
      {editingQ && (
        <QuestionEditorModal
          initial={editingQ}
          onClose={() => setEditingQ(null)}
          onSave={async (body) => {
            await updateQuestion(editingQ.id, body)
            setEditingQ(null)
            load()
          }}
        />
      )}
      {addingOption && (
        <OptionEditorModal
          onClose={() => setAddingOption(null)}
          onSave={async (body) => {
            await createOption(addingOption, body)
            setAddingOption(null)
            load()
          }}
        />
      )}
      {editingO && (
        <OptionEditorModal
          initial={editingO}
          onClose={() => setEditingO(null)}
          onSave={async (body) => {
            await updateOption(editingO.id, body)
            setEditingO(null)
            load()
          }}
        />
      )}
    </div>
  )
}

function QuestionEditorModal({
  initial,
  onClose,
  onSave,
}: {
  initial?: QuizQuestion
  onClose: () => void
  onSave: (body: { textEn: string; textHe?: string; orderIndex?: number; active?: boolean }) => Promise<void>
}) {
  const { t } = useTranslation()
  const [textEn, setTextEn] = useState(initial?.textEn ?? '')
  const [textHe, setTextHe] = useState(initial?.textHe ?? '')
  const [orderIndex, setOrderIndex] = useState<number | ''>(initial?.orderIndex ?? '')
  const [active, setActive] = useState(initial?.active ?? true)
  const [err, setErr] = useState<string | null>(null)
  const [saving, setSaving] = useState(false)

  async function submit() {
    setSaving(true)
    setErr(null)
    try {
      await onSave({
        textEn,
        textHe: textHe.trim() ? textHe : undefined,
        orderIndex: orderIndex === '' ? undefined : orderIndex,
        active,
      })
    } catch (e) {
      setErr(errorBody(e)?.message ?? t('admin.quiz.errorSaveQuestion'))
    } finally {
      setSaving(false)
    }
  }

  return (
    <AdminModal
      title={initial ? t('admin.quiz.editQuestion') : t('admin.quiz.addQuestion')}
      onClose={onClose}
      footer={<ModalActions onCancel={onClose} onSubmit={submit} submitting={saving} />}
    >
      <Labelled label={t('admin.quiz.fields.questionEn')}>
        <textarea value={textEn} onChange={(e) => setTextEn(e.target.value)} rows={3} className={inputCls} />
      </Labelled>
      <Labelled label={t('admin.quiz.fields.questionHe')}>
        <textarea value={textHe} onChange={(e) => setTextHe(e.target.value)} rows={3} className={inputCls} dir="rtl" />
      </Labelled>
      <Labelled label={t('admin.quiz.fields.order')}>
        <input
          type="number"
          value={orderIndex}
          onChange={(e) => setOrderIndex(e.target.value === '' ? '' : parseInt(e.target.value, 10))}
          className={inputCls}
        />
      </Labelled>
      <label className="flex items-center gap-2 text-sm text-base">
        <input type="checkbox" checked={active} onChange={(e) => setActive(e.target.checked)} />
        {t('admin.quiz.fields.active')}
      </label>
      {err && <p className="mt-2 text-sm text-danger">{err}</p>}
    </AdminModal>
  )
}

function OptionEditorModal({
  initial,
  onClose,
  onSave,
}: {
  initial?: QuizOption
  onClose: () => void
  onSave: (body: { textEn: string; textHe?: string } & Partial<Record<WeightField, number>>) => Promise<void>
}) {
  const { t } = useTranslation()
  const [textEn, setTextEn] = useState(initial?.textEn ?? '')
  const [textHe, setTextHe] = useState(initial?.textHe ?? '')
  const [weights, setWeights] = useState<Record<WeightField, string>>(() => {
    const base: Record<WeightField, string> = {} as Record<WeightField, string>
    for (const field of WEIGHT_FIELDS) base[field] = initial ? initial[field].toString() : '0'
    return base
  })
  const [err, setErr] = useState<string | null>(null)
  const [saving, setSaving] = useState(false)

  async function submit() {
    setSaving(true)
    setErr(null)
    try {
      const body: { textEn: string; textHe?: string } & Partial<Record<WeightField, number>> = {
        textEn,
        textHe: textHe.trim() ? textHe : undefined,
      }
      for (const field of WEIGHT_FIELDS) {
        body[field] = parseFloat(weights[field] || '0')
      }
      await onSave(body)
    } catch (e) {
      setErr(errorBody(e)?.message ?? t('admin.quiz.errorSaveOption'))
    } finally {
      setSaving(false)
    }
  }

  return (
    <AdminModal
      title={initial ? t('admin.quiz.editOption') : t('admin.quiz.addOption')}
      onClose={onClose}
      size="md"
      footer={<ModalActions onCancel={onClose} onSubmit={submit} submitting={saving} />}
    >
      <Labelled label={t('admin.quiz.fields.optionEn')}>
        <input value={textEn} onChange={(e) => setTextEn(e.target.value)} className={inputCls} />
      </Labelled>
      <Labelled label={t('admin.quiz.fields.optionHe')}>
        <input value={textHe} onChange={(e) => setTextHe(e.target.value)} className={inputCls} dir="rtl" />
      </Labelled>
      <div className="grid grid-cols-1 tablet:grid-cols-2 gap-2">
        {WEIGHT_FIELDS.map((field) => (
          <Labelled key={field} label={t(`admin.quiz.weights.${field}`)}>
            <input
              type="number"
              step="0.05"
              value={weights[field]}
              onChange={(e) => setWeights((state) => ({ ...state, [field]: e.target.value }))}
              className={inputCls}
            />
          </Labelled>
        ))}
      </div>
      {err && <p className="mt-2 text-sm text-danger">{err}</p>}
    </AdminModal>
  )
}

function StatusBadge({ active }: { active: boolean }) {
  const { t } = useTranslation()
  return (
    <span className={`text-xs px-2 py-0.5 rounded-md ${
      active ? 'bg-bubble-green-soft text-bubble-green' : 'bg-surface-muted text-secondary'
    }`}>
      {active ? t('admin.quiz.active') : t('admin.quiz.inactive')}
    </span>
  )
}

function ModalActions({
  onCancel,
  onSubmit,
  submitting,
}: {
  onCancel: () => void
  onSubmit: () => void
  submitting?: boolean
}) {
  const { t } = useTranslation()
  return (
    <div className="flex justify-end gap-2">
      <Button type="button" variant="ghost" size="sm" onClick={onCancel} disabled={submitting}>
        {t('common.cancel')}
      </Button>
      <Button type="button" variant="ghost" size="sm" className="admin-action-button" onClick={onSubmit} disabled={submitting}>
        {submitting ? t('admin.reason.submitting') : t('common.save')}
      </Button>
    </div>
  )
}

function Labelled({ label, children }: { label: string; children: React.ReactNode }) {
  return (
    <label className="block mb-3">
      <span className="text-xs font-semibold text-muted">{label}</span>
      <div className="mt-1">{children}</div>
    </label>
  )
}

const inputCls = 'w-full rounded-2xl border border-line bg-base px-3 py-2 text-sm focus-bubble'
