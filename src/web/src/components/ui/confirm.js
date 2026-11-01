import { ElMessageBox } from 'element-plus'
// ElMessageBox 是以函数方式调用的，不会出现在模板里，
// unplugin-vue-components 的按需解析只处理模板标签，因此必须手动引入样式，
// 否则确认框渲染出来是空白的（表现为「点了没反应」）。
import 'element-plus/es/components/message-box/style/css'

/**
 * 统一确认框。
 *
 * 约定：调用方必须检查返回值，取消时不执行任何写操作：
 *
 *   if (!(await confirmAction({ message: '确定删除该商品？' }))) return
 *
 * 注意区分「用户取消」与「真实异常」：
 * ElMessageBox 在点击取消、点右上角关闭、按 ESC 时都会 reject，action 分别为
 * cancel / close。这两种属于正常交互，返回 false 即可。其他异常说明代码本身有问题，
 * 不能吞掉当成「用户取消」，否则真实 bug 会被静默掩盖。
 */
export async function confirmAction(options = {}) {
  const {
    message = '',
    title = '请确认',
    type = 'warning',
    confirmButtonText = '确定',
    cancelButtonText = '取消',
    danger = false,
  } = typeof options === 'string' ? { message: options } : options

  try {
    await ElMessageBox.confirm(message, title, {
      type,
      confirmButtonText,
      cancelButtonText,
      ...(danger ? { confirmButtonClass: 'el-button--danger' } : {}),
    })
    return true
  } catch (err) {
    const action = err?.action ?? err
    if (action === 'cancel' || action === 'close') {
      return false
    }
    // 非取消类异常：记录并继续抛出，交由上层错误处理
    console.error('[confirmAction] 确认框异常', err)
    throw err
  }
}

export default confirmAction
