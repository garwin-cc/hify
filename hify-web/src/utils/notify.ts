import { ElMessage } from 'element-plus'

export const notifySuccess = (msg: string) =>
  ElMessage({ message: msg, type: 'success', duration: 2500 })

export const notifyError = (msg: string) =>
  ElMessage({ message: msg, type: 'error', duration: 4000 })

export const notifyWarning = (msg: string) =>
  ElMessage({ message: msg, type: 'warning', duration: 3000 })
