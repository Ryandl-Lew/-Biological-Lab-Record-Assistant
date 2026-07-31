import { render, screen, within } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import RevisionDiffViewer from './RevisionDiffViewer'

describe('RevisionDiffViewer', () => {
  it('renders structured scalar and multi-line text changes', () => {
    render(
      <RevisionDiffViewer
        diff={{
          generatedAt: '2026-07-27T00:00:00Z',
          truncated: true,
          warnings: ['正文已截断'],
          summary: { added: 1, removed: 1, modified: 2, unchanged: 0 },
          sections: [
            {
              key: 'fixed:title',
              label: '标题',
              kind: 'SCALAR',
              status: 'MODIFIED',
              before: '旧标题',
              after: '新标题',
              textHunks: [],
            },
            {
              key: 'field:notes',
              label: '备注',
              kind: 'TEMPLATE_FIELD',
              valueType: 'MULTI_LINE_TEXT',
              status: 'MODIFIED',
              before: '旧备注',
              after: '新备注',
              textHunks: [
                { operation: 'DELETE', text: '旧' },
                { operation: 'INSERT', text: '新' },
                { operation: 'EQUAL', text: '备注' },
              ],
            },
            {
              key: 'review',
              label: '审核信息',
              kind: 'REVIEW_METADATA',
              status: 'MODIFIED',
              before: '退回',
              after: '通过',
              textHunks: [],
            },
          ],
        }}
      />,
    )
    expect(screen.getByText('旧标题')).toBeInTheDocument()
    expect(screen.getByText('新标题')).toBeInTheDocument()
    const notesTable = screen.getByRole('table', { name: '备注对比' })
    expect(within(notesTable).getByText('旧')).toBeInTheDocument()
    expect(within(notesTable).getByText('新')).toBeInTheDocument()
    expect(within(notesTable).getAllByText('备注')).toHaveLength(2)
    expect(screen.queryByText('审核信息')).not.toBeInTheDocument()
    expect(screen.getByRole('alert')).toHaveTextContent('已截断')
  })

  it('renders rich text body as a GitHub style side-by-side diff with context and word highlights', () => {
    render(
      <RevisionDiffViewer
        diff={{
          generatedAt: '2026-07-27T00:00:00Z',
          truncated: false,
          warnings: [],
          from: { type: 'REVISION', revisionId: 'r1', revisionNo: 1 },
          to: { type: 'WORKING_COPY' },
          summary: { added: 1, removed: 1, modified: 1, unchanged: 1 },
          sections: [
            {
              key: 'content:block:0',
              label: '正文块 1',
              kind: 'RICH_TEXT',
              status: 'UNCHANGED',
              before: '相同的段落',
              after: '相同的段落',
              textHunks: [],
            },
            {
              key: 'content:block:1',
              label: '正文块 2',
              kind: 'RICH_TEXT',
              status: 'MODIFIED',
              before: '旧的结论',
              after: '新的结论',
              textHunks: [
                { operation: 'DELETE', text: '旧' },
                { operation: 'INSERT', text: '新' },
                { operation: 'EQUAL', text: '的结论' },
              ],
            },
            {
              key: 'content:block:2',
              label: '正文块 3',
              kind: 'RICH_TEXT',
              status: 'ADDED',
              before: null,
              after: '补充的段落',
              textHunks: [],
            },
            {
              key: 'content:block:3',
              label: '正文块 4',
              kind: 'RICH_TEXT',
              status: 'REMOVED',
              before: '删除的段落',
              after: null,
              textHunks: [],
            },
          ],
        }}
      />,
    )
    const table = screen.getByRole('table', { name: '正文版本对比' })
    expect(within(table).getByText('基准：R1')).toBeInTheDocument()
    expect(within(table).getByText('目标：当前工作副本')).toBeInTheDocument()
    expect(within(table).getAllByText('相同的段落')).toHaveLength(2)
    expect(within(table).getByText('旧')).toBeInTheDocument()
    expect(within(table).getByText('新')).toBeInTheDocument()
    expect(within(table).getAllByText('的结论')).toHaveLength(2)
    expect(within(table).getByText('补充的段落')).toBeInTheDocument()
    expect(within(table).getByText('删除的段落')).toBeInTheDocument()
    expect(screen.queryByText('正文块 2')).not.toBeInTheDocument()
    const rows = within(table).getAllByRole('row').slice(1)
    expect([rows[0].cells[0].textContent, rows[0].cells[2].textContent]).toEqual(['1', '1'])
    expect([rows[1].cells[0].textContent, rows[1].cells[2].textContent]).toEqual(['2', '2'])
    expect([rows[2].cells[0].textContent, rows[2].cells[2].textContent]).toEqual(['', '3'])
    expect([rows[3].cells[0].textContent, rows[3].cells[2].textContent]).toEqual(['3', ''])
  })

  it('keeps the body comparison in section order and hides attachment and review sections', () => {
    render(
      <RevisionDiffViewer
        diff={{
          generatedAt: '2026-07-27T00:00:00Z',
          truncated: false,
          warnings: [],
          from: { type: 'REVISION', revisionId: 'r1', revisionNo: 1 },
          to: { type: 'REVISION', revisionId: 'r2', revisionNo: 2 },
          summary: {
            added: 0,
            removed: 0,
            modified: 3,
            unchanged: 0,
            attachmentAdded: 1,
            attachmentRemoved: 1,
          },
          sections: [
            {
              key: 'fixed:title',
              label: '实验名称',
              kind: 'SCALAR',
              status: 'MODIFIED',
              before: '旧题',
              after: '新题',
              textHunks: [],
            },
            {
              key: 'content:block:0',
              label: '正文块 1',
              kind: 'RICH_TEXT',
              status: 'MODIFIED',
              before: '旧正文',
              after: '新正文',
              textHunks: [
                { operation: 'DELETE', text: '旧正文' },
                { operation: 'INSERT', text: '新正文' },
              ],
            },
            {
              key: 'attachments',
              label: '附件',
              kind: 'ATTACHMENT_SET',
              status: 'MODIFIED',
              before: ['a.png'],
              after: ['b.png'],
              textHunks: [],
            },
            {
              key: 'review',
              label: '审核信息',
              kind: 'REVIEW_METADATA',
              status: 'MODIFIED',
              before: '退回',
              after: '通过',
              textHunks: [],
            },
          ],
        }}
      />,
    )
    const body = screen.getByRole('table', { name: '正文版本对比' })
    expect(screen.getByText('实验名称').compareDocumentPosition(body)).toBe(
      Node.DOCUMENT_POSITION_FOLLOWING,
    )
    expect(screen.queryByText('附件')).not.toBeInTheDocument()
    expect(screen.queryByText('附件新增')).not.toBeInTheDocument()
    expect(screen.queryByText('附件删除')).not.toBeInTheDocument()
    expect(screen.queryByText('审核信息')).not.toBeInTheDocument()
  })
})
