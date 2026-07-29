import { post, form } from '@/utils/request'

export default {
  pageList: query => post('/api/admin/question/page', query),
  edit: query => post('/api/admin/question/edit', query),
  importBank: query => post('/api/admin/question/bank/import', query),
  previewDocx: query => form('/api/admin/question/bank/docx/preview', query),
  select: id => post('/api/admin/question/select/' + id),
  deleteQuestion: id => post('/api/admin/question/delete/' + id),
  batchDelete: ids => post('/api/admin/question/batch/delete', { ids })
}
