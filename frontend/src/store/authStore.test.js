import { beforeEach, describe, expect, it } from 'vitest'
import { useAuthStore } from './authStore'

describe('authStore session handling', () => {
  beforeEach(() => { localStorage.clear(); useAuthStore.setState({ currentUser: { id: 'x' }, token: 'old', loading: false }) })
  it('clears token and user after a 401 event', () => {
    localStorage.setItem('auth_token', 'old')
    const clear = useAuthStore.getState().clearSession
    window.addEventListener('bionote:unauthorized', clear, { once: true })
    window.dispatchEvent(new Event('bionote:unauthorized'))
    expect(localStorage.getItem('auth_token')).toBeNull()
    expect(useAuthStore.getState().currentUser).toBeNull()
  })
  it('does not trust a cached user when no token exists', async () => {
    useAuthStore.setState({ currentUser: { id: 'cached' }, loading: true })
    await useAuthStore.getState().restoreSession()
    expect(useAuthStore.getState().currentUser).toBeNull()
    expect(useAuthStore.getState().loading).toBe(false)
  })
})
