export function agentErrorMessage(error) {
  const code = error?.code || error?.errorCode
  if (code === 'AGENT_DISABLED') return 'Agent 功能当前未启用；已有报告仍可查看。'
  if (code === 'MODEL_PROVIDER_UNAVAILABLE') return '模型服务暂不可用，可以稍后重新运行。'
  if (code === 'AGENT_TIMEOUT') return '模型服务或运行超时，未保存报告。'
  if (['AGENT_UNKNOWN_TOOL', 'AGENT_TOOL_NOT_ALLOWED', 'AGENT_TOOL_ARGUMENTS_INVALID'].includes(code)) return '模型请求了未注册或非法的工具，运行已安全终止。'
  if (['AGENT_INVALID_OUTPUT', 'AGENT_EVIDENCE_INVALID'].includes(code)) return '模型结果未通过结构或证据校验，因此没有保存不可靠报告。'
  if (code === 'AGENT_RATE_LIMITED') return '请求过于频繁或并发运行已达上限，请稍后重试。'
  return error?.message || 'Agent 请求失败，请稍后重试。'
}
