async function showMessage(message: string, type: 'success' | 'error' | 'warning', duration: number) {
  const [{ ElMessage }] = await Promise.all([
    import('element-plus/es/components/message/index'),
    import('element-plus/es/components/message/style/css'),
  ])
  ElMessage({ message, type, duration })
}

export const notifySuccess = (msg: string) =>
  void showMessage(msg, 'success', 2500)

export const notifyError = (msg: string) =>
  void showMessage(msg, 'error', 4000)

export const notifyWarning = (msg: string) =>
  void showMessage(msg, 'warning', 3000)
