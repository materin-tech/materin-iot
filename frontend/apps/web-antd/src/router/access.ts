import type {
  ComponentRecordType,
  GenerateMenuAndRoutesOptions,
  RouteRecordStringComponent,
} from '@vben/types';

import { generateAccessible } from '@vben/access';
import { preferences } from '@vben/preferences';

import { message } from 'ant-design-vue';

import { getAllMenusApi } from '#/api';
import { BasicLayout, IFrameView } from '#/layouts';
import { $t } from '#/locales';

const forbiddenComponent = () => import('#/views/_core/fallback/forbidden.vue');

/**
 * 后端菜单标题 i18n：meta.title 以 "page." 开头视为 i18n key，按当前语言翻译；
 * 非 key 的标题（历史中文字符串）原样保留。
 */
function translateMenuTitles(
  routes: RouteRecordStringComponent[],
): RouteRecordStringComponent[] {
  const walk = (nodes: RouteRecordStringComponent[]) => {
    for (const node of nodes) {
      const title = node.meta?.title;
      if (typeof title === 'string' && title.startsWith('page.')) {
        node.meta!.title = $t(title);
      }
      if (node.children?.length) {
        walk(node.children as RouteRecordStringComponent[]);
      }
    }
  };
  walk(routes);
  return routes;
}

async function generateAccess(options: GenerateMenuAndRoutesOptions) {
  const pageMap: ComponentRecordType = import.meta.glob('../views/**/*.vue');

  const layoutMap: ComponentRecordType = {
    BasicLayout,
    IFrameView,
  };

  return await generateAccessible(preferences.app.accessMode, {
    ...options,
    fetchMenuListAsync: async () => {
      message.loading({
        content: `${$t('common.loadingMenu')}...`,
        duration: 1.5,
      });
      return translateMenuTitles(await getAllMenusApi());
    },
    // 可以指定没有权限跳转403页面
    forbiddenComponent,
    // 如果 route.meta.menuVisibleWithForbidden = true
    layoutMap,
    pageMap,
  });
}

export { generateAccess };
