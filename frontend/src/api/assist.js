import { request } from './client'

const query = (params = {}) => {
  const searchParams = new URLSearchParams()
  Object.entries(params).forEach(([key, value]) => {
    if (value !== undefined && value !== null && value !== '') searchParams.set(key, value)
  })
  const value = searchParams.toString()
  return value ? `?${value}` : ''
}

export const search = (params = {}) => request(`/search${query(params)}`)
export const fetchDashboardTasks = () => request('/dashboard/tasks')
export const fetchDashboardSummary = () => request('/dashboard/summary')
