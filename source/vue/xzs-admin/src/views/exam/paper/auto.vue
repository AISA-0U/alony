<template>
  <div class="app-container">
    <el-form ref="form" :model="form" :rules="rules" label-width="150px" v-loading="loading">
      <el-form-item label="试卷名称" prop="name"><el-input v-model="form.name" /></el-form-item>
      <el-form-item label="考核职位" prop="positionId">
        <el-select v-model="form.positionId" filterable>
          <el-option v-for="item in positions" :key="item.id" :value="item.id" :label="item.name" />
        </el-select>
      </el-form-item>
      <!-- 部门选择已隐藏，自动组卷直接按科目筛选。 -->
      <el-form-item label="科目" prop="subjectId">
        <el-select v-model="form.subjectId">
          <el-option v-for="item in subjectFilter" :key="item.id" :value="item.id" :label="item.name" />
        </el-select>
      </el-form-item>
      <el-form-item label="建议时长（分钟）" prop="suggestTime">
        <el-input-number v-model="form.suggestTime" :min="1" :max="600" />
      </el-form-item>
      <el-divider content-position="left">题库数量（共100题）</el-divider>
      <el-form-item label="职位 / 安全 / 通用">
        <el-input-number v-model="form.positionCount" :min="0" :max="100" />
        <el-input-number v-model="form.safetyCount" :min="0" :max="100" />
        <el-input-number v-model="form.ethicsCount" :min="0" :max="100" />
      </el-form-item>
      <el-divider content-position="left">题型数量（共100题）</el-divider>
      <el-form-item label="选择 / 填空 / 判断 / 问答">
        <el-input-number v-model="form.choiceCount" :min="0" :max="100" />
        <el-input-number v-model="form.gapCount" :min="0" :max="100" />
        <el-input-number v-model="form.trueFalseCount" :min="0" :max="100" />
        <el-input-number v-model="form.shortAnswerCount" :min="0" :max="100" />
      </el-form-item>
      <el-alert title="每题固定1分。题库数量和题型数量均必须合计100；题量不足时不会生成残缺试卷。" type="info" show-icon :closable="false" />
      <el-form-item class="auto-actions">
        <el-button type="primary" @click="generate">随机生成试卷</el-button>
      </el-form-item>
    </el-form>
  </div>
</template>

<script>
import { mapActions, mapState } from 'vuex'
import examPaperApi from '@/api/examPaper'
import jobPositionApi from '@/api/jobPosition'

export default {
  data () {
    return {
      loading: false,
      positions: [],
      subjectFilter: [],
      form: {
        name: '',
        positionId: null,
        subjectId: null,
        paperType: 1,
        suggestTime: 60,
        totalCount: 100,
        positionCount: 80,
        safetyCount: 10,
        ethicsCount: 10,
        choiceCount: 30,
        gapCount: 20,
        trueFalseCount: 40,
        shortAnswerCount: 10
      },
      rules: {
        name: [{ required: true, message: '请输入试卷名称', trigger: 'blur' }],
        positionId: [{ required: true, message: '请选择考核职位', trigger: 'change' }],
        subjectId: [{ required: true, message: '请选择科目', trigger: 'change' }]
      }
    }
  },
  created () {
    this.initSubject(() => { this.subjectFilter = this.subjects })
    jobPositionApi.list().then(re => { this.positions = re.response || [] })
  },
  methods: {
    generate () {
      this.$refs.form.validate(valid => {
        if (!valid) return
        if (this.form.positionCount + this.form.safetyCount + this.form.ethicsCount !== 100 ||
          this.form.choiceCount + this.form.gapCount + this.form.trueFalseCount + this.form.shortAnswerCount !== 100) {
          this.$message.error('题库数量和题型数量必须分别合计100')
          return
        }
        this.loading = true
        examPaperApi.autoGenerate(this.form).then(re => {
          this.$message.success('试卷已生成，可继续检查和调整')
          this.$router.push({ path: '/exam/paper/edit', query: { id: re.response.id } })
        }).finally(() => { this.loading = false })
      })
    },
    ...mapActions('exam', { initSubject: 'initSubject' })
  },
  computed: {
    ...mapState('exam', { subjects: state => state.subjects })
  }
}
</script>

<style scoped>
.el-input-number { margin-right: 8px; }
.auto-actions { margin-top: 24px; }
</style>
