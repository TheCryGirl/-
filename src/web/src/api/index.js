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
    }
    return Promise.reject(err)
  },
)

/* ---------------- 用户 ---------------- */
export const userApi = {
  login: (userId, password) => http.post('/users/login', { userId, password }),
  register: (user) => http.post('/users', user),
  getById: (userId) => http.get(`/users/${userId}`),
  getNickname: (userId) => http.get(`/users/getname/${userId}`),
  update: (user) => http.put('/users', user),
}

/* ---------------- 商品 ---------------- */
export const productApi = {
  detail: (productId) => http.get(`/products/${productId}`),
  byUser: (userId) => http.get(`/products/user/${userId}`),
  byUserAll: (userId) => http.get(`/products/user/${userId}/all`),
  byStatus: (status) => http.get(`/products/status/${status}`),
  search: (keyword) => http.get('/products/search', { params: { keyword } }),
  byClassification: (classification) =>
    http.get(`/products/classification/${encodeURIComponent(classification)}`),
  byPriceRange: (low, high) => http.get(`/products/price/${low}/${high}`),
  createWithImage: (formData) =>
    http.post('/products/with-image', formData, {
      headers: { 'Content-Type': 'multipart/form-data' },
    }),
  updateStatus: (productId, status) => http.put(`/products/${productId}/status`, { status }),
  remove: (productId) => http.delete(`/products/${productId}`),
}

/* ---------------- 订单 ---------------- */
export const orderApi = {
  detail: (orderId) => http.get(`/orders/${orderId}`),
  byBuyer: (buyerId) => http.get(`/orders/buyer/${buyerId}`),
  bySeller: (sellerId) => http.get(`/orders/seller/${sellerId}`),
  remove: (orderId) => http.delete(`/orders/${orderId}`),
}

/* ---------------- 求购请求 ---------------- */
export const purchaseApi = {
  byBuyer: (buyerId) => http.get(`/purchase-requests/buyer/${buyerId}`),
  create: (productId) => http.post(`/purchase-requests/${productId}`),
  reject: (productId) => http.put(`/purchase-requests/${productId}/reject`),
}

/* ---------------- 消息 ---------------- */
export const messageApi = {
  conversation: (a, b) => http.get('/messages/conversation', { params: { userId: a, otherId: b } }),
  send: (payload) => http.post('/messages', payload),
  partners: (userId) => http.get(`/messages/partners/${userId}`),
  unreadCount: (userId) => http.get('/messages/unread/count', { params: { userId } }),
  markRead: (messageId) => http.post('/messages/read', { messageId }),
}

/* ---------------- AI ---------------- */
export const aiApi = {
  chat: (payload) => http.post('/ai/chat', payload),
  history: (userId) => http.get('/ai/history', { params: { userId } }),
}

export default http
