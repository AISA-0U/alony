<template>
  <div>
    <el-form-item label="题库类别" prop="bankType" required>
      <el-select v-model="form.bankType" @change="bankTypeChanged">
        <el-option :value="1" label="职位类题库" />
        <el-option :value="2" label="安全类题库" />
        <el-option :value="3" label="职业道德通用题库" />
      </el-select>
    </el-form-item>
    <el-form-item v-if="form.bankType !== 3" label="考核职位" prop="positionId" required>
      <el-select v-model="form.positionId" filterable placeholder="请选择职位">
        <el-option v-for="item in positions" :key="item.id" :value="item.id" :label="item.name" />
      </el-select>
    </el-form-item>
  </div>
</template>

<script>
import jobPositionApi from '@/api/jobPosition'

export default {
  name: 'QuestionBankFields',
  props: {
    form: { type: Object, required: true }
  },
  data () {
    return { positions: [] }
  },
  created () {
    jobPositionApi.list().then(re => { this.positions = re.response || [] })
  },
  methods: {
    bankTypeChanged (value) {
      if (value === 3) this.form.positionId = null
    }
  }
}
</script>
