import { ElMessageBox } from 'element-plus'
import { notifySuccess } from '@/utils/notify'

export interface ConfirmOptions {
  title?: string
  confirmText?: string
  successMsg?: string
}

/**
 * 删除（或任何需要二次确认的操作）的一行式 composable。
 *
 * @example
 * const { confirm } = useConfirm()
 * await confirm(`确定删除「${row.name}」？`, () => deleteProvider(row.id))
 */
export function useConfirm() {
  const confirm = async (
    message: string,
    apiFn: () => Promise<unknown>,
    options: ConfirmOptions = {},
  ): Promise<boolean> => {
    const {
      title      = '删除确认',
      confirmText = '删除',
      successMsg  = '删除成功',
    } = options

    try {
      await ElMessageBox.confirm(message, title, {
        confirmButtonText: confirmText,
        cancelButtonText:  '取消',
        type:              'warning',
      })
    } catch {
      return false
    }

    await apiFn()
    notifySuccess(successMsg)
    return true
  }

  return { confirm }
}
