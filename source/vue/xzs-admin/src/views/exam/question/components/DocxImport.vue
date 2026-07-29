<template>
  <el-dialog
    title="Word 试题导入"
    width="82%"
    top="5vh"
    :visible="visible"
    :close-on-click-modal="false"
    @close="close">
    <el-alert
      title="推荐流程：选择导入范围和 Word 文件 → 解析预览 → 核对题型、题干和答案 → 确认导入"
      type="info"
      :closable="false"
      show-icon />
    <el-form :model="form" label-width="90px" class="import-form">
      <el-row :gutter="16">
        <el-col :span="6">
          <el-form-item label="科目" required>
            <el-select v-model="form.subjectId" filterable placeholder="请选择科目" @change="clearPreview">
              <el-option v-for="item in subjects" :key="item.id" :label="item.name" :value="item.id" />
            </el-select>
          </el-form-item>
        </el-col>
        <el-col :span="6">
          <el-form-item label="题库类别" required>
            <el-select v-model="form.bankType" placeholder="请选择类别" @change="bankTypeChange">
              <el-option :value="1" label="职位类" />
              <el-option :value="2" label="安全类" />
              <el-option :value="3" label="职业道德通用类" />
            </el-select>
          </el-form-item>
        </el-col>
        <el-col v-if="form.bankType !== 3" :span="6">
          <el-form-item label="职位" required>
            <el-select v-model="form.positionId" filterable placeholder="请选择职位" @change="clearPreview">
              <el-option v-for="item in positions" :key="item.id" :label="item.name" :value="item.id" />
            </el-select>
          </el-form-item>
        </el-col>
        <el-col :span="6">
          <el-form-item label="难度" required>
            <el-select v-model="form.difficult" @change="clearPreview">
              <el-option v-for="value in 5" :key="value" :label="value" :value="value" />
            </el-select>
          </el-form-item>
        </el-col>
      </el-row>
      <el-form-item label="Word 文件" required>
        <el-upload
          ref="upload"
          action=""
          accept=".docx"
          :auto-upload="false"
          :limit="1"
          :show-file-list="false"
          :on-change="fileChange"
          :on-remove="fileRemove">
          <el-button v-if="!selectedFile" size="small" type="primary">选择 .docx 文件</el-button>
          <div slot="tip" class="el-upload__tip">
            文件不超过 5MB；支持“单选题、多选题、判断题、简答题”四个分区，多选正确项请用 ✅ 标注。
          </div>
        </el-upload>
        <div v-if="selectedFile" class="selected-document">
          <div class="selected-document__info">
            <i class="el-icon-document selected-document__icon" />
            <div>
              <div class="selected-document__name">{{ selectedFile.name }}</div>
              <div class="selected-document__size">{{ fileSizeText(selectedFile.size) }}</div>
            </div>
          </div>
          <div class="selected-document__actions">
            <el-button size="mini" icon="el-icon-view" :disabled="parsing || importing" @click="viewDocument">
              查看文档
            </el-button>
            <el-button size="mini" type="danger" icon="el-icon-delete" :disabled="parsing || importing" @click="removeDocument">
              删除文档
            </el-button>
          </div>
        </div>
      </el-form-item>
    </el-form>

    <div class="preview-actions">
      <el-button type="primary" :loading="parsing" @click="previewDocument">解析预览</el-button>
      <span v-if="preview" class="preview-summary">
        共 {{ preview.totalCount }} 题：单选 {{ preview.singleChoiceCount }}、多选 {{ preview.multipleChoiceCount }}、
        判断 {{ preview.trueFalseCount }}、简答 {{ preview.shortAnswerCount }}
      </span>
    </div>

    <template v-if="preview">
      <el-alert
        v-for="warning in preview.warnings"
        :key="warning"
        :title="warning"
        type="warning"
        :closable="false"
        show-icon
        class="preview-warning" />
      <el-table :data="preview.questions" border max-height="430" class="preview-table">
        <el-table-column type="index" :index="questionTypeIndex" label="题型序号" width="80" />
        <el-table-column label="题型" width="90">
          <template slot-scope="scope">{{ questionTypeName(scope.row.questionType) }}</template>
        </el-table-column>
        <el-table-column prop="title" label="题干" min-width="360" show-overflow-tooltip />
        <el-table-column label="答案" min-width="140">
          <template slot-scope="scope">{{ answerText(scope.row) }}</template>
        </el-table-column>
      </el-table>
    </template>

    <el-dialog
      append-to-body
      width="70%"
      top="6vh"
      :title="documentPreview.name || '查看 Word 文档'"
      :visible.sync="documentPreview.visible">
      <div v-loading="documentPreview.loading" class="document-preview">
        <pre v-if="documentPreview.content">{{ documentPreview.content }}</pre>
        <el-empty v-else-if="!documentPreview.loading" description="文档中没有可预览的文字内容" />
      </div>
    </el-dialog>

    <span slot="footer">
      <el-button @click="close">取消</el-button>
      <el-button type="success" :disabled="!preview || preview.totalCount === 0" :loading="importing" @click="confirmImport">
        确认导入
      </el-button>
    </span>
  </el-dialog>
</template>

<script>
import questionApi from '@/api/question'
import JSZip from 'jszip'

export default {
  props: {
    visible: {
      type: Boolean,
      default: false
    },
    subjects: {
      type: Array,
      default: () => []
    },
    positions: {
      type: Array,
      default: () => []
    }
  },
  data () {
    return {
      parsing: false,
      importing: false,
      selectedFile: null,
      documentPreview: {
        visible: false,
        loading: false,
        name: '',
        content: ''
      },
      preview: null,
      form: this.defaultForm()
    }
  },
  watch: {
    visible (value) {
      if (value) this.reset()
    }
  },
  methods: {
    defaultForm () {
      return {
        subjectId: null,
        bankType: 1,
        positionId: null,
        difficult: 2
      }
    },
    reset () {
      this.form = this.defaultForm()
      this.selectedFile = null
      this.documentPreview = {
        visible: false,
        loading: false,
        name: '',
        content: ''
      }
      this.preview = null
      this.parsing = false
      this.importing = false
      this.$nextTick(() => {
        if (this.$refs.upload) this.$refs.upload.clearFiles()
      })
    },
    close () {
      if (this.parsing || this.importing) return
      this.$emit('update:visible', false)
    },
    clearPreview () {
      this.preview = null
    },
    bankTypeChange (value) {
      if (value === 3) this.form.positionId = null
      this.clearPreview()
    },
    fileChange (file) {
      const extensionValid = file.name.toLowerCase().endsWith('.docx')
      const sizeValid = file.size <= 5 * 1024 * 1024
      if (!extensionValid || !sizeValid) {
        this.$message.error(extensionValid ? 'Word 文件不能超过 5MB' : '仅支持 .docx 格式的 Word 文件')
        this.selectedFile = null
        this.$refs.upload.clearFiles()
        return
      }
      this.selectedFile = file.raw
      this.clearPreview()
    },
    fileRemove () {
      this.selectedFile = null
      this.clearPreview()
    },
    removeDocument () {
      if (this.parsing || this.importing) return
      this.selectedFile = null
      this.clearPreview()
      if (this.$refs.upload) this.$refs.upload.clearFiles()
    },
    async viewDocument () {
      if (!this.selectedFile) {
        this.$message.warning('请先选择 Word 文件')
        return
      }
      this.documentPreview = {
        visible: true,
        loading: true,
        name: this.selectedFile.name,
        content: ''
      }
      try {
        const archive = await JSZip.loadAsync(this.selectedFile)
        const documentXml = archive.file('word/document.xml')
        if (!documentXml) throw new Error('Word 文档缺少正文内容')
        const xml = await documentXml.async('string')
        const xmlDocument = new DOMParser().parseFromString(xml, 'application/xml')
        const paragraphs = Array.from(xmlDocument.getElementsByTagNameNS('*', 'p'))
          .map(paragraph => Array.from(paragraph.getElementsByTagNameNS('*', 't'))
            .map(text => text.textContent).join(''))
          .filter(text => text.trim())
        this.documentPreview.content = paragraphs.join('\n')
      } catch (error) {
        this.documentPreview.visible = false
        this.$message.error(error.message || '无法预览该 Word 文档')
      } finally {
        this.documentPreview.loading = false
      }
    },
    fileSizeText (size) {
      if (size < 1024) return `${size} B`
      if (size < 1024 * 1024) return `${(size / 1024).toFixed(1)} KB`
      return `${(size / 1024 / 1024).toFixed(2)} MB`
    },
    validateInput () {
      if (!this.form.subjectId) return '请选择科目'
      if (!this.form.bankType) return '请选择题库类别'
      if (this.form.bankType !== 3 && !this.form.positionId) return '请选择职位'
      if (!this.form.difficult) return '请选择难度'
      if (!this.selectedFile) return '请选择 Word 文件'
      return null
    },
    previewDocument () {
      const error = this.validateInput()
      if (error) {
        this.$message.warning(error)
        return
      }
      const payload = new FormData()
      payload.append('file', this.selectedFile)
      payload.append('subjectId', this.form.subjectId)
      payload.append('bankType', this.form.bankType)
      if (this.form.positionId) payload.append('positionId', this.form.positionId)
      payload.append('difficult', this.form.difficult)

      this.parsing = true
      questionApi.previewDocx(payload).then(response => {
        if (response.code !== 1) {
          this.$message.error(response.message)
          return
        }
        this.preview = response.response
        this.$message.success('解析完成，请核对预览内容')
      }).finally(() => {
        this.parsing = false
      })
    },
    confirmImport () {
      if (!this.preview) return
      const requestedCount = this.preview.questions.length
      this.$confirm(
        `确认将预览中的 ${this.preview.totalCount} 道题导入题库吗？导入后可逐题编辑。`,
        '确认导入',
        { type: 'warning', confirmButtonText: '确认导入', cancelButtonText: '返回核对' }
      ).then(() => {
        this.importing = true
        return questionApi.importBank({
          batchNo: this.preview.batchNo,
          questions: this.preview.questions
        }).then(response => {
          if (response.code !== 1) {
            this.$message.error(response.message)
            return
          }
          const skippedCount = requestedCount - response.response
          const skippedMessage = skippedCount > 0 ? `，跳过 ${skippedCount} 道重复题` : ''
          this.$message.success(`成功导入 ${response.response} 道题${skippedMessage}`)
          this.$emit('imported')
          this.$emit('update:visible', false)
        }).finally(() => {
          this.importing = false
        })
      }).catch(() => {})
    },
    questionTypeName (type) {
      return { 1: '单选题', 2: '多选题', 3: '判断题', 5: '简答题' }[type] || '未知'
    },
    questionTypeIndex (index) {
      const questions = this.preview && this.preview.questions ? this.preview.questions : []
      const currentQuestion = questions[index]
      if (!currentQuestion) return index + 1
      return questions.slice(0, index + 1)
        .filter(question => question.questionType === currentQuestion.questionType).length
    },
    answerText (question) {
      return question.correctArray && question.correctArray.length > 0
        ? question.correctArray.join('、')
        : question.correct
    }
  }
}
</script>

<style scoped>
.import-form {
  margin-top: 18px;
}
.import-form .el-select {
  width: 100%;
}
.preview-actions {
  display: flex;
  align-items: center;
  margin: 8px 0 14px;
}
.selected-document {
  display: flex;
  align-items: center;
  justify-content: space-between;
  max-width: 680px;
  padding: 10px 14px;
  border: 1px solid #dcdfe6;
  border-radius: 4px;
  background: #f8f9fb;
}
.selected-document__info {
  display: flex;
  align-items: center;
  min-width: 0;
}
.selected-document__icon {
  margin-right: 10px;
  color: #409eff;
  font-size: 26px;
}
.selected-document__name {
  overflow: hidden;
  max-width: 390px;
  color: #303133;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.selected-document__size {
  color: #909399;
  font-size: 12px;
  line-height: 18px;
}
.selected-document__actions {
  flex-shrink: 0;
  margin-left: 16px;
}
.preview-summary {
  margin-left: 18px;
  color: #303133;
}
.preview-warning {
  margin-bottom: 10px;
}
.preview-table {
  width: 100%;
}
.document-preview {
  min-height: 220px;
  max-height: 70vh;
  overflow: auto;
}
.document-preview pre {
  margin: 0;
  color: #303133;
  font-family: inherit;
  line-height: 1.8;
  white-space: pre-wrap;
  word-break: break-word;
}
</style>
