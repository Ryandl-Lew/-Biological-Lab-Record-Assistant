import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import PdfPreview from './PdfPreview'

const pdfMocks = vi.hoisted(() => {
  const renderTask = { promise: Promise.resolve(), cancel: vi.fn() }
  const page = {
    getViewport: vi.fn(() => ({ width: 400, height: 600 })),
    render: vi.fn(() => renderTask),
  }
  const document = {
    numPages: 2,
    getPage: vi.fn(() => Promise.resolve(page)),
    destroy: vi.fn(),
  }
  const loadingTask = { promise: Promise.resolve(document), destroy: vi.fn() }
  return { document, loadingTask, page, renderTask }
})

vi.mock('pdfjs-dist', () => ({
  GlobalWorkerOptions: {},
  getDocument: vi.fn(() => pdfMocks.loadingTask),
}))
vi.mock('pdfjs-dist/build/pdf.worker.min.mjs?url', () => ({ default: 'pdf-worker.js' }))

describe('PdfPreview', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    HTMLCanvasElement.prototype.getContext = vi.fn(() => ({}))
  })

  it('renders PDF pages to canvas and supports paging and zoom', async () => {
    const user = userEvent.setup()
    const blob = { arrayBuffer: vi.fn().mockResolvedValue(new ArrayBuffer(16)) }
    render(<PdfPreview blob={blob} />)

    expect(await screen.findByText('1 / 2')).toBeInTheDocument()
    await waitFor(() => expect(pdfMocks.document.getPage).toHaveBeenCalledWith(1))
    expect(screen.getByLabelText('PDF 第 1 页')).toBeInTheDocument()

    await user.click(screen.getByRole('button', { name: '下一页' }))
    await waitFor(() => expect(pdfMocks.document.getPage).toHaveBeenCalledWith(2))
    expect(screen.getByText('2 / 2')).toBeInTheDocument()

    await user.click(screen.getByRole('button', { name: '放大' }))
    expect(screen.getByText('140%')).toBeInTheDocument()
  })
})
