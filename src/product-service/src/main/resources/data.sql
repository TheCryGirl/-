-- 商品分类初始数据
INSERT IGNORE INTO category (category_id, parent_id, name, sort_no) VALUES
('C01', NULL, '教材图书', 1),
('C02', NULL, '数码电子', 2),
('C03', NULL, '生活用品', 3),
('C04', NULL, '服饰鞋包', 4),
('C05', NULL, '运动户外', 5),
('C0101', 'C01', '专业教材', 1),
('C0102', 'C01', '考研资料', 2),
('C0201', 'C02', '手机平板', 1),
('C0202', 'C02', '电脑配件', 2);
