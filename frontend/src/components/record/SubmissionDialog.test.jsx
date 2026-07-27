import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import SubmissionDialog from './SubmissionDialog'
import { fetchReviewerCandidates, submitRecord } from '@/api'

vi.mock('@/api', () => ({ fetchReviewerCandidates: vi.fn(), submitRecord: vi.fn() }))
const record = { id: 'r1', version: 3, currentRevisionNo: 1 }

describe('SubmissionDialog', () => {
  it('disables submission when there is no legal reviewer', async () => { fetchReviewerCandidates.mockResolvedValue([]); render(<SubmissionDialog record={record} open onClose={() => {}} onSubmitted={() => {}} />); expect(await screen.findByText(/暂无合法审核人/)).toBeInTheDocument(); expect(screen.getByRole('button', { name: '确认提交' })).toBeDisabled() })
  it('submits the current optimistic-lock version to a selected reviewer', async () => { fetchReviewerCandidates.mockResolvedValue([{ userId: 'u2', displayName: '审核人', role: 'REVIEWER' }]); submitRecord.mockResolvedValue({ id: 'rv2' }); const done = vi.fn(); const user = userEvent.setup(); render(<SubmissionDialog record={record} open onClose={() => {}} onSubmitted={done} />); await screen.findByRole('option', { name: /审核人/ }); await user.click(screen.getByRole('button', { name: '确认提交' })); expect(submitRecord).toHaveBeenCalledWith('r1', expect.objectContaining({ reviewerId: 'u2', expectedRecordVersion: 3 })); expect(done).toHaveBeenCalled() })
  it('shows the concrete invalid field returned by submission validation', async () => { fetchReviewerCandidates.mockResolvedValue([{ userId: 'u2', displayName: '审核人', role: 'REVIEWER' }]); const error = Object.assign(new Error('请补全必填字段'), { fieldErrors: { target_gene: '目标基因不能为空' } }); submitRecord.mockRejectedValue(error); const user = userEvent.setup(); render(<SubmissionDialog record={record} open onClose={() => {}} onSubmitted={() => {}} />); await screen.findByRole('option', { name: /审核人/ }); await user.click(screen.getByRole('button', { name: '确认提交' })); expect(await screen.findByRole('alert')).toHaveTextContent('请补全必填字段：目标基因不能为空') })
})
