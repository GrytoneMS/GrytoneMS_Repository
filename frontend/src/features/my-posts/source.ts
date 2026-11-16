import { getMyPosts, deleteMyPost } from './api'
import { closePost } from '../posts/post-actions-api'
import { isPostMock } from '../posts/source'
import type { PostRequestOptions } from '../posts/api'
import type { MyService } from './controller'

export function myPostsService(query: URLSearchParams, options: PostRequestOptions = {}): MyService {
  if (!isPostMock) return {
    load: (status, cursor, signal) => getMyPosts(status, cursor, { ...options, signal }),
    close: (id, signal) => closePost(id, { ...options, signal }),
    remove: (id, signal) => deleteMyPost(id, { ...options, signal }),
  }
  const service = import('./mock').then(module => module.mockMyPosts(query))
  return { load: async (...args) => (await service).load(...args), close: async (...args) => (await service).close(...args), remove: async (...args) => (await service).remove(...args) }
}
