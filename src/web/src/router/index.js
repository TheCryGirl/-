import { createRouter, createWebHistory } from 'vue-router'
import { isLoggedIn, currentRole } from '../stores/auth'

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
    path: '/cart',
    name: 'cart',
    component: () => import('../views/Cart.vue'),
    meta: { title: '购物车', requiresAuth: true },
  },
  {
    path: '/orders/confirm',
    name: 'order-confirm',
    component: () => import('../views/OrderConfirm.vue'),
    meta: { title: '确认订单', requiresAuth: true },
  },
  {
    path: '/orders/:id/pay',
    name: 'order-pay',
    component: () => import('../views/OrderPay.vue'),
    meta: { title: '支付订单', requiresAuth: true },
  },
  {
    path: '/orders',
    name: 'orders',
    component: () => import('../views/OrderList.vue'),
    meta: { title: '我的订单', requiresAuth: true },
  },
  {
    path: '/orders/:id',
    name: 'order-detail',
    component: () => import('../views/OrderDetail.vue'),
    meta: { title: '订单详情', requiresAuth: true },
  },
  {
    path: '/after-sales',
    name: 'after-sales',
    component: () => import('../views/AfterSales.vue'),
    meta: { title: '我的售后', requiresAuth: true },
  },
  {
    path: '/profile',
    name: 'profile',
    component: () => import('../views/Profile.vue'),
    meta: { title: '个人中心', requiresAuth: true },
  },
  {
    path: '/favorites',
    name: 'favorites',
    component: () => import('../views/Favorites.vue'),
    meta: { title: '我的收藏', requiresAuth: true },
  },
  {
    path: '/history',
    name: 'browse-history',
    component: () => import('../views/BrowseHistory.vue'),
    meta: { title: '浏览足迹', requiresAuth: true },
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
  // 卖家中心
  {
    path: '/seller/products',
    name: 'seller-products',
    component: () => import('../views/seller/SellerProducts.vue'),
    meta: { title: '商品管理', requiresAuth: true, role: 'SELLER' },
  },
  {
    path: '/seller/products/new',
    name: 'seller-product-new',
    component: () => import('../views/seller/SellerProductEdit.vue'),
    meta: { title: '发布商品', requiresAuth: true, role: 'SELLER' },
  },
  {
    path: '/seller/products/:id/edit',
    name: 'seller-product-edit',
    component: () => import('../views/seller/SellerProductEdit.vue'),
    meta: { title: '编辑商品', requiresAuth: true, role: 'SELLER' },
  },
  {
    path: '/seller/shipments',
    name: 'seller-shipments',
    component: () => import('../views/seller/SellerShipments.vue'),
    meta: { title: '待发货', requiresAuth: true, role: 'SELLER' },
  },
  {
    path: '/seller/shop',
    name: 'seller-shop',
    component: () => import('../views/seller/ShopProfile.vue'),
    meta: { title: '店铺资料', requiresAuth: true, role: 'SELLER' },
  },
  {
    path: '/seller/refunds',
    name: 'seller-refunds',
    component: () => import('../views/seller/SellerRefunds.vue'),
    meta: { title: '退款审核', requiresAuth: true, role: 'SELLER' },
  },
  {
    path: '/seller/after-sales',
    name: 'seller-after-sales',
    component: () => import('../views/seller/SellerAfterSales.vue'),
    meta: { title: '售后处理', requiresAuth: true, role: 'SELLER' },
  },
  // 管理中心（管理员）
  {
    path: '/admin/users',
    name: 'admin-users',
    component: () => import('../views/admin/AdminUsers.vue'),
    meta: { title: '用户管理', requiresAuth: true, role: 'ADMIN' },
  },
  {
    path: '/admin/products',
    name: 'admin-products',
    component: () => import('../views/admin/AdminProducts.vue'),
    meta: { title: '商品审核', requiresAuth: true, role: 'ADMIN' },
  },
  {
    path: '/admin/shops',
    name: 'admin-shops',
    component: () => import('../views/admin/AdminShops.vue'),
    meta: { title: '店铺管理', requiresAuth: true, role: 'ADMIN' },
  },
  {
    path: '/admin/after-sales',
    name: 'admin-after-sales',
    component: () => import('../views/admin/AdminAfterSales.vue'),
    meta: { title: '售后仲裁', requiresAuth: true, role: 'ADMIN' },
  },
    {
      path: '/admin/shops',
      name: 'admin-shops',
      component: () => import('../views/admin/AdminShops.vue'),
      meta: { title: '店铺管理', requiresAuth: true, role: 'ADMIN' },
    },
    {
      path: '/admin/refunds',
      name: 'admin-refunds',
      component: () => import('../views/admin/AdminRefunds.vue'),
      meta: { title: '退款复核', requiresAuth: true, role: 'ADMIN' },
    },
    {
      path: '/admin/finance',
      name: 'admin-finance',
      component: () => import('../views/admin/AdminFinance.vue'),
      meta: { title: '资金对账', requiresAuth: true, role: 'ADMIN' },
    },
    {
      path: '/admin/risk',
      name: 'admin-risk',
      component: () => import('../views/admin/AdminRisk.vue'),
      meta: { title: '基础风控', requiresAuth: true, role: 'ADMIN' },
    },
    {
      path: '/admin/config',
      name: 'admin-config',
      component: () => import('../views/admin/AdminConfig.vue'),
      meta: { title: '系统配置', requiresAuth: true, role: 'ADMIN' },
    },
  {
    path: '/admin/audit-logs',
    name: 'admin-audit-logs',
    component: () => import('../views/admin/AdminAuditLogs.vue'),
    meta: { title: '审计日志', requiresAuth: true, role: 'ADMIN' },
  },
  { path: '/:pathMatch(.*)*', redirect: '/products' },
]

const router = createRouter({
  history: createWebHistory(),
  routes,
  scrollBehavior: () => ({ top: 0 }),
})

router.beforeEach((to) => {
  document.title = to.meta.title ? `${to.meta.title} - 在线商城` : '在线商城'

  if (to.meta.requiresAuth && !isLoggedIn.value) {
    sessionStorage.setItem('redirectAfterLogin', to.fullPath)
    return { name: 'login' }
  }
  if (to.meta.guestOnly && isLoggedIn.value) {
    return { name: 'products' }
  }
  if (to.meta.role && currentRole.value !== 'ADMIN' && currentRole.value !== to.meta.role) {
    return { name: 'products' }
  }
  return true
})

export default router