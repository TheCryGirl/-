import axios from 'axios'

// 开发期走 Vite 代理（见 vite.config.js），生产期同源部署，baseURL 留空即可
const http = axios.create({
  baseURL: '/api',
  timeout: 15000,
})

// 每次请求自动带上登录令牌
http.interceptors.request.use((config) => {
  const token = localStorage.getItem('token')
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

// 统一处理 401：清掉本地登录态，交给路由守卫跳登录页
http.interceptors.response.use(
  (res) => res,
  (err) => {
    if (err.response?.status === 401) {
      localStorage.removeItem('token')
      localStorage.removeItem('user')
      // 通知 App 清空内存登录态并跳转登录页，避免页面仍显示为已登录
      window.dispatchEvent(new Event('auth:expired'))
    }
    return Promise.reject(err)
  },
)

function extractData(res) {
  // 统一按契约结构取 data；如果后端直接返回数组/对象也兼容
  const data = res.data
  if (data && typeof data === 'object' && 'data' in data) {
    return data.data
  }
  return data
}

function extractList(res) {
  const data = extractData(res)
  if (Array.isArray(data)) return data
  return data?.list || []
}

/**
 * 兼容两种返回：已经拆开的业务数据，或统一的 { code, message, data } 包装。
 * 业务失败（code !== 0）返回 null，调用方据此提示错误，避免把错误包装当成数据渲染。
 */
export function unwrapResult(payload) {
  if (payload && typeof payload === 'object' && 'code' in payload) {
    return payload.code === 0 ? payload.data ?? null : null
  }
  return payload ?? null
}

/**
 * 判断统一响应是否成功。Result<Void> 成功时 data 也是 null，
 * 不能靠 unwrapResult 区分成功与失败，写操作应改用本函数。
 */
export function isOk(payload) {
  if (payload && typeof payload === 'object' && 'code' in payload) {
    return payload.code === 0
  }
  return true
}

/* ---------------- 用户 ---------------- */
export const userApi = {
  register: (payload) => http.post('/users/register', payload),
  login: (account, password) => http.post('/users/login', { account, password }),
  logout: () => http.post('/users/logout'),
  me: () => http.get('/users/me'),
  updateMe: (payload) => http.put('/users/me', payload),
  list: (params) => http.get('/users', { params }),
  updateStatus: (userId, payload) => http.put(`/users/${userId}/status`, payload),
  updateRole: (userId, role) => http.put(`/users/${userId}/role`, { role }),
  uploadAvatar: (formData) =>
    http.post('/users/avatar', formData, {
      headers: { 'Content-Type': 'multipart/form-data' },
    }),
}

/* ---------------- 收货地址 ---------------- */
export const addressApi = {
  list: (userId) => http.get(`/users/${userId}/addresses`),
  create: (payload) => http.post('/users/addresses', payload),
  update: (addressId, payload) => http.put(`/users/addresses/${addressId}`, payload),
  remove: (addressId) => http.delete(`/users/addresses/${addressId}`),
  setDefault: (addressId) => http.put(`/users/addresses/${addressId}/default`),
}

/* ---------------- 店铺（#49） ---------------- */
export const shopApi = {
  mine: () => http.get('/shops/mine'),
  updateMine: (payload) => http.put('/shops/mine', payload),
  list: (params) => http.get('/shops', { params }),
  updateStatus: (shopId, status) => http.put(`/shops/${shopId}/status`, { status }),
  bySeller: (sellerId) => http.get(`/shops/seller/${sellerId}`),
}

/* ---------------- 收藏与浏览足迹（#52） ---------------- */
export const favoriteApi = {
  add: (productId) => http.post('/favorites', { productId }),
  remove: (productId) => http.delete(`/favorites/${productId}`),
  list: (params) => http.get('/favorites', { params }),
  status: (productId) => http.get('/favorites/status', { params: { productId } }),
}

export const historyApi = {
  record: (productId) => http.post('/history', { productId }),
  recent: (limit = 20) => http.get('/history', { params: { limit } }),
  remove: (productId) => http.delete(`/history/${productId}`),
  clear: () => http.delete('/history'),
}

/* ---------------- 商品 ---------------- */
export const productApi = {
  list: (params) => http.get('/products', { params }),
  detail: (productId) => http.get(`/products/${productId}`),
  categories: () => http.get('/categories'),
  create: (payload) => http.post('/products', payload),
  update: (productId, payload) => http.put(`/products/${productId}`, payload),
  updateStatus: (productId, status) => http.put(`/products/${productId}/status`, { status }),
  audit: (productId, payload) => http.put(`/products/${productId}/audit`, payload),
  mine: () => http.get('/products/mine'),
  createSku: (productId, payload) => http.post(`/products/${productId}/skus`, payload),
  updateStock: (skuId, stockAvailable) =>
    http.put(`/products/skus/${skuId}/stock`, { stockAvailable }),
  uploadImage: (formData) =>
    http.post('/products/images', formData, {
      headers: { 'Content-Type': 'multipart/form-data' },
    }),
}

/* ---------------- 购物车 ---------------- */
export const configApi = {
  get: () => http.get('/config'),
  update: (payload) => http.put('/config', payload),
  history: (limit = 20) => http.get('/config/history', { params: { limit } }),
}

export const cartApi = {
  get: () => http.get('/cart'),
  add: (payload) => http.post('/cart/items', payload),
  updateQuantity: (cartItemId, quantity) => http.put(`/cart/items/${cartItemId}`, { quantity }),
  updateSelected: (cartItemId, selected) => http.put(`/cart/items/${cartItemId}/select`, { selected }),
  remove: (cartItemId) => http.delete(`/cart/items/${cartItemId}`),
  clear: () => http.delete('/cart'),
}

/* ---------------- 订单 ---------------- */
export const orderApi = {
  create: (payload, idempotencyKey) =>
    http.post('/orders', payload, { headers: { 'Idempotency-Key': idempotencyKey } }),
  list: (params) => http.get('/orders', { params }),
  detail: (orderId) => http.get(`/orders/${orderId}`),
  cancel: (orderId) => http.put(`/orders/${orderId}/cancel`),
  ship: (orderId, payload) => http.put(`/orders/${orderId}/ship`, payload),
  confirm: (orderId) => http.put(`/orders/${orderId}/confirm`),
}

/* ---------------- 支付 ---------------- */
export const paymentApi = {
  create: (payload, idempotencyKey) =>
    http.post('/payments', payload, { headers: { 'Idempotency-Key': idempotencyKey } }),
  detail: (paymentId) => http.get(`/payments/${paymentId}`),
  byOrder: (orderId) => http.get(`/payments/order/${orderId}`),
  mockPay: (payload) => http.post('/payments/mock/pay', payload),
  mockRefund: (payload) => http.post('/payments/mock/refund', payload),
  refund: (paymentId, payload) => http.post(`/payments/${paymentId}/refund`, payload),
  auditRefund: (refundId, payload) => http.put(`/payments/refunds/${refundId}/audit`, payload),
  refunds: (params) => http.get('/payments/refunds', { params }),
  refundDetail: (refundId) => http.get(`/payments/refunds/${refundId}`),

  /* ---- 售后中心 ---- */
  afterSales: (params) => http.get('/payments/after-sales', { params }),
  afterSaleDetail: (ticketId) => http.get(`/payments/after-sales/${ticketId}`),
  createAfterSale: (payload, idempotencyKey) =>
    http.post('/payments/after-sales', payload, {
      headers: { 'Idempotency-Key': idempotencyKey },
    }),
  auditAfterSale: (ticketId, payload) =>
    http.put(`/payments/after-sales/${ticketId}/audit`, payload),
  shipAfterSaleReturn: (ticketId, payload) =>
    http.put(`/payments/after-sales/${ticketId}/return-shipment`, payload),
  receiveAfterSaleReturn: (ticketId, payload) =>
    http.put(`/payments/after-sales/${ticketId}/receive`, payload),
  reshipAfterSale: (ticketId, payload) =>
    http.put(`/payments/after-sales/${ticketId}/reship`, payload),
}

export { http, extractData, extractList }
export default http
