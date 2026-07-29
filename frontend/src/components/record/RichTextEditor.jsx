import { EditorContent, useEditor } from '@tiptap/react'
import StarterKit from '@tiptap/starter-kit'
import Link from '@tiptap/extension-link'
import { useEffect, useState } from 'react'
import { Bold, Code2, Heading2, Heading3, Italic, Link2, List, ListOrdered, Minus, Pilcrow, Quote, Redo2, RemoveFormatting, Strikethrough, Undo2, Unlink } from 'lucide-react'

function ToolButton({ label, icon, active = false, disabled = false, onClick }) {
  const ToolIcon = icon
  return <button type="button" aria-label={label} title={label} disabled={disabled} onClick={onClick} className={`inline-flex h-8 min-w-8 items-center justify-center rounded-md px-2 text-xs font-medium transition disabled:cursor-not-allowed disabled:opacity-35 ${active ? 'bg-brand-100 text-brand-700 shadow-sm' : 'text-slate-600 hover:bg-white hover:text-slate-900 hover:shadow-sm'}`}><ToolIcon size={15}/></button>
}

export default function RichTextEditor({ html, onChange, editable = true }) {
  const [stats, setStats] = useState({ characters: 0, words: 0 })
  const [, setRevision] = useState(0)
  const updateStats = (value) => {
    const text = value.getText().trim()
    setStats({ characters: text.length, words: text ? text.split(/\s+/u).length : 0 })
  }
  const editor = useEditor({
    extensions: [
      StarterKit.configure({ heading: { levels: [2, 3] } }),
      Link.configure({ openOnClick: false, autolink: true, linkOnPaste: true, HTMLAttributes: { rel: 'noopener noreferrer nofollow', target: '_blank' } }),
    ],
    content: html || '',
    editable,
    editorProps: { attributes: { class: 'min-h-80 px-5 py-4 text-[15px] leading-7 text-slate-800 outline-none' } },
    onCreate: ({ editor: value }) => updateStats(value),
    onSelectionUpdate: () => setRevision((value) => value + 1),
    onUpdate: ({ editor: value }) => { updateStats(value); onChange?.(value.getJSON(), value.getHTML()) },
  })
  useEffect(() => { if (editor && html !== editor.getHTML()) editor.commands.setContent(html || '', false) }, [editor, html])
  useEffect(() => { editor?.setEditable(editable) }, [editable, editor])
  if (!editor) return null
  const setLink = () => {
    const current = editor.getAttributes('link').href || ''
    const input = prompt('输入链接地址', current)
    if (input === null) return
    if (!input.trim()) { editor.chain().focus().extendMarkRange('link').unsetLink().run(); return }
    const href = /^(https?:\/\/|mailto:)/i.test(input.trim()) ? input.trim() : `https://${input.trim()}`
    editor.chain().focus().extendMarkRange('link').setLink({ href }).run()
  }
  return <div className="overflow-hidden rounded-xl border border-slate-300 bg-white shadow-sm transition focus-within:border-brand-400 focus-within:shadow-glow">
    <div className="sticky top-0 z-10 flex flex-wrap items-center gap-1 border-b border-slate-200 bg-slate-50/95 px-3 py-2 backdrop-blur">
      <div className="flex items-center gap-0.5 border-r border-slate-200 pr-2"><ToolButton label="正文" icon={Pilcrow} active={editor.isActive('paragraph')} onClick={() => editor.chain().focus().setParagraph().run()}/><ToolButton label="二级标题" icon={Heading2} active={editor.isActive('heading', { level: 2 })} onClick={() => editor.chain().focus().toggleHeading({ level: 2 }).run()}/><ToolButton label="三级标题" icon={Heading3} active={editor.isActive('heading', { level: 3 })} onClick={() => editor.chain().focus().toggleHeading({ level: 3 }).run()}/></div>
      <div className="flex items-center gap-0.5 border-r border-slate-200 px-2"><ToolButton label="粗体" icon={Bold} active={editor.isActive('bold')} onClick={() => editor.chain().focus().toggleBold().run()}/><ToolButton label="斜体" icon={Italic} active={editor.isActive('italic')} onClick={() => editor.chain().focus().toggleItalic().run()}/><ToolButton label="删除线" icon={Strikethrough} active={editor.isActive('strike')} onClick={() => editor.chain().focus().toggleStrike().run()}/><ToolButton label="行内代码" icon={Code2} active={editor.isActive('code')} onClick={() => editor.chain().focus().toggleCode().run()}/></div>
      <div className="flex items-center gap-0.5 border-r border-slate-200 px-2"><ToolButton label="无序列表" icon={List} active={editor.isActive('bulletList')} onClick={() => editor.chain().focus().toggleBulletList().run()}/><ToolButton label="有序列表" icon={ListOrdered} active={editor.isActive('orderedList')} onClick={() => editor.chain().focus().toggleOrderedList().run()}/><ToolButton label="引用" icon={Quote} active={editor.isActive('blockquote')} onClick={() => editor.chain().focus().toggleBlockquote().run()}/><ToolButton label="分隔线" icon={Minus} onClick={() => editor.chain().focus().setHorizontalRule().run()}/></div>
      <div className="flex items-center gap-0.5 border-r border-slate-200 px-2"><ToolButton label="添加或编辑链接" icon={Link2} active={editor.isActive('link')} onClick={setLink}/><ToolButton label="移除链接" icon={Unlink} disabled={!editor.isActive('link')} onClick={() => editor.chain().focus().extendMarkRange('link').unsetLink().run()}/><ToolButton label="清除格式" icon={RemoveFormatting} onClick={() => editor.chain().focus().unsetAllMarks().clearNodes().run()}/></div>
      <div className="flex items-center gap-0.5 pl-2"><ToolButton label="撤销" icon={Undo2} disabled={!editor.can().chain().focus().undo().run()} onClick={() => editor.chain().focus().undo().run()}/><ToolButton label="重做" icon={Redo2} disabled={!editor.can().chain().focus().redo().run()} onClick={() => editor.chain().focus().redo().run()}/></div>
    </div>
    <EditorContent editor={editor} className="bionote-rich-editor prose max-w-none"/>
    <div className="flex items-center justify-between border-t border-slate-100 bg-slate-50/70 px-4 py-2 text-xs text-slate-400"><span>支持标题、列表、引用、链接和代码格式</span><span>{stats.characters} 字符 · {stats.words} 词</span></div>
  </div>
}
