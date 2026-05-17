import { get, post, put, del } from '@/utils/request'

export type ProjectRole = 'OWNER' | 'DEVELOPER' | 'OPERATOR' | 'REVIEWER' | 'VIEWER'
export type ProjectStatus = 'ACTIVE' | 'DISABLED'

export interface Project {
  id: number
  workspaceId: number
  name: string
  code: string
  status: ProjectStatus
  createdAt?: string
  updatedAt?: string
}

export interface ProjectMember {
  id: number
  workspaceId: number
  projectId: number
  userId: number
  username: string
  displayName: string
  role: ProjectRole
  status: ProjectStatus
}

export const getProjects = (): Promise<Project[]> =>
  get('/v1/projects')

export const getProjectMembers = (projectId: number): Promise<ProjectMember[]> =>
  get(`/v1/projects/${projectId}/members`)

export const addProjectMember = (projectId: number, userId: number, role: ProjectRole): Promise<ProjectMember> =>
  post(`/v1/projects/${projectId}/members`, { userId, role })

export const updateProjectMemberRole = (
  projectId: number,
  userId: number,
  role: ProjectRole,
): Promise<ProjectMember> =>
  put(`/v1/projects/${projectId}/members/${userId}/role`, { role })

export const removeProjectMember = (projectId: number, userId: number): Promise<void> =>
  del(`/v1/projects/${projectId}/members/${userId}`)
