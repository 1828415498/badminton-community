import request from './request'

/**
 * 上传一张图片。
 *
 * <h2>为什么用 FormData 而不是 JSON</h2>
 * 文件是二进制流，塞不进 JSON 字符串，必须走 multipart/form-data 表单编码。
 *
 * <h2>为什么这里不写 Content-Type</h2>
 * multipart 的 Content-Type 必须带一个随机 boundary（分隔各字段的标记），
 * 这个 boundary 是浏览器生成并写在 body 里的。如果这里手写
 * 'multipart/form-data'，axios 会把 boundary 丢掉，后端直接解析失败。
 * 交给 axios 自动判断即可 —— 它检测到 FormData 就会自己设定。
 *
 * @param {File} file 用户选择的文件对象
 * @returns {Promise<{url: string, name: string, size: number}>}
 *          url 是相对路径（/api/files/…），直接塞进 <img src> 就能显示
 */
export const uploadImage = (file) => {
  const fd = new FormData()
  // 字段名必须与后端 @RequestParam(name = "file") 对齐
  fd.append('file', file)
  return request.post('/upload', fd)
}
