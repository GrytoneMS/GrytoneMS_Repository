import { Navigate, Route, Routes } from 'react-router'
import { AppLayout } from './layouts/AppLayout'
import LoginPage from './pages/LoginPage'
import SignupPage from './pages/SignupPage'
import SchoolVerificationPage from './pages/SchoolVerificationPage'
import ProfileSetupPage from './pages/ProfileSetupPage'
import PostListPage from './pages/PostListPage'
import PostDetailPage from './pages/PostDetailPage'
import PostCreatePage from './pages/PostCreatePage'
import PostEditPage from './pages/PostEditPage'
import PostApplicantsPage from './pages/PostApplicantsPage'
import MyProfilePage from './pages/MyProfilePage'
import ProfileEditPage from './pages/ProfileEditPage'
import UserProfilePage from './pages/UserProfilePage'
import MyPostsPage from './pages/MyPostsPage'
import MyApplicationsPage from './pages/MyApplicationsPage'
import MyScrapsPage from './pages/MyScrapsPage'
import MyBlocksPage from './pages/MyBlocksPage'
import { ScrapProvider } from './features/scraps/ScrapContext'
import NotFoundPage from './pages/NotFoundPage'
import { AuthProvider } from './features/auth/AuthContext'
import { useAuth } from './features/auth/useAuth'
import AdminPage from './features/admin/AdminPage'

export default function App() {
  return <AuthProvider><SessionRoutes /></AuthProvider>
}

function SessionRoutes() {
  const auth = useAuth()
  return (
    <ScrapProvider session={auth.session} authScope={auth.scope} store={auth.store}><Routes>
      <Route path="/admin" element={<AdminPage />} />
      <Route path="/admin/:section" element={<AdminPage />} />
      <Route element={<AppLayout />}>
        <Route index element={<Navigate to="/posts" replace />} />
        <Route path="/login" element={<LoginPage />} />
        <Route path="/signup" element={<SignupPage />} />
        <Route path="/school-verification" element={<SchoolVerificationPage />} />
        <Route path="/profile/setup" element={<ProfileSetupPage />} />
        <Route path="/posts" element={<PostListPage />} />
        <Route path="/posts/:postId" element={<PostDetailPage />} />
        <Route path="/posts/new" element={<PostCreatePage />} />
        <Route path="/posts/:postId/edit" element={<PostEditPage />} />
        <Route path="/posts/:postId/applicants" element={<PostApplicantsPage />} />
        <Route path="/me/profile" element={<MyProfilePage />} />
        <Route path="/me/profile/edit" element={<ProfileEditPage />} />
        <Route path="/users/:userId" element={<UserProfilePage />} />
        <Route path="/me/posts" element={<MyPostsPage />} />
        <Route path="/me/applications" element={<MyApplicationsPage />} />
        <Route path="/me/scraps" element={<MyScrapsPage />} />
        <Route path="/me/blocks" element={<MyBlocksPage />} />
        <Route path="*" element={<NotFoundPage />} />
      </Route>
    </Routes></ScrapProvider>
  )
}
