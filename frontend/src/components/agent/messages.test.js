import { describe, expect, it } from 'vitest'
import { agentErrorMessage, trimChatHistory } from './messages'

describe('agentErrorMessage', () => {
  it('maps safe stable runtime failures without exposing provider internals', () => {
    expect(agentErrorMessage({ errorCode: 'AGENT_TIMEOUT' })).toContain('超时')
    expect(agentErrorMessage({ errorCode: 'AGENT_UNKNOWN_TOOL' })).toContain('非法的工具')
    expect(agentErrorMessage({ errorCode: 'AGENT_EVIDENCE_INVALID' })).toContain('证据校验')
  })

  it('surfaces validation field errors', () => {
    expect(agentErrorMessage({
      code: 'VALIDATION_ERROR',
      message: '请检查输入内容',
      fieldErrors: { 'history[1].content': '长度需要在0和4000之间' },
    })).toContain('长度需要在0和4000之间')
  })
})

describe('trimChatHistory', () => {
  it('keeps recent turns and truncates long content', () => {
    const history = trimChatHistory([
      { role: 'user', content: 'a'.repeat(10) },
      { role: 'assistant', content: 'b'.repeat(5000) },
      { role: 'user', content: 'x轴是发酵时长，y轴是耗碱量（显示值）' },
    ], { maxItems: 2, maxChars: 100 })
    expect(history).toHaveLength(2)
    expect(history[0].content).toHaveLength(100)
    expect(history[1].content).toContain('发酵时长')
  })
})
