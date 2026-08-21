import { createBrowserRouter, Navigate } from 'react-router-dom'
import Guard from './auth/Guard'
import MobileLayout from './layout/MobileLayout'
import { PermissionProvider } from './permissions'
import LoginPage, { CallbackPage } from './pages/LoginPage'
import TodosPage from './pages/TodosPage'
import AttendancePage from './pages/AttendancePage'
import DirectoryPage from './pages/DirectoryPage'
import AnnouncementsPage from './pages/AnnouncementsPage'

const shell = <Guard><PermissionProvider><MobileLayout /></PermissionProvider></Guard>
export const router = createBrowserRouter([
  { path: '/login', element: <LoginPage /> },
  { path: '/callback', element: <CallbackPage /> },
  { path: '/', element: shell, children: [
    { index: true, element: <Navigate to="/workbench" replace /> },
    { path: 'workbench', element: <TodosPage /> },
    { path: 'attendance', element: <AttendancePage /> },
    { path: 'directory', element: <DirectoryPage /> },
    { path: 'announcements', element: <AnnouncementsPage /> },
  ] },
  { path: '*', element: <Navigate to="/workbench" replace /> },
])
