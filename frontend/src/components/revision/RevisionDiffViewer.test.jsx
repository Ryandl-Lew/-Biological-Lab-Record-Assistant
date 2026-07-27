import { render, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import RevisionDiffViewer from './RevisionDiffViewer'

describe('RevisionDiffViewer', () => {
  it('renders structured scalar, text, attachment and review changes with accessible markers', () => {
    render(<RevisionDiffViewer diff={{ generatedAt: '2026-07-27T00:00:00Z', truncated: true, warnings: ['正文已截断'], summary: { added: 1, removed: 1, modified: 2, unchanged: 0, attachmentAdded: 1, attachmentRemoved: 1 }, sections: [
      { key: 'title', label: '标题', kind: 'SCALAR', status: 'MODIFIED', before: '旧标题', after: '新标题', textHunks: [] },
      { key: 'body', label: '正文', kind: 'RICH_TEXT', status: 'MODIFIED', before: '旧正文', after: '新正文', textHunks: [{ operation: 'DELETE', text: '旧' }, { operation: 'INSERT', text: '新' }] },
      { key: 'attachments', label: '附件', kind: 'ATTACHMENT_SET', status: 'MODIFIED', before: ['a.png'], after: ['b.png'], textHunks: [] },
      { key: 'review', label: '审核', kind: 'REVIEW_METADATA', status: 'MODIFIED', before: '退回', after: '通过', textHunks: [] },
    ] }} />)
    expect(screen.getByText('旧标题')).toBeInTheDocument()
    expect(screen.getByText('新标题')).toBeInTheDocument()
    expect(screen.getByLabelText('删除文本')).toBeInTheDocument()
    expect(screen.getByLabelText('新增文本')).toBeInTheDocument()
    expect(screen.getByText(/不计入实验内容结论/)).toBeInTheDocument()
    expect(screen.getByRole('alert')).toHaveTextContent('已截断')
  })
})
