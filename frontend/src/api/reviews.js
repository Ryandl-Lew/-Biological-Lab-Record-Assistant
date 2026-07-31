import { request } from './client'
import { createUuid } from '@/lib/uuid'

export const fetchReviewerCandidates = (recordId) =>
  request(`/records/${recordId}/reviewer-candidates`)
export const submitRecord = (recordId, input, idempotencyKey = createUuid()) =>
  request(`/records/${recordId}/submissions`, {
    method: 'POST',
    headers: { 'Idempotency-Key': idempotencyKey },
    body: JSON.stringify(input),
  })
export const requestReviewChanges = (recordId, reviewId, comment) =>
  request(`/records/${recordId}/reviews/${reviewId}/request-changes`, {
    method: 'POST',
    body: JSON.stringify({ comment }),
  })
export const approveReview = (recordId, reviewId, comment) =>
  request(`/records/${recordId}/reviews/${reviewId}/approve`, {
    method: 'POST',
    body: JSON.stringify({ comment }),
  })
export const fetchPendingReviews = () => request('/reviews/pending')
