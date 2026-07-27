import { describe, expect, it } from 'vitest'
import { agentErrorMessage } from './messages'

describe('agentErrorMessage', () => {
  it('maps safe stable runtime failures without exposing provider internals', () => {
    expect(agentErrorMessage({ errorCode: 'AGENT_TIMEOUT' })).toContain('超时')
    expect(agentErrorMessage({ errorCode: 'AGENT_UNKNOWN_TOOL' })).toContain('非法的工具')
    expect(agentErrorMessage({ errorCode: 'AGENT_EVIDENCE_INVALID' })).toContain('证据校验')
  })
})
