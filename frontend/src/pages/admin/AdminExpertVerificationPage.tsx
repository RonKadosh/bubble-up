import { useEffect, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import {
  AdminExpert,
  listExperts,
  rejectExpert,
  revokeExpert,
  VerificationStatus,
  verifyExpert,
} from '../../api/admin'
import AdminModal from './components/AdminModal'
import ReasonPromptModal from './components/ReasonPromptModal'

const STATUSES: VerificationStatus[] = ['PENDING', 'VERIFIED', 'REJECTED']

export default function AdminExpertVerificationPage() {
  const navigate = useNavigate()
  const [status, setStatus] = useState<VerificationStatus>('PENDING')
  const [items, setItems] = useState<AdminExpert[]>([])
  const [opening, setOpening] = useState<AdminExpert | null>(null)
  const [err, setErr] = useState<string | null>(null)

  async function load() {
    try {
      setItems(await listExperts(status))
    } catch (e) {
      setErr(String(e))
    }
  }
  useEffect(() => {
    load()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [status])

  return (
    <div className="flex flex-col h-full overflow-hidden">
      <header className="px-6 py-4 border-b border-line flex items-center justify-between">
        <div>
          <h1 className="text-xl font-semibold">Expert verification</h1>
          <p className="text-sm text-secondary">Review and approve expert applications.</p>
        </div>
        <button
          onClick={() => navigate('/admin')}
          className="text-sm rounded-full px-4 py-2 border border-line bg-surface hover:bg-surface-hover"
        >
          ← Admin home
        </button>
      </header>
      <div className="px-6 pt-3 flex gap-1 border-b border-line">
        {STATUSES.map((s) => (
          <button
            key={s}
            onClick={() => setStatus(s)}
            className={`px-4 py-2 text-sm rounded-t-xl border-b-2 ${
              status === s ? 'border-indigo-600 font-medium' : 'border-transparent text-secondary'
            }`}
          >
            {s}
          </button>
        ))}
      </div>
      <main className="flex-1 overflow-y-auto p-6">
        {err && <p className="text-red-600 text-sm">{err}</p>}
        <ul className="grid grid-cols-1 tablet:grid-cols-2 gap-3">
          {items.map((e) => (
            <li
              key={e.id}
              className="rounded-2xl border border-line bg-surface p-4 cursor-pointer hover:bg-surface-hover"
              onClick={() => setOpening(e)}
            >
              <div className="text-base font-medium">{e.headline}</div>
              <div className="text-xs text-secondary mt-1">User: {e.userId}</div>
              <div className="text-xs text-secondary mt-0.5">
                {e.expertiseTags.length > 0 && `Tags: ${e.expertiseTags.join(', ')}`}
              </div>
              <div className="text-xs text-secondary mt-1">
                Applied: {new Date(e.appliedAt).toLocaleString()}
              </div>
              {e.verifiedAt && (
                <div className="text-xs text-green-700 mt-1">
                  Verified {new Date(e.verifiedAt).toLocaleDateString()}
                </div>
              )}
            </li>
          ))}
          {items.length === 0 && (
            <li className="text-secondary text-sm col-span-full">No experts in this state.</li>
          )}
        </ul>
      </main>
      {opening && (
        <ExpertDetailModal
          expert={opening}
          onClose={() => setOpening(null)}
          onChanged={() => {
            setOpening(null)
            load()
          }}
        />
      )}
    </div>
  )
}

function ExpertDetailModal({
  expert,
  onClose,
  onChanged,
}: {
  expert: AdminExpert
  onClose: () => void
  onChanged: () => void
}) {
  const [action, setAction] = useState<'verify' | 'reject' | 'revoke' | null>(null)
  return (
    <AdminModal
      title={expert.headline}
      onClose={onClose}
      size="md"
      footer={
        <div className="flex justify-end gap-2">
          {expert.verificationStatus !== 'VERIFIED' && (
            <button
              onClick={() => setAction('verify')}
              className="px-4 py-2 rounded-full bg-green-600 text-on-brand"
            >
              Verify
            </button>
          )}
          {expert.verificationStatus === 'VERIFIED' && (
            <button
              onClick={() => setAction('revoke')}
              className="px-4 py-2 rounded-full border border-line"
            >
              Revoke
            </button>
          )}
          <button
            onClick={() => setAction('reject')}
            className="px-4 py-2 rounded-full border border-red-300 text-red-600"
          >
            Reject (delete + demote)
          </button>
          <button onClick={onClose} className="px-4 py-2 rounded-full bg-indigo-600 text-on-brand">
            Close
          </button>
        </div>
      }
    >
      <p className="text-sm text-secondary mb-2">User: <span className="font-mono text-xs">{expert.userId}</span></p>
      <p className="text-sm mb-3">{expert.bio || 'No bio.'}</p>
      <p className="text-xs text-secondary">
        Status: <span className="font-medium text-base">{expert.verificationStatus}</span>
      </p>
      {expert.verifiedAt && (
        <p className="text-xs text-secondary">
          Verified at: {new Date(expert.verifiedAt).toLocaleString()}
        </p>
      )}
      {action && (
        <ReasonPromptModal
          title={
            action === 'verify' ? 'Verify expert'
            : action === 'reject' ? 'Reject expert'
            : 'Revoke verification'
          }
          description={
            action === 'verify' ? 'Reason is optional but helpful for the audit trail.'
            : action === 'reject' ? 'Deletes the profile entirely and demotes the user to STUDENT.'
            : 'Clears the verified flag but keeps the profile and EXPERT role.'
          }
          destructive={action !== 'verify'}
          confirmLabel={action === 'verify' ? 'Verify' : action === 'reject' ? 'Reject' : 'Revoke'}
          onCancel={() => setAction(null)}
          onConfirm={async (reason) => {
            if (action === 'verify') await verifyExpert(expert.userId, reason)
            else if (action === 'reject') await rejectExpert(expert.userId, reason)
            else if (action === 'revoke') await revokeExpert(expert.userId, reason)
            setAction(null)
            onChanged()
          }}
        />
      )}
    </AdminModal>
  )
}
