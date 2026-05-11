import { del, get, post, put } from '@/utils/request'
import type { PageData } from '@/components/HifyTable.vue'

export type UserRole = 'ADMIN' | 'EDITOR' | 'VIEWER'
export type UserStatus = 'ACTIVE' | 'DISABLED'

export interface UserInfo {
  id: number
  username: string
  displayName: string
  role: UserRole
  status: UserStatus
  lastLoginAt?: string
  createdAt?: string
}

export interface LoginResp {
  token: string
  user: UserInfo
}

export interface CreateUserReq {
  username: string
  displayName: string
  password: string
  role: UserRole
}

export interface UpdateUserReq {
  displayName: string
  role: UserRole
  status: UserStatus
}

export const login = (username: string, password: string): Promise<LoginResp> =>
  post('/v1/auth/login', { username, password })

export const logout = (): Promise<void> =>
  post('/v1/auth/logout', {})

export const getMe = (): Promise<UserInfo> =>
  get('/v1/auth/me')

export const changePassword = (oldPassword: string, newPassword: string): Promise<void> =>
  post('/v1/auth/change-password', { oldPassword, newPassword })

export const getUserList = (page: number, size: number): Promise<PageData<UserInfo>> =>
  get('/v1/users', { page, size })

export const createUser = (data: CreateUserReq): Promise<UserInfo> =>
  post('/v1/users', data)

export const updateUser = (id: number, data: UpdateUserReq): Promise<UserInfo> =>
  put(`/v1/users/${id}`, data)

export const resetUserPassword = (id: number, password: string): Promise<void> =>
  put(`/v1/users/${id}/password`, { password })

export const deleteUser = (id: number): Promise<void> =>
  del(`/v1/users/${id}`)
