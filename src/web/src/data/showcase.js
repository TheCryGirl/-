/**
 * 商城前台的静态展示数据。
 *
 * 说明：这些内容只用于首页的氛围模块（轮播、秒杀、精选好物、分类快捷入口），
 * 不参与下单、库存与价格计算；真实商品仍来自 productApi.list。
 * 图片放在 public/media/products/ 下，构建时原样拷贝，不依赖外网。
 */

export const heroSlides = [
  {
    id: 'autumn',
    badge: '双 11 预热',
    title: '数码焕新 · 至高直降 1200',
    subtitle: '耳机、轻薄本、微单相机，精选爆款限时低至 5 折',
    cta: '立即抢购',
    to: { name: 'products', query: { sort: 'price_desc' } },
    image: '/media/products/headphones.jpg',
    theme: 'red',
    stat: { value: '5 折起', label: '限时折扣' },
  },
  {
    id: 'home',
    badge: '品质生活',
    title: '把日子过成喜欢的样子',
    subtitle: '人体工学椅、香薰、手冲咖啡，慢生活专场满 300 减 80',
    cta: '逛逛生活馆',
    to: { name: 'products', query: { keyword: '生活' } },
    image: '/media/products/chair.jpg',
    theme: 'dark',
    stat: { value: '满 300 减 80', label: '跨店满减' },
  },
  {
    id: 'student',
    badge: '开学装备',
    title: '学生党省钱清单',
    subtitle: '教材图书 9.9 元起，配件数码第二件半价，宿舍好物一次备齐',
    cta: '去凑单',
    to: { name: 'products', query: { sort: 'price_asc' } },
    image: '/media/products/books.jpg',
    theme: 'amber',
    stat: { value: '9.9 元起', label: '低价好物' },
  },
]

export const hotKeywords = ['无线耳机', '轻薄本', '运动鞋', '咖啡机', '护肤套装', '双肩包', '相机', '台灯']

/** 秒杀位：真实商品不足时用它撑住首屏，标记为「精选」而不是伪造销量 */
export const flashSaleItems = [
  { id: 'fs-headphones', title: '头戴式降噪耳机 40h 续航', price: 399, origin: 799, image: '/media/products/headphones.jpg', progress: 82, stock: 42, keyword: '耳机' },
  { id: 'fs-keyboard', title: '三模机械键盘 客制化轴体', price: 289, origin: 499, image: '/media/products/keyboard.jpg', progress: 65, stock: 18, keyword: '键盘' },
  { id: 'fs-speaker', title: '便携蓝牙音箱 360° 环绕', price: 159, origin: 299, image: '/media/products/speaker.jpg', progress: 91, stock: 7, keyword: '音箱' },
  { id: 'fs-watch', title: '极简石英腕表 蓝宝石镜面', price: 499, origin: 899, image: '/media/products/watch.jpg', progress: 48, stock: 63, keyword: '手表' },
  { id: 'fs-sneakers', title: '轻量缓震跑鞋 全掌回弹', price: 269, origin: 499, image: '/media/products/sneakers.jpg', progress: 76, stock: 25, keyword: '运动鞋' },
  { id: 'fs-coffee', title: '家用半自动咖啡机 15Bar', price: 899, origin: 1599, image: '/media/products/coffee.jpg', progress: 39, stock: 11, keyword: '咖啡' },
]

/** 精选好物：首页的氛围商品墙 */
export const curatedItems = [
  {
    id: 'c-laptop',
    keyword: '笔记本',
    title: '14 英寸轻薄笔记本 32G + 1T',
    subtitle: '2.8K 120Hz 屏 · 全金属机身 · 1.19kg',
    price: 5699,
    origin: 6499,
    image: '/media/products/laptop.jpg',
    tags: ['精选好物', '12 期免息'],
    rating: 4.9,
    comments: 2318,
  },
  {
    id: 'c-camera',
    keyword: '相机',
    title: '入门微单相机 4K 视频套机',
    subtitle: '2420 万像素 · 五轴防抖 · 翻转触控屏',
    price: 4299,
    origin: 4899,
    image: '/media/products/camera.jpg',
    tags: ['以旧换新', '包邮'],
    rating: 4.8,
    comments: 864,
  },
  {
    id: 'c-skincare',
    keyword: '护肤',
    title: '氨基酸温和洁面套装',
    subtitle: '敏感肌可用 · 补水保湿 · 三件套',
    price: 199,
    origin: 299,
    image: '/media/products/skincare.jpg',
    tags: ['第 2 件半价'],
    rating: 4.7,
    comments: 12480,
  },
  {
    id: 'c-backpack',
    keyword: '双肩包',
    title: '通勤双肩包 15.6 英寸电脑仓',
    subtitle: '防泼水面料 · 减压肩带 · 大容量分层',
    price: 219,
    origin: 359,
    image: '/media/products/backpack.jpg',
    tags: ['学生优选'],
    rating: 4.8,
    comments: 3021,
  },
  {
    id: 'c-sunglasses',
    keyword: '太阳镜',
    title: '偏光太阳镜 防紫外线 UV400',
    subtitle: '轻量 TR90 镜框 · 男女同款',
    price: 129,
    origin: 259,
    image: '/media/products/sunglasses.jpg',
    tags: ['百亿补贴'],
    rating: 4.6,
    comments: 5310,
  },
  {
    id: 'c-boots',
    keyword: '靴',
    title: '复古工装靴 头层牛皮',
    subtitle: '固特异工艺 · 耐磨橡胶大底',
    price: 459,
    origin: 799,
    image: '/media/products/shoe-boots.jpg',
    tags: ['七天无理由'],
    rating: 4.7,
    comments: 1762,
  },
  {
    id: 'c-lamp',
    keyword: '台灯',
    title: '护眼台灯 无频闪国 AA 级',
    subtitle: '五档调光 · 触控开关 · 学生宿舍可用',
    price: 169,
    origin: 259,
    image: '/media/products/lamp.jpg',
    tags: ['宿舍好物'],
    rating: 4.9,
    comments: 8842,
  },
  {
    id: 'c-smartphone',
    keyword: '手机',
    title: '5G 直屏手机 12G + 256G',
    subtitle: '5000mAh 长续航 · 一亿像素主摄',
    price: 1999,
    origin: 2499,
    image: '/media/products/smartphone.jpg',
    tags: ['销量冠军', '次日达'],
    rating: 4.8,
    comments: 15630,
  },
]

/** 分类快捷入口：图标按分类名做近似映射，真实分类仍来自接口 */
export const categoryIcons = {
  教材图书: 'book',
  数码电子: 'smartphone',
  生活用品: 'lamp',
  服饰鞋包: 'backpack',
  运动户外: 'sneakers',
}

export const categoryIconFallback = 'grid'

export const servicePromises = [
  { icon: 'shield', title: '正品保障', desc: '假一赔十' },
  { icon: 'truck', title: '极速发货', desc: '当日下单当日发' },
  { icon: 'refresh', title: '无忧退换', desc: '7 天无理由' },
  { icon: 'headset', title: '贴心客服', desc: '9:00 - 22:00 在线' },
]