// ESLint 扁平配置（ESLint 9+/10），对应 Sprint 2 前端代码规范任务
// TypeScript 项目由 tsc 负责未定义变量检查，因此不启用 no-undef，也不额外引入 globals
import js from '@eslint/js';
import tseslint from 'typescript-eslint';
import reactHooks from 'eslint-plugin-react-hooks';
import reactRefresh from 'eslint-plugin-react-refresh';
import prettier from 'eslint-config-prettier';

export default tseslint.config(
  {
    ignores: ['dist', 'node_modules', 'coverage'],
  },
  {
    extends: [js.configs.recommended, ...tseslint.configs.recommended],
    files: ['**/*.{ts,tsx}'],
    plugins: {
      'react-hooks': reactHooks,
      'react-refresh': reactRefresh,
    },
    rules: {
      ...reactHooks.configs.recommended.rules,
      // 页面组件文件只导出组件，避免 Fast Refresh 失效；常量导出放行
      'react-refresh/only-export-components': ['warn', { allowConstantExport: true }],
    },
  },
  // 放在最后：关闭所有与 Prettier 冲突的格式化规则
  prettier,
);
