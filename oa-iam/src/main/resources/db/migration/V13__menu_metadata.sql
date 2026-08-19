-- ═══════════════════════════════════════════════════════════════════
-- 给 9 个 MENU 权限点补 route / icon / sort_order。
--
-- 表里这几列一直是 NULL —— 于是 /me/permissions 返回的 menus 是一棵
-- 【没有路由、没有图标、全都 sort_order=0】的扁平列表，前端只能拿到
-- "哪些菜单可见"这一位信息，路由和层级得自己再维护一份静态表。
-- 两份菜单定义迟早会漂：后端加了权限点、前端忘了加路由，那个菜单就永远点不动。
--
-- 菜单元数据本来就属于权限点目录（表里早就留好了这四列），填上即可。
-- 前端只需消费，不必再维护第二份。
-- ═══════════════════════════════════════════════════════════════════

UPDATE oa_iam.permission SET route = '/workbench',   icon = 'DashboardOutlined',   sort_order = 10  WHERE code = 'oa:menu:workbench';
UPDATE oa_iam.permission SET route = '/org',         icon = 'ApartmentOutlined',   sort_order = 20  WHERE code = 'oa:menu:org';
UPDATE oa_iam.permission SET route = '/attendance',  icon = 'ClockCircleOutlined', sort_order = 30  WHERE code = 'oa:menu:attendance';
UPDATE oa_iam.permission SET route = '/doc',         icon = 'FileTextOutlined',    sort_order = 40  WHERE code = 'oa:menu:doc';
UPDATE oa_iam.permission SET route = '/kb',          icon = 'BookOutlined',        sort_order = 50  WHERE code = 'oa:menu:kb';
UPDATE oa_iam.permission SET route = '/admin-biz',   icon = 'ShopOutlined',        sort_order = 60  WHERE code = 'oa:menu:admin';
UPDATE oa_iam.permission SET route = '/notify',      icon = 'BellOutlined',        sort_order = 70  WHERE code = 'oa:menu:notify';
UPDATE oa_iam.permission SET route = '/report',      icon = 'BarChartOutlined',    sort_order = 80  WHERE code = 'oa:menu:report';
UPDATE oa_iam.permission SET route = '/iam',         icon = 'SafetyOutlined',      sort_order = 90  WHERE code = 'oa:menu:iam';

-- 三条"有权限点、没有任何 handler 消费"的孤儿 code。
-- 留着会让前端为不存在的功能渲染按钮，点了 404 —— 比没有这个按钮更糟。
-- 不删除（删了要动 role_permission 外键，且将来可能真做），改成 DISABLED 让目录能标出来。
UPDATE oa_iam.permission SET status = 'DISABLED', remark = '目录中已定义但尚无接口实现；前端不应据此渲染入口'
 WHERE code IN ('oa:employee:export', 'oa:room:manage', 'oa:asset:manage');
