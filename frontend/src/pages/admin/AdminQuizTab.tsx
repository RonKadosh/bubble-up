import { useEffect, useState } from 'react'
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

const WEIGHT_LABELS: Record<WeightField, string> = {
  weightLeader: 'Leader',
  weightPlanner: 'Planner',
  weightExpert: 'Expert',
  weightCreative: 'Creative',
  weightCommunicator: 'Communicator',
  weightTeamPlayer: 'Team player',
  weightChallenger: 'Challenger',
}

export default function AdminQuizTab() {
  const [items, setItems] = useState<QuizQuestionDetail[]>([])
  const [err, setErr] = useState<string | null>(null)
  const [creatingQ, setCreatingQ] = useState(false)
  const [editingQ, setEditingQ] = useState<QuizQuestion | null>(null)
  const [editingO, setEditingO] = useState<QuizOption | null>(null)
  const [addingOption, setAddingOption] = useState<string | null>(null)

  async function load() {
    try {
      setItems(await listQuiz())
    } catch (e) {
      setErr(String(e))
    }
  }
  useEffect(() => {
    load()
  }, [])

  return (
    <div className="flex flex-col gap-4">
      <div className="flex justify-end">
        <button
          onClick={() => setCreatingQ(true)}
          className="px-3 py-1.5 rounded-full bg-indigo-600 text-on-brand text-sm"
        >
          + Add question
        </button>
      </div>
      {err && <p className="text-red-600 text-sm">{err}</p>}
      <ul className="flex flex-col gap-3">
        {items.map(({ question: q, options }) => (
          <li key={q.id} className="rounded-2xl border border-line bg-surface p-4">
            <div className="flex items-start justify-between gap-2">
              <div>
                <div className="text-base font-medium">{q.textEn}</div>
                {q.textHe && (
                  <div className="text-sm text-secondary mt-0.5" dir="rtl">{q.textHe}</div>
                )}
                <div className="text-xs text-secondary mt-1">
                  Order #{q.orderIndex} · {q.active ? 'Active' : 'Inactive'}
                </div>
              </div>
              <div className="flex items-center gap-2 shrink-0">
                <button
                  onClick={async () => {
                    await setQuestionActive(q.id, !q.active)
                    load()
                  }}
                  className={`text-xs px-3 py-1 rounded-full border ${
                    q.active ? 'border-line text-base' : 'border-indigo-300 text-indigo-700'
                  }`}
                >
                  {q.active ? 'Deactivate' : 'Activate'}
                </button>
                <button
                  onClick={() => setEditingQ(q)}
                  className="text-xs px-3 py-1 rounded-full border border-line"
                >
                  Edit
                </button>
                <button
                  onClick={async () => {
                    if (!confirm('Delete this question and all its responses?')) return
                    await deleteQuestion(q.id)
                    load()
                  }}
                  className="text-xs px-3 py-1 rounded-full border border-red-300 text-red-600"
                >
                  Delete
                </button>
              </div>
            </div>
            <ul className="mt-3 flex flex-col gap-1">
              {options.map((o) => (
                <li
                  key={o.id}
                  className="flex items-center justify-between rounded-xl border border-line bg-base px-3 py-1.5 text-sm"
                >
                  <div>
                    <div>{o.textEn}</div>
                    {o.textHe && <div className="text-xs text-secondary" dir="rtl">{o.textHe}</div>}
                    <div className="text-[10px] text-secondary mt-0.5">
                      {WEIGHT_FIELDS.map((w) => `${WEIGHT_LABELS[w]}:${o[w].toFixed(2)}`).join('  ')}
                    </div>
                  </div>
                  <div className="flex gap-1">
                    <button
                      onClick={() => setEditingO(o)}
                      className="text-xs px-2 py-0.5 rounded-full border border-line"
                    >
                      Edit
                    </button>
                    <button
                      onClick={async () => {
                        if (!confirm('Delete this option?')) return
                        await deleteOption(o.id)
                        load()
                      }}
                      className="text-xs px-2 py-0.5 rounded-full border border-red-300 text-red-600"
                    >
                      Delete
                    </button>
                  </div>
                </li>
              ))}
            </ul>
            <button
              onClick={() => setAddingOption(q.id)}
              className="mt-2 text-xs text-indigo-600 hover:underline"
            >
              + Add option
            </button>
          </li>
        ))}
        {items.length === 0 && <li className="text-secondary text-sm">No questions yet.</li>}
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
  const [textEn, setTextEn] = useState(initial?.textEn ?? '')
  const [textHe, setTextHe] = useState(initial?.textHe ?? '')
  const [orderIndex, setOrderIndex] = useState<number | ''>(initial?.orderIndex ?? '')
  const [active, setActive] = useState(initial?.active ?? true)
  const [err, setErr] = useState<string | null>(null)
  return (
    <AdminModal
      title={initial ? 'Edit question' : 'Add question'}
      onClose={onClose}
      footer={
        <div className="flex justify-end gap-2">
          <button onClick={onClose} className="px-4 py-2 rounded-full border border-line">
            Cancel
          </button>
          <button
            onClick={async () => {
              try {
                await onSave({
                  textEn,
                  textHe: textHe.trim() ? textHe : undefined,
                  orderIndex: orderIndex === '' ? undefined : orderIndex,
                  active,
                })
              } catch (e) {
                setErr(extractError(e))
              }
            }}
            className="px-4 py-2 rounded-full bg-indigo-600 text-on-brand"
          >
            Save
          </button>
        </div>
      }
    >
      <Labelled label="Question text (English)">
        <textarea value={textEn} onChange={(e) => setTextEn(e.target.value)} rows={3} className={inputCls} />
      </Labelled>
      <Labelled label="Question text (Hebrew)">
        <textarea value={textHe} onChange={(e) => setTextHe(e.target.value)} rows={3} className={inputCls} dir="rtl" />
      </Labelled>
      <Labelled label="Order index">
        <input
          type="number"
          value={orderIndex}
          onChange={(e) => setOrderIndex(e.target.value === '' ? '' : parseInt(e.target.value, 10))}
          className={inputCls}
        />
      </Labelled>
      <label className="flex items-center gap-2 text-sm">
        <input type="checkbox" checked={active} onChange={(e) => setActive(e.target.checked)} />
        Active in live quiz
      </label>
      {err && <p className="mt-2 text-sm text-red-600">{err}</p>}
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
  const [textEn, setTextEn] = useState(initial?.textEn ?? '')
  const [textHe, setTextHe] = useState(initial?.textHe ?? '')
  const [weights, setWeights] = useState<Record<WeightField, string>>(() => {
    const base: Record<WeightField, string> = {} as Record<WeightField, string>
    for (const w of WEIGHT_FIELDS) base[w] = initial ? initial[w].toString() : '0'
    return base
  })
  const [err, setErr] = useState<string | null>(null)
  return (
    <AdminModal
      title={initial ? 'Edit option' : 'Add option'}
      onClose={onClose}
      size="md"
      footer={
        <div className="flex justify-end gap-2">
          <button onClick={onClose} className="px-4 py-2 rounded-full border border-line">
            Cancel
          </button>
          <button
            onClick={async () => {
              try {
                const body: { textEn: string; textHe?: string } & Partial<Record<WeightField, number>> = {
                  textEn,
                  textHe: textHe.trim() ? textHe : undefined,
                }
                for (const w of WEIGHT_FIELDS) {
                  body[w] = parseFloat(weights[w] || '0')
                }
                await onSave(body)
              } catch (e) {
                setErr(extractError(e))
              }
            }}
            className="px-4 py-2 rounded-full bg-indigo-600 text-on-brand"
          >
            Save
          </button>
        </div>
      }
    >
      <Labelled label="Option text (English)">
        <input value={textEn} onChange={(e) => setTextEn(e.target.value)} className={inputCls} />
      </Labelled>
      <Labelled label="Option text (Hebrew)">
        <input value={textHe} onChange={(e) => setTextHe(e.target.value)} className={inputCls} dir="rtl" />
      </Labelled>
      <div className="grid grid-cols-2 gap-2">
        {WEIGHT_FIELDS.map((w) => (
          <Labelled key={w} label={WEIGHT_LABELS[w]}>
            <input
              type="number"
              step="0.05"
              value={weights[w]}
              onChange={(e) => setWeights((s) => ({ ...s, [w]: e.target.value }))}
              className={inputCls}
            />
          </Labelled>
        ))}
      </div>
      {err && <p className="mt-2 text-sm text-red-600">{err}</p>}
    </AdminModal>
  )
}

function Labelled({ label, children }: { label: string; children: React.ReactNode }) {
  return (
    <label className="block mb-3">
      <span className="text-xs text-secondary">{label}</span>
      <div className="mt-1">{children}</div>
    </label>
  )
}

const inputCls = 'w-full rounded-xl border border-line bg-base px-3 py-2 text-sm'

function extractError(e: unknown): string {
  if (typeof e === 'object' && e !== null) {
    const anyE = e as { response?: { data?: { error?: { code?: string; message?: string } } } }
    return anyE.response?.data?.error?.message ?? anyE.response?.data?.error?.code ?? 'Request failed.'
  }
  return 'Request failed.'
}
