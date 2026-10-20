import { createRouter, createWebHistory } from 'vue-router'
import { isLoggedIn } from '../stores/auth'

const routes = [
  { path: '/', redirect: '/products' },
  {
    path: '/products',
    name: 'products',
    component: () => import('../views/ProductList.vue'),
    meta: { title: '商品列表' },
  },
  {
    path: '/products/:id',
    name: 'product-detail',
    component: () => import('../views/ProductDetail.vue'),
    meta: { title: '商品详情' },
  },
  {
    path: '/login',
    name: 'login',
    component: () => import('../views/Login.vue'),
    meta: { title: '登录', guestOnly: true },
  },
  {
    path: '/register',
    name: 'register',
    component: () => import('../views/Register.vue'),
    meta: { title: '注册', guestOnly: true },
  },
  {
    path: '/sell',
    name: 'sell',
    component: () => import('../views/Publish.vue'),
    meta: { title: '发布商品', requiresAuth: true },
  },
  {
    path: '/my/products',
    name: 'my-products',
    component: () => import('../views/MyProducts.vue'),
    meta: { title: '我的商品', requiresAuth: true },
  },
  {
    path: '/my/orders',
    name: 'my-orders',
    component: () => import('../views/MyOrders.vue'),
    meta: { title: '我的订单', requiresAuth: true },
  },
  {
    path: '/messages',
    name: 'messages',
    component: () => import('../views/Messages.vue'),
    meta: { title: '消息', requiresAuth: true },
  },
  { path: '/:pathMatch(.*)*', redirect: '/products' },
]

const router = createRouter({
  history: createWebHistory(),
  routes,
  scrollBehavior: () => ({ top: 0 }),
})

router.beforeEach((to) => {
  document.title = to.meta.title ? `${to.meta.title} - 校园二手交易平台` : '校园二手交易平台'

  if (to.meta.requiresAuth && !isLoggedIn.value) {
    sessionStorage.setItem('redirectAfterLogin', to.fullPath)
    return { name: 'login' }
  }
  if (to.meta.guestOnly && isLoggedIn.value) {
    return { name: 'products' }
  }
  return true
})

export default router
