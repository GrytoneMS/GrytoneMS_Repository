import { Link } from 'react-router'

export default function NotFoundPage() {
  return <section><h1>페이지를 찾을 수 없습니다</h1><Link to="/posts">모집글 목록으로 이동</Link></section>
}
