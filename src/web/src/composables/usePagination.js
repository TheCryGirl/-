import { computed, ref } from 'vue'

/**
 * 统一分页约定与状态管理。
 *
 * 约定（全组统一，写进 docs/ui-plan/）：
 * - 页码从 1 开始，页面内部 state 也是 1 起；后端若为 0 起，在 api 适配层转换，不要在页面里混用
 * - 每页条数参数名 `size`，页码参数名 `page`
 * - 列表接口返回 { list, total }
 *
 * 这里只管理分页状态，不发起请求 —— 各页面的请求参数与时机差异较大，
 * 由页面在 onMounted / watch 中调用 fetch。
 *
 * 最常出错的一点已经封装掉：删除若干条后若当前页已超出总页数，
 * 自动回退到最后一页，避免停在空白页。
 *
 *   const { page, size, total, onPageChange, onSizeChange, afterDelete } = usePagination({ fetch })
 */
export function usePagination(options = {}) {
  const { defaultSize = 10, defaultPage = 1, onChange } = options

  const page = ref(defaultPage)
  const size = ref(defaultSize)
  const total = ref(0)
  const loading = ref(false)

  const totalPages = computed(() => Math.max(1, Math.ceil(total.value / size.value)))

  function notify() {
    if (typeof onChange === 'function') onChange()
  }

  function onPageChange(next) {
    page.value = next
    notify()
  }

  function onSizeChange(next) {
    size.value = next
    // 改变每页条数后页码重置为 1，避免越界
    page.value = 1
    notify()
  }

  /**
   * 写操作（删除等）完成、数据条数减少后调用。
   * 若当前页已无数据且不是第一页，回退一页再刷新。
   */
  function afterDelete(remainingTotal) {
    if (typeof remainingTotal === 'number') {
      total.value = remainingTotal
    }
    const maxPage = Math.max(1, Math.ceil(total.value / size.value))
    if (page.value > maxPage) {
      page.value = maxPage
    }
    notify()
  }

  function reset() {
    page.value = defaultPage
    notify()
  }

  return {
    page,
    size,
    total,
    loading,
    totalPages,
    onPageChange,
    onSizeChange,
    afterDelete,
    reset,
  }
}

export default usePagination
