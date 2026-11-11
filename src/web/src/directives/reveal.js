/**
 * 滚动进入视口的渐显指令：v-reveal
 *
 * 用法：v-reveal 或 v-reveal="{ delay: 120 }"
 * 只在元素第一次进入视口时加 .is-visible，之后不再重复触发；
 * 不支持 IntersectionObserver 或用户要求减少动效时直接显示。
 */
const supportsObserver = typeof window !== 'undefined' && 'IntersectionObserver' in window

const prefersReducedMotion =
  typeof window !== 'undefined' &&
  typeof window.matchMedia === 'function' &&
  window.matchMedia('(prefers-reduced-motion: reduce)').matches

let observer = null

function getObserver() {
  if (!observer) {
    observer = new IntersectionObserver(
      (entries) => {
        for (const entry of entries) {
          if (!entry.isIntersecting) continue
          entry.target.classList.add('is-visible')
          observer.unobserve(entry.target)
        }
      },
      { rootMargin: '0px 0px -8% 0px', threshold: 0.08 },
    )
  }
  return observer
}

export const revealDirective = {
  mounted(el, binding) {
    el.classList.add('jd-reveal')
    const delay = Number(binding?.value?.delay) || 0
    if (delay > 0) el.style.transitionDelay = `${delay}ms`

    if (!supportsObserver || prefersReducedMotion) {
      el.classList.add('is-visible')
      return
    }
    getObserver().observe(el)
  },
  unmounted(el) {
    if (observer) observer.unobserve(el)
  },
}

export default revealDirective