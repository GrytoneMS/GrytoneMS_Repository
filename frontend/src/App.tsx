import { Navigate, Route, Routes } from 'react-router'
import { AppLayout } from './layouts/AppLayout'
import LoginPage from './pages/LoginPage'
import SignupPage from './pages/SignupPage'
import SchoolVerificationPage from './pages/SchoolVerificationPage'
import ProfileSetupPage from './pages/ProfileSetupPage'
import PostListPage from './pages/PostListPage'
import PostDetailPage from './pages/PostDetailPage'
import PostCreatePage from './pages/PostCreatePage'
import PostApplicantsPage from './pages/PostApplicantsPage'
import MyProfilePage from './pages/MyProfilePage'
import ProfileEditPage from './pages/ProfileEditPage'
import UserProfilePage from './pages/UserProfilePage'
import MyPostsPage from './pages/MyPostsPage'
import MyApplicationsPage from './pages/MyApplicationsPage'
import NotFoundPage from './pages/NotFoundPage'

export default function App() {
  return (
    <Routes>
      <Route element={<AppLayout />}>
        <Route index element={<Navigate to="/posts" replace />} />
        <Route path="/login" element={<LoginPage />} />
        <Route path="/signup" element={<SignupPage />} />
        <Route path="/school-verification" element={<SchoolVerificationPage />} />
        <Route path="/profile/setup" element={<ProfileSetupPage />} />
        <Route path="/posts" element={<PostListPage />} />
        <Route path="/posts/:postId" element={<PostDetailPage />} />
        <Route path="/posts/new" element={<PostCreatePage />} />
        <Route path="/posts/:postId/applicants" element={<PostApplicantsPage />} />
        <Route path="/me/profile" element={<MyProfilePage />} />
        <Route path="/me/profile/edit" element={<ProfileEditPage />} />
        <Route path="/users/:userId" element={<UserProfilePage />} />
        <Route path="/me/posts" element={<MyPostsPage />} />
        <Route path="/me/applications" element={<MyApplicationsPage />} />
        <Route path="*" element={<NotFoundPage />} />
      </Route>
    </Routes>
  )
}
