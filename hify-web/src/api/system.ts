import { del, get, post, put } from '@/utils/request'

export interface HealthStatus {
  status?: string
  [key: string]: unknown
}

export interface IdentityProvider {
  id: number
  name: string
  type: 'OIDC' | 'LDAP' | 'SAML' | string
  issuerUrl?: string
  clientId?: string
  ldapUrl?: string
  ldapBaseDn?: string
  enabled: number
  configJson?: string
  createdAt?: string
  updatedAt?: string
}

export interface SaveIdentityProviderReq {
  name: string
  type: string
  issuerUrl?: string
  clientId?: string
  clientSecret?: string
  ldapUrl?: string
  ldapBaseDn?: string
  enabled?: number
  configJson?: string
}

export const getHealthStatus = (level: 'health' | 'liveness' | 'readiness' | 'deep' = 'health') =>
  get<HealthStatus | string>(`/v1/health${level === 'health' ? '' : `/${level}`}`)

export const getIdentityProviders = (): Promise<IdentityProvider[]> =>
  get('/v1/identity-providers')

export const createIdentityProvider = (data: SaveIdentityProviderReq): Promise<IdentityProvider> =>
  post('/v1/identity-providers', data)

export const updateIdentityProvider = (
  id: number,
  data: SaveIdentityProviderReq,
): Promise<IdentityProvider> =>
  put(`/v1/identity-providers/${id}`, data)

export const deleteIdentityProvider = (id: number): Promise<void> =>
  del(`/v1/identity-providers/${id}`)
