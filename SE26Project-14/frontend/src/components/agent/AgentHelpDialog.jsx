import { X } from 'lucide-react'
import { createPortal } from 'react-dom'
import { Button } from '@/components/ui'

const SECTIONS = [
  {
    title: '用户须知',
    items: [
      '本项目助手接入外部agent，项目agent会自动调用当前项目内容包括附件作为数据库',
      '支持自然语言交流，可以要求agent对项目总结、概括、分析等',
      'agent支持读取csv，excel，pdf等，读取表格文件时会讲第一行内容作为列名，不支持智能识别变量',
      '因此请确保表格第一行内容为列名，否则agent无法正确识别分析',
    ],
  },
  {
    title: '能做什么',
    items: [
      '用自然语言请求曲线拟合（需确认后才计算）',
      '按 Excel/CSV 列名画折线图、柱状图或散点图',
      '启用通用分析模板，按固定章节组织叙述',
      '关联本机参考文件，供本轮对话上下文使用',
    ],
  },
  {
    title: '曲线拟合',
    items: [
      '预置方法：线性 / 二次 / 三次 / 指数 / 对数 / 幂 / 米氏 / Logistic 等',
      '也可直接写方程，例如：y=a*x/(b+x)',
      '系统会先给出拟合方案，点击「确认拟合」后才计算参数',
      '结果含 R²、RMSE、观测点与拟合曲线图，数值由引擎计算，不会由模型编造',
    ],
  },
  {
    title: '数据图',
    items: [
      '示例：帮我按 某某excel 内容画折线图',
      '再指定坐标：x轴发酵时长，y轴耗碱量（显示值）',
      '也可一次说清：画折线图，x轴=时间，y轴=残糖',
      '数据来自项目记录附件中的表格列，不会修改项目或记录',
    ],
  },
  {
    title: '参考文件',
    items: [
      '点击「文件」可关联本机 CSV / Excel / PDF 等（单次最多 5 个）',
      '参考文件仅用于当前对话，不会写入实验记录附件',
    ],
  },
  {
    title: '注意',
    items: [
      'Agent 为只读助手，不会改项目、记录、审核或成员',
      'COMPLETED 表示流程完成，不代表实验成功',
      '画图与拟合均要求能从表格解析出数值；时间列支持 0h、2h 等写法',
    ],
  },
]

export default function AgentHelpDialog({ open, onClose }) {
  if (!open) return null
  return createPortal(
    <div
      className="fixed inset-0 z-50 flex items-center justify-center bg-slate-900/50 p-4"
      role="presentation"
      onClick={onClose}
    >
      <div
        role="dialog"
        aria-modal="true"
        aria-labelledby="agent-help-title"
        className="max-h-[88vh] w-full max-w-xl overflow-hidden rounded-2xl bg-white/95 shadow-pop backdrop-blur-sm"
        onClick={(event) => event.stopPropagation()}
      >
        <div className="flex items-start justify-between border-b border-slate-100 px-5 py-4">
          <div>
            <h2 id="agent-help-title" className="text-lg font-semibold text-slate-900">
              Agent 使用帮助
            </h2>
            <p className="mt-1 text-sm text-slate-500">项目问答支持拟合、画图与分析模板</p>
          </div>
          <button
            type="button"
            aria-label="关闭帮助"
            onClick={onClose}
            className="rounded-lg p-2 text-slate-400 hover:bg-slate-100 hover:text-slate-700"
          >
            <X size={18} />
          </button>
        </div>
        <div className="max-h-[min(70vh,32rem)] space-y-5 overflow-y-auto px-5 py-4">
          {SECTIONS.map((section) => (
            <section key={section.title}>
              <h3 className="text-sm font-semibold text-slate-800">{section.title}</h3>
              <ul className="mt-2 list-disc space-y-1.5 pl-5 text-sm leading-6 text-slate-600">
                {section.items.map((item) => (
                  <li key={item}>{item}</li>
                ))}
              </ul>
            </section>
          ))}
        </div>
        <div className="flex justify-end border-t border-slate-100 px-5 py-3">
          <Button type="button" onClick={onClose}>
            知道了
          </Button>
        </div>
      </div>
    </div>,
    document.body,
  )
}
