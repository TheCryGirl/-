import { ElMessage } from 'element-plus'
// 函数式调用的组件不会被 unplugin 按需解析，样式必须手动引入
import 'element-plus/es/components/message/style/css'

/**
 * 统一消息提示。
 *
 * 约定：失败提示统一读取后端返回的 message，拿不到时用调用方给的兜底文案。
 * 取消确认框不算失败，调用方不应在此提示。
 */
export function showSuccess(message = '操作成功') {
  ElMessage.success(message)
}

export function showError(message = '操作失败') {
  ElMessage.error(message)
}

export function showWarning(message) {
  ElMessage.warning(message)
}

export function showInfo(message) {
  ElMessage.info(message)
}

/**
 * 从 Axios 错误或统一响应体中提取可展示的错误文案。
 * 优先业务 message，其次 HTTP 错误体 message，最后用兜底文案。
 */
export function resolveErrorMessage(error, fallback = '操作失败') {
  if (!error) return fallback
  // 统一响应体 { code, message } 直接传入
  if (typeof error === 'object' && typeof error.message === 'string' && !error.response) {
    return error.message || fallback
  }
  const data = error.response?.data
  if (data && typeof data.message === 'string' && data.message) {
    return data.message
  }
  if (typeof error.message === 'string' && error.message) {
    return error.message
  }
  return fallback
}

export default { showSuccess, showError, showWarning, showInfo, resolveErrorMessage }
