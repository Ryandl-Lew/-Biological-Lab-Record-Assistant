import RecordChatPanel from './RecordChatPanel'

export default function RecordSummaryPanel({ record, initialMessages, onAutoSave, onNewChat }) {
  return (
    <RecordChatPanel
      record={record}
      initialMessages={initialMessages}
      onAutoSave={onAutoSave}
      onNewChat={onNewChat}
    />
  )
}
