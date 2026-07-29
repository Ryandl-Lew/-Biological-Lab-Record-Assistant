UPDATE attachments
SET previewable = TRUE
WHERE media_type = 'text/markdown'
  AND previewable = FALSE;
