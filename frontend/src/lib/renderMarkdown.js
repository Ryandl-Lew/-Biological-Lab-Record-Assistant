import DOMPurify from 'dompurify'
import katex from 'katex'

export function renderMarkdown(text) {
  if (!text) return ''

  let html = text

  // code blocks (```...```) — must process before inline
  const codeBlocks = []
  html = html.replace(/```(\w*)\n([\s\S]*?)```/g, (_m, lang, code) => {
    const escaped = code.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
    const placeholder = `%%CODEBLOCK_${codeBlocks.length}%%`
    codeBlocks.push(
      `<pre class="my-2 overflow-x-auto rounded-lg bg-slate-100 p-3 text-xs"><code>${escaped}</code></pre>`,
    )
    return placeholder
  })

  // block math $$...$$
  html = html.replace(/\$\$([\s\S]*?)\$\$/g, (_m, formula) => {
    try {
      return katex.renderToString(formula.trim(), { displayMode: true, throwOnError: false })
    } catch {
      return `<code>${formula.trim()}</code>`
    }
  })

  // inline math $...$ (must not match $$)
  html = html.replace(/(?<!\$)\$(?!\$)([^$]+)\$(?!\$)/g, (_m, formula) => {
    try {
      return katex.renderToString(formula.trim(), { displayMode: false, throwOnError: false })
    } catch {
      return `<code>${formula.trim()}</code>`
    }
  })

  // restore code blocks
  html = html.replace(/%%CODEBLOCK_(\d+)%%/g, (_m, idx) => codeBlocks[parseInt(idx)] || '')

  // inline code (`...`)
  html = html.replace(
    /`([^`]+)`/g,
    '<code class="rounded bg-slate-100 px-1 py-0.5 text-xs font-mono text-rose-600">$1</code>',
  )

  // headers
  html = html.replace(/^### (.+)$/gm, '<h4 class="mt-3 mb-1 text-sm font-semibold">$1</h4>')
  html = html.replace(/^## (.+)$/gm, '<h3 class="mt-4 mb-1 text-base font-semibold">$1</h3>')
  html = html.replace(/^# (.+)$/gm, '<h2 class="mt-4 mb-2 text-lg font-bold">$1</h2>')

  // bold and italic
  html = html.replace(/\*\*\*(.+?)\*\*\*/g, '<strong><em>$1</em></strong>')
  html = html.replace(/\*\*(.+?)\*\*/g, '<strong>$1</strong>')
  html = html.replace(/\*(.+?)\*/g, '<em>$1</em>')

  // links
  html = html.replace(
    /\[([^\]]+)\]\(([^)]+)\)/g,
    '<a href="$2" class="text-brand-600 underline" target="_blank" rel="noopener noreferrer">$1</a>',
  )

  // unordered lists — group consecutive lines, wrap in <ul>, each line in <li>
  // Handles optional leading whitespace (indented lists)
  html = html.replace(/((?:^\s*[-*] .+(?:\n|$))+)/gm, (block) => {
    const items = block
      .trim()
      .split('\n')
      .map((line) => `<li>${line.replace(/^\s*[-*] /, '')}</li>`)
      .join('')
    return `<ul class="my-1 list-disc list-inside space-y-0.5 pl-1 text-sm">${items}</ul>`
  })

  // ordered lists
  html = html.replace(/((?:^\s*\d+\. .+(?:\n|$))+)/gm, (block) => {
    const items = block
      .trim()
      .split('\n')
      .map((line) => `<li>${line.replace(/^\s*\d+\. /, '')}</li>`)
      .join('')
    return `<ol class="my-1 list-decimal list-inside space-y-0.5 pl-1 text-sm">${items}</ol>`
  })

  // tables (| header | header | ... | separator | data rows)
  html = html.replace(
    /((?:^\s*\|[^\n]+\|\s*\n\s*\|[\s\-:|]+\|\s*\n(?:\s*\|[^\n]+\|\s*\n?)*)+)/gm,
    (block) => {
      const lines = block.trim().split('\n')
      if (lines.length < 3) return block
      let result = '<table class="my-2 w-full border-collapse overflow-x-auto text-xs"><thead>'
      // header row
      const headers = lines[0]
        .split('|')
        .map((cell) => cell.trim())
        .filter(Boolean)
      result +=
        '<tr>' +
        headers
          .map(
            (h) =>
              `<th class="border border-slate-300 bg-slate-100 px-2 py-1 text-left font-medium">${h}</th>`,
          )
          .join('') +
        '</tr>'
      result += '</thead><tbody>'
      // data rows (skip separator line)
      for (let i = 2; i < lines.length; i++) {
        const cells = lines[i]
          .split('|')
          .map((cell) => cell.trim())
          .filter(Boolean)
        result +=
          '<tr>' +
          cells.map((c) => `<td class="border border-slate-300 px-2 py-1">${c}</td>`).join('') +
          '</tr>'
      }
      result += '</tbody></table>'
      return result
    },
  )

  // paragraphs — split by double newline, wrap in <p>
  const blocks = html.split('\n\n')
  html = blocks
    .map((block) => {
      const trimmed = block.trim()
      if (!trimmed) return ''
      if (/^<(h[2-4]|ul|ol|pre|blockquote)/.test(trimmed)) return trimmed
      // convert single newlines to <br> within paragraphs
      return `<p class="mb-1">${trimmed.replace(/\n/g, '<br>')}</p>`
    })
    .join('\n')

  return DOMPurify.sanitize(html, {
    ALLOWED_TAGS: [
      'p',
      'br',
      'strong',
      'em',
      'h2',
      'h3',
      'h4',
      'ul',
      'ol',
      'li',
      'pre',
      'code',
      'a',
      'blockquote',
      'table',
      'thead',
      'tbody',
      'tr',
      'th',
      'td',
      'span',
      'mrow',
      'mfrac',
      'msup',
      'msub',
      'mover',
      'munder',
      'mo',
      'mi',
      'mn',
      'mtext',
      'msqrt',
      'mtable',
      'mtr',
      'mtd',
      'annotation',
    ],
    ALLOWED_ATTR: [
      'href',
      'target',
      'rel',
      'class',
      'style',
      'aria-hidden',
      'columnalign',
      'columnspacing',
      'rowspacing',
    ],
  })
}
