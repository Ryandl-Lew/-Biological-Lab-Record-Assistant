import { useEffect, useRef, useState } from 'react'
import { ChevronLeft, ChevronRight, RotateCw, ZoomIn, ZoomOut } from 'lucide-react'
import * as pdfjs from 'pdfjs-dist'
import pdfWorkerUrl from 'pdfjs-dist/build/pdf.worker.min.mjs?url'
import { Button } from '@/components/ui'

pdfjs.GlobalWorkerOptions.workerSrc = pdfWorkerUrl

const MIN_SCALE = 0.6
const MAX_SCALE = 2.4
const SCALE_STEP = 0.2

export default function PdfPreview({ blob }) {
  const canvasRef = useRef(null)
  const [document, setDocument] = useState(null)
  const [pageNumber, setPageNumber] = useState(1)
  const [pageCount, setPageCount] = useState(0)
  const [scale, setScale] = useState(1.2)
  const [rotation, setRotation] = useState(0)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')

  useEffect(() => {
    if (!blob) return undefined
    let disposed = false
    let loadingTask
    let loadedDocument

    const load = async () => {
      setLoading(true)
      setError('')
      try {
        const data = new Uint8Array(await blob.arrayBuffer())
        loadingTask = pdfjs.getDocument({ data })
        loadedDocument = await loadingTask.promise
        if (disposed) return
        setDocument(loadedDocument)
        setPageCount(loadedDocument.numPages)
        setPageNumber(1)
      } catch {
        if (!disposed) setError('PDF 文件无法解析，请下载后查看。')
      } finally {
        if (!disposed) setLoading(false)
      }
    }

    load()
    return () => {
      disposed = true
      loadingTask?.destroy?.()
      loadedDocument?.destroy?.()
    }
  }, [blob])

  useEffect(() => {
    if (!document || !canvasRef.current) return undefined
    let disposed = false
    let renderTask

    const renderPage = async () => {
      setLoading(true)
      setError('')
      try {
        const page = await document.getPage(pageNumber)
        if (disposed) return
        const viewport = page.getViewport({ scale, rotation })
        const outputScale = Math.min(window.devicePixelRatio || 1, 2)
        const canvas = canvasRef.current
        const context = canvas.getContext('2d', { alpha: false })
        canvas.width = Math.ceil(viewport.width * outputScale)
        canvas.height = Math.ceil(viewport.height * outputScale)
        canvas.style.width = `${Math.ceil(viewport.width)}px`
        canvas.style.height = `${Math.ceil(viewport.height)}px`
        renderTask = page.render({
          canvasContext: context,
          viewport,
          transform: outputScale === 1 ? null : [outputScale, 0, 0, outputScale, 0, 0],
        })
        await renderTask.promise
      } catch (renderError) {
        if (!disposed && renderError?.name !== 'RenderingCancelledException') {
          setError('PDF 页面渲染失败，请重试或下载后查看。')
        }
      } finally {
        if (!disposed) setLoading(false)
      }
    }

    renderPage()
    return () => {
      disposed = true
      renderTask?.cancel?.()
    }
  }, [document, pageNumber, rotation, scale])

  const changeScale = (delta) => {
    setScale((current) =>
      Math.min(MAX_SCALE, Math.max(MIN_SCALE, Number((current + delta).toFixed(1)))),
    )
  }

  return (
    <div className="flex h-full min-h-0 flex-col overflow-hidden rounded-lg border border-slate-200 bg-slate-200">
      <div className="flex flex-wrap items-center justify-center gap-2 border-b border-slate-200 bg-white px-3 py-2">
        <Button
          size="sm"
          variant="ghost"
          icon={ChevronLeft}
          className="w-8 px-0"
          aria-label="上一页"
          title="上一页"
          disabled={pageNumber <= 1}
          onClick={() => setPageNumber((value) => Math.max(1, value - 1))}
        />
        <span className="min-w-24 text-center text-xs tabular-nums text-slate-600">
          {pageCount ? `${pageNumber} / ${pageCount}` : '加载中'}
        </span>
        <Button
          size="sm"
          variant="ghost"
          icon={ChevronRight}
          className="w-8 px-0"
          aria-label="下一页"
          title="下一页"
          disabled={!pageCount || pageNumber >= pageCount}
          onClick={() => setPageNumber((value) => Math.min(pageCount, value + 1))}
        />
        <span className="mx-1 h-5 w-px bg-slate-200" />
        <Button
          size="sm"
          variant="ghost"
          icon={ZoomOut}
          className="w-8 px-0"
          aria-label="缩小"
          title="缩小"
          disabled={scale <= MIN_SCALE}
          onClick={() => changeScale(-SCALE_STEP)}
        />
        <span className="min-w-12 text-center text-xs tabular-nums text-slate-600">
          {Math.round(scale * 100)}%
        </span>
        <Button
          size="sm"
          variant="ghost"
          icon={ZoomIn}
          className="w-8 px-0"
          aria-label="放大"
          title="放大"
          disabled={scale >= MAX_SCALE}
          onClick={() => changeScale(SCALE_STEP)}
        />
        <Button
          size="sm"
          variant="ghost"
          icon={RotateCw}
          className="w-8 px-0"
          aria-label="顺时针旋转"
          title="顺时针旋转"
          onClick={() => setRotation((value) => (value + 90) % 360)}
        />
      </div>
      <div className="relative min-h-0 flex-1 overflow-auto p-4">
        {error ? (
          <p
            role="alert"
            className="mx-auto max-w-md rounded-lg bg-red-50 p-4 text-sm text-red-700"
          >
            {error}
          </p>
        ) : null}
        <canvas
          ref={canvasRef}
          aria-label={`PDF 第 ${pageNumber} 页`}
          className={`mx-auto bg-white shadow-sm ${loading ? 'opacity-40' : 'opacity-100'}`}
        />
        {loading && !error ? (
          <p role="status" className="absolute inset-x-0 top-8 text-center text-sm text-slate-500">
            正在渲染 PDF…
          </p>
        ) : null}
      </div>
    </div>
  )
}
