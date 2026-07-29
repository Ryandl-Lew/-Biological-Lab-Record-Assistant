export function agentErrorMessage(error) {
  const code = error?.code || error?.errorCode
  if (code === 'AGENT_DISABLED') return 'Agent 功能当前未启用；已有报告仍可查看。'
  if (code === 'MODEL_PROVIDER_UNAVAILABLE') return '模型服务暂不可用，可以稍后重新运行。'
  if (code === 'MODEL_PROVIDER_ERROR') return '模型返回无法处理，可以稍后重新运行。'
  if (code === 'AGENT_TIMEOUT') return '模型服务或运行超时，未保存报告。'
  if (['AGENT_UNKNOWN_TOOL', 'AGENT_TOOL_NOT_ALLOWED', 'AGENT_TOOL_ARGUMENTS_INVALID'].includes(code)) return '模型请求了未注册或非法的工具，运行已安全终止。'
  if (['AGENT_INVALID_OUTPUT', 'AGENT_EVIDENCE_INVALID'].includes(code)) return '模型结果未通过结构或证据校验，因此没有保存不可靠报告。'
  if (code === 'AGENT_RATE_LIMITED') return '请求过于频繁或并发运行已达上限，请稍后重试。'
  if (code === 'AGENT_LIMIT_EXCEEDED') return '模型调用或工具步数已达上限，未生成报告；可稍后重新运行。'
  if (code === 'VALIDATION_ERROR') {
    const details = Object.values(error?.fieldErrors || {}).filter(Boolean)
    if (details.length) return `请检查输入内容：${details.join('；')}`
  }
  return error?.message || error?.errorMessage || 'Agent 请求失败，请稍后重试。'
}

/** Keep chat history inside API validation limits. */
export function trimChatHistory(messages, { maxItems = 12, maxChars = 3500 } = {}) {
  return (messages || [])
    .filter((item) => item?.role === 'user' || item?.role === 'assistant')
    .slice(-maxItems)
    .map((item) => ({
      role: item.role,
      content: String(item.content || '').slice(0, maxChars),
    }))
}
