/**
 * 路由配置
 * 覆盖原型的主要功能入口。所有页面挂在 AppLayout 外壳下。
 *
 * 路由结构：
 *   /login                 登录页（独立布局，无需登录）
 *   /register              注册页（独立布局，无需登录）
 *   /                      工作台 Dashboard
 *   /projects              项目管理列表
 *   /projects/:projectId   项目详情（原型 current-project）
 *   /records               当前项目实验记录目录
 *   /records/new           新建实验记录（选择项目与结构）
 *   /records/new/edit      新建实验记录（编辑器）
 *   /records/:recordId     实验详情（只读）
 *   /records/:recordId/edit 编辑实验记录
 *   /templates             模板中心
 *   /search                搜索中心
 *   /profile               个人中心
 *   *                      404
 */
import { createBrowserRouter } from 'react-router-dom'
import ProtectedRoute from '@/components/auth/ProtectedRoute'
import AppLayout from '@/components/layout/AppLayout'
import {
  DashboardPage,
  ProjectsPage,
  ProjectDetailPage,
  RecordsPage,
  RecordCreatePage,
  RecordEditorPage,
  RecordDetailPage,
  TemplatesPage,
  SearchPage,
  ProfilePage,
  NotFoundPage,
  LoginPage,
  RegisterPage,
} from '@/pages'

export const router = createBrowserRouter([
  {
    path: '/login',
    element: <LoginPage />,
  },
  {
    path: '/register',
    element: <RegisterPage />,
  },
  {
    path: '/',
    element: (
      <ProtectedRoute>
        <AppLayout />
      </ProtectedRoute>
    ),
    children: [
      { index: true, element: <DashboardPage /> },
      { path: 'projects', element: <ProjectsPage /> },
      { path: 'projects/:projectId', element: <ProjectDetailPage /> },
      { path: 'records', element: <RecordsPage /> },
      { path: 'records/new', element: <RecordCreatePage /> },
      { path: 'records/new/edit', element: <RecordEditorPage /> },
      { path: 'records/:recordId', element: <RecordDetailPage /> },
      { path: 'records/:recordId/edit', element: <RecordEditorPage /> },
      { path: 'templates', element: <TemplatesPage /> },
      { path: 'search', element: <SearchPage /> },
      { path: 'profile', element: <ProfilePage /> },
      { path: '*', element: <NotFoundPage /> },
    ],
  },
])
