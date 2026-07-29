/**
 * StatusBadge 业务状态徽章
 * 将项目 / 实验记录状态码映射为中文标签与色系（带状态圆点）。
 */
import Badge from './Badge'
import {
  PROJECT_STATUS_LABELS,
  PROJECT_STATUS_TONES,
  RECORD_STATUS_LABELS,
  RECORD_STATUS_TONES,
} from '@/domain'

/**
 * @param {Object} props
 * @param {'project'|'record'} props.kind 状态所属领域
 * @param {string} props.status 状态码
 */
export default function StatusBadge({ kind, status }) {
  const labels = kind === 'project' ? PROJECT_STATUS_LABELS : RECORD_STATUS_LABELS
  const tones = kind === 'project' ? PROJECT_STATUS_TONES : RECORD_STATUS_TONES
  return (
    <Badge tone={tones[status] ?? 'gray'} dot>
      {labels[status] ?? status}
    </Badge>
  )
}
