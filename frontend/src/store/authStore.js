import { create } from 'zustand'
import { getCurrentUser, login as loginApi, logout as logoutApi, register as registerApi } from '@/api/auth'
import { updateMyProfile, uploadMyAvatar } from '@/api/users'

const clearToken = () => localStorage.removeItem('auth_token')

export const useAuthStore = create((set) => ({
  currentUser: null,
  token: localStorage.getItem('auth_token'),
  loading: true,
  error: null,

  restoreSession: async () => {
    const token = localStorage.getItem('auth_token')
    if (!token) {
      set({ currentUser: null, token: null, loading: false })
      return
    }
    try {
      const user = await getCurrentUser()
      set({ currentUser: user, token, loading: false, error: null })
    } catch (error) {
      clearToken()
      set({ currentUser: null, token: null, loading: false, error })
    }
  },

  login: async ({ email, password }) => {
    const result = await loginApi({ email: email.trim(), password })
    localStorage.setItem('auth_token', result.accessToken)
    set({ currentUser: result.user, token: result.accessToken, error: null })
    return result.user
  },

  register: async ({ displayName, email, password }) => {
    const result = await registerApi({ displayName: displayName.trim(), email: email.trim(), password })
    localStorage.setItem('auth_token', result.accessToken)
    set({ currentUser: result.user, token: result.accessToken, error: null })
    return result.user
  },

  updateProfile: async (displayName) => {
    const user = await updateMyProfile(displayName)
    set({ currentUser: user })
    return user
  },

  uploadAvatar: async (file) => {
    const user = await uploadMyAvatar(file)
    set({ currentUser: user })
    return user
  },

  logout: async () => {
    try { await logoutApi() } finally {
      clearToken()
      set({ currentUser: null, token: null })
    }
  },

  clearSession: () => {
    clearToken()
    set({ currentUser: null, token: null, loading: false })
  },
}))
