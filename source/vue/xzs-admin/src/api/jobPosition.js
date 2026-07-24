import { post } from '@/utils/request'

export default {
  list: () => post('/api/admin/job-position/list'),
  save: model => post('/api/admin/job-position/save', model)
}
