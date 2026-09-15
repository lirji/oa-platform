-- V13 给菜单补 route 时，工作台/考勤/公文/知识库/行政/驾驶舱还没插入。
-- 那些行是 V21/V31/V61/V81 后加的，route 一直是 NULL。
-- 前端菜单 key 回退成权限码（oa:menu:xxx），点进去就是 404。
-- 幂等回填，与 V13 同一套路径。

UPDATE oa_iam.permission SET route = '/workbench',   icon = 'DashboardOutlined',   sort_order = 10  WHERE code = 'oa:menu:workbench';
UPDATE oa_iam.permission SET route = '/org',         icon = 'ApartmentOutlined',   sort_order = 20  WHERE code = 'oa:menu:org';
UPDATE oa_iam.permission SET route = '/attendance',  icon = 'ClockCircleOutlined', sort_order = 30  WHERE code = 'oa:menu:attendance';
UPDATE oa_iam.permission SET route = '/doc',         icon = 'FileTextOutlined',    sort_order = 40  WHERE code = 'oa:menu:doc';
UPDATE oa_iam.permission SET route = '/kb',          icon = 'BookOutlined',        sort_order = 50  WHERE code = 'oa:menu:kb';
UPDATE oa_iam.permission SET route = '/admin-biz',   icon = 'ShopOutlined',        sort_order = 60  WHERE code = 'oa:menu:admin';
UPDATE oa_iam.permission SET route = '/notify',      icon = 'BellOutlined',        sort_order = 70  WHERE code = 'oa:menu:notify';
UPDATE oa_iam.permission SET route = '/report',      icon = 'BarChartOutlined',    sort_order = 80  WHERE code = 'oa:menu:report';
UPDATE oa_iam.permission SET route = '/iam',         icon = 'SafetyOutlined',      sort_order = 90  WHERE code = 'oa:menu:iam';
