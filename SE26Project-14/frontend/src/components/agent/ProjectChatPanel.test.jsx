import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { sendProjectAgentChat, uploadProjectAgentReference } from '@/api/agentChat'
import ProjectChatPanel from './ProjectChatPanel'

vi.mock('@/api/agentChat', () => ({
  sendProjectAgentChat: vi.fn(),
  uploadProjectAgentReference: vi.fn(),
  deleteProjectAgentReference: vi.fn(),
}))

const uploadedReference = {
  id: 'reference-1',
  filename: 'results.csv',
  contentType: 'text/csv',
  sizeBytes: 42,
  kind: 'table',
  columns: ['time', 'value'],
}

describe('ProjectChatPanel attachments', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    uploadProjectAgentReference.mockResolvedValue(uploadedReference)
    sendProjectAgentChat.mockResolvedValue({ reply: '拟合完成' })
  })

  it('moves sent attachments from the composer into the user message', async () => {
    const user = userEvent.setup()
    const onAutoSave = vi.fn()
    render(<ProjectChatPanel project={{ id: 'project-1' }} onAutoSave={onAutoSave} />)

    await user.upload(
      screen.getByLabelText('上传问答附件'),
      new File(['time,value\n0,1'], 'results.csv', { type: 'text/csv' }),
    )
    expect(await screen.findByLabelText('待发送附件')).toHaveTextContent('results.csv')

    await user.type(screen.getByRole('textbox'), '请拟合附件数据')
    await user.click(screen.getByRole('button', { name: '发送' }))

    await waitFor(() =>
      expect(sendProjectAgentChat).toHaveBeenCalledWith(
        'project-1',
        expect.objectContaining({ referenceIds: ['reference-1'] }),
      ),
    )
    expect(screen.queryByLabelText('待发送附件')).not.toBeInTheDocument()
    expect(
      within(screen.getByLabelText('本条消息附件')).getByText('results.csv'),
    ).toBeInTheDocument()
    expect(await screen.findByText('拟合完成')).toBeInTheDocument()

    const savedUser = onAutoSave.mock.calls[0][0].find((message) => message.role === 'user')
    expect(JSON.parse(savedUser.metadata).attachments).toEqual([uploadedReference])
  })

  it('restores the attachment to the composer when sending fails', async () => {
    sendProjectAgentChat.mockRejectedValue(new Error('network error'))
    const user = userEvent.setup()
    render(<ProjectChatPanel project={{ id: 'project-1' }} />)

    await user.upload(
      screen.getByLabelText('上传问答附件'),
      new File(['x,y\n1,2'], 'results.csv', { type: 'text/csv' }),
    )
    await user.type(screen.getByRole('textbox'), '请读取附件')
    await user.click(screen.getByRole('button', { name: '发送' }))

    expect(await screen.findByLabelText('待发送附件')).toHaveTextContent('results.csv')
    expect(screen.queryByLabelText('本条消息附件')).not.toBeInTheDocument()
    expect(screen.getByRole('textbox')).toHaveValue('请读取附件')
  })

  it('keeps sent attachments in context for follow-up questions', async () => {
    sendProjectAgentChat
      .mockResolvedValueOnce({ reply: '请选择要拟合的变量' })
      .mockResolvedValueOnce({ reply: '已完成发酵时长与耗碱量拟合' })
    const user = userEvent.setup()
    render(<ProjectChatPanel project={{ id: 'project-1' }} />)

    await user.upload(
      screen.getByLabelText('上传问答附件'),
      new File(['time,alkali\n0,1\n1,2'], 'fermentation.xlsx', {
        type: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
      }),
    )
    await user.type(screen.getByRole('textbox'), '拟合')
    await user.click(screen.getByRole('button', { name: '发送' }))
    expect(await screen.findByText('请选择要拟合的变量')).toBeInTheDocument()

    await user.type(screen.getByRole('textbox'), '发酵时长 vs 耗碱量')
    await user.click(screen.getByRole('button', { name: '发送' }))

    await waitFor(() => expect(sendProjectAgentChat).toHaveBeenCalledTimes(2))
    expect(sendProjectAgentChat.mock.calls[1][1]).toEqual(
      expect.objectContaining({
        referenceIds: ['reference-1'],
        history: expect.arrayContaining([
          expect.objectContaining({ role: 'user', content: '拟合' }),
          expect.objectContaining({ role: 'assistant', content: '请选择要拟合的变量' }),
        ]),
      }),
    )
    expect(await screen.findByText('已完成发酵时长与耗碱量拟合')).toBeInTheDocument()
  })

  it('restores attachment context from a saved conversation', async () => {
    const user = userEvent.setup()
    render(
      <ProjectChatPanel
        project={{ id: 'project-1' }}
        initialMessages={[
          {
            role: 'user',
            content: '拟合',
            metadata: JSON.stringify({ attachments: [uploadedReference] }),
          },
          { role: 'assistant', content: '请选择变量' },
        ]}
      />,
    )

    expect(await screen.findByText('results.csv')).toBeInTheDocument()
    await user.type(screen.getByRole('textbox'), 'time vs value')
    await user.click(screen.getByRole('button', { name: '发送' }))

    await waitFor(() =>
      expect(sendProjectAgentChat).toHaveBeenCalledWith(
        'project-1',
        expect.objectContaining({ referenceIds: ['reference-1'] }),
      ),
    )
  })
})
