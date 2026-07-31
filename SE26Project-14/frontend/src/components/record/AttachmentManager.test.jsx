import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import AttachmentManager from './AttachmentManager'
import { previewAttachment, uploadAttachment } from '@/api'

vi.mock('@/api', () => ({
  fetchAttachments: vi.fn().mockResolvedValue([
    {
      id: 'a1',
      originalFilename: '结果.png',
      mediaType: 'image/png',
      sizeBytes: 120,
      previewable: true,
      canDelete: true,
    },
  ]),
  uploadAttachment: vi.fn(),
  deleteAttachment: vi.fn(),
  downloadAttachment: vi.fn(),
  saveBlob: vi.fn(),
  previewAttachment: vi.fn(),
}))
vi.mock('./PdfPreview', () => ({
  default: ({ blob }) => <div data-testid="pdf-canvas-preview">{blob ? 'PDF.js preview' : ''}</div>,
}))

describe('AttachmentManager', () => {
  beforeEach(() => {
    vi.stubGlobal(
      'confirm',
      vi.fn(() => true),
    )
    URL.createObjectURL = vi.fn(() => 'blob:preview')
    URL.revokeObjectURL = vi.fn()
  })
  it('shows upload progress and refreshes the list', async () => {
    uploadAttachment.mockImplementation(async (_id, _file, progress) => {
      progress(50)
      progress(100)
      return {}
    })
    const user = userEvent.setup()
    render(<AttachmentManager recordId="r1" />)
    const file = new File(['data'], '数据.txt', { type: 'text/plain' })
    await user.upload(screen.getByLabelText('选择附件'), file)
    expect(uploadAttachment).toHaveBeenCalled()
    await waitFor(() => expect(screen.getByText('结果.png')).toBeInTheDocument())
  })
  it('creates and revokes authenticated preview object URLs', async () => {
    previewAttachment.mockResolvedValue({ blob: new Blob(['x'], { type: 'image/png' }) })
    const user = userEvent.setup()
    render(
      <AttachmentManager
        recordId="r1"
        initialItems={[
          {
            id: 'a1',
            filename: '结果.png',
            mediaType: 'image/png',
            sizeBytes: 1,
            previewable: true,
          },
        ]}
        readOnly
      />,
    )
    await user.click(screen.getByRole('button', { name: '预览' }))
    expect(URL.createObjectURL).toHaveBeenCalled()
    await user.click(screen.getByRole('button', { name: '关闭' }))
    expect(URL.revokeObjectURL).toHaveBeenCalledWith('blob:preview')
  })
  it('previews authenticated markdown as safe source text', async () => {
    previewAttachment.mockResolvedValue({
      blob: { text: vi.fn().mockResolvedValue('# 实验结果\n\n- 成功') },
    })
    const user = userEvent.setup()
    render(
      <AttachmentManager
        recordId="r1"
        initialItems={[
          {
            id: 'm1',
            filename: '结果.md',
            mediaType: 'text/markdown',
            sizeBytes: 20,
            previewable: true,
          },
        ]}
        readOnly
      />,
    )
    await user.click(screen.getByRole('button', { name: '预览' }))
    expect(await screen.findByText(/# 实验结果/)).toBeInTheDocument()
    expect(screen.getByText('Markdown 文本预览')).toBeInTheDocument()
  })
  it('uses the PDF.js canvas preview instead of an iframe', async () => {
    previewAttachment.mockResolvedValue({ blob: new Blob(['%PDF-'], { type: 'application/pdf' }) })
    const user = userEvent.setup()
    render(
      <AttachmentManager
        recordId="r1"
        initialItems={[
          {
            id: 'p1',
            filename: '报告.pdf',
            mediaType: 'application/pdf',
            sizeBytes: 20,
            previewable: true,
          },
        ]}
        readOnly
      />,
    )
    await user.click(screen.getByRole('button', { name: '预览' }))
    expect(await screen.findByTestId('pdf-canvas-preview')).toHaveTextContent('PDF.js preview')
    expect(screen.queryByTitle('PDF 附件预览')).not.toBeInTheDocument()
  })
})
