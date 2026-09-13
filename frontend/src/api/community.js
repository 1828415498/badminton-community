import request from './request'

// 社区：帖子 / 评论 / 点赞（api.md §7）
export const listPosts = (params) => request.get('/posts', { params })
export const createPost = (data) => request.post('/posts', data)
export const getPost = (id) => request.get(`/posts/${id}`)
export const deletePost = (id) => request.delete(`/posts/${id}`)

export const createComment = (postId, data) => request.post(`/posts/${postId}/comments`, data)
export const listComments = (postId, params) => request.get(`/posts/${postId}/comments`, { params })

export const likePost = (postId) => request.post(`/posts/${postId}/like`)
export const unlikePost = (postId) => request.delete(`/posts/${postId}/like`)
