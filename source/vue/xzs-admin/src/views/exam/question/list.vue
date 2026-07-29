<template>
  <div class="app-container">
    <el-form :model="queryParam" ref="queryForm" :inline="true">
      <el-form-item label="题目ID：">
        <el-input v-model="queryParam.id" clearable></el-input>
      </el-form-item>
      <el-form-item label="题目内容：">
        <el-input v-model="queryParam.content" clearable></el-input>
      </el-form-item>

      <!-- 部门筛选已隐藏，科目下拉框直接显示全部科目。 -->
      <el-form-item label="科目：">
        <el-select v-model="queryParam.subjectId" clearable>
          <el-option v-for="item in subjectFilter" :key="item.id" :value="item.id"
                     :label="item.name"></el-option>
        </el-select>
      </el-form-item>
      <el-form-item label="题型：">
        <el-select v-model="queryParam.questionType" clearable>
          <el-option v-for="item in questionType" :key="item.key" :value="item.key" :label="item.value"></el-option>
        </el-select>
      </el-form-item>
      <el-form-item label="题库类别">
        <el-select v-model="queryParam.bankType" clearable @change="bankTypeChange">
          <el-option :value="1" label="职位类" />
          <el-option :value="2" label="安全类" />
          <el-option :value="3" label="职业道德通用类" />
        </el-select>
      </el-form-item>
      <el-form-item v-if="queryParam.bankType !== 3" label="职位">
        <el-select v-model="queryParam.positionId" clearable filterable>
          <el-option v-for="item in positions" :key="item.id" :value="item.id" :label="item.name" />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button type="primary" @click="submitForm">查询</el-button>
        <el-popover placement="bottom" trigger="click">
          <el-button type="warning" size="mini" v-for="item in editUrlEnum" :key="item.key"
                     @click="$router.push({path:item.value})">{{item.name}}
          </el-button>
          <el-button slot="reference" type="primary" class="link-left">添加</el-button>
        </el-popover>
        <el-button type="success" class="link-left" @click="importDialogVisible = true">Word 导入</el-button>
        <el-button
          type="danger"
          class="link-left"
          :disabled="selectedQuestionIds.length === 0"
          @click="batchDeleteQuestions">
          批量删除（{{ selectedQuestionIds.length }}）
        </el-button>
      </el-form-item>
    </el-form>
    <el-table
      ref="questionTable"
      v-loading="listLoading"
      :data="tableData"
      border
      fit
      highlight-current-row
      style="width: 100%"
      @selection-change="selectionChange">
      <el-table-column type="selection" width="45" />
      <el-table-column prop="id" label="Id" width="90px"/>
      <el-table-column prop="subjectId" label="科目" :formatter="subjectFormatter" width="120px"/>
      <el-table-column prop="questionType" label="题型" :formatter="questionTypeFormatter" width="70px"/>
      <el-table-column prop="shortTitle" label="题干" show-overflow-tooltip/>
      <el-table-column prop="score" label="分数" width="60px"/>
      <el-table-column prop="difficult" label="难度" width="60px"/>
      <el-table-column prop="createTime" label="创建时间" width="160px"/>
      <el-table-column label="操作" align="center" width="220px">
        <template slot-scope="{row}">
          <el-button size="mini"   @click="showQuestion(row)">预览</el-button>
          <el-button size="mini"  @click="editQuestion(row)">编辑</el-button>
          <el-button size="mini"  type="danger" @click="deleteQuestion(row)" class="link-left">删除</el-button>
        </template>
      </el-table-column>
    </el-table>
    <pagination v-show="total>0" :total="total" :page.sync="queryParam.pageIndex" :limit.sync="queryParam.pageSize"
                @pagination="search"/>
    <el-dialog :visible.sync="questionShow.dialog" style="width: 100%;height: 100%">
      <QuestionShow :qType="questionShow.qType" :question="questionShow.question" :qLoading="questionShow.loading"/>
    </el-dialog>
    <DocxImport
      :visible.sync="importDialogVisible"
      :subjects="subjectFilter || []"
      :positions="positions"
      @imported="importCompleted" />
  </div>
</template>

<script>
import { mapGetters, mapState, mapActions } from 'vuex'
import Pagination from '@/components/Pagination'
import QuestionShow from './components/Show'
import DocxImport from './components/DocxImport'
import questionApi from '@/api/question'
import jobPositionApi from '@/api/jobPosition'

export default {
  components: { Pagination, QuestionShow, DocxImport },
  data () {
    return {
      queryParam: {
        id: null,
        questionType: null,
        level: null,
        subjectId: null,
        bankType: null,
        positionId: null,
        pageIndex: 1,
        pageSize: 10
      },
      subjectFilter: null,
      positions: [],
      importDialogVisible: false,
      selectedQuestionIds: [],
      listLoading: true,
      tableData: [],
      total: 0,
      questionShow: {
        qType: 0,
        dialog: false,
        question: null,
        loading: false
      }
    }
  },
  created () {
    this.initSubject(() => { this.subjectFilter = this.subjects })
    jobPositionApi.list().then(re => { this.positions = re.response || [] })
    this.search()
  },
  methods: {
    submitForm () {
      this.queryParam.pageIndex = 1
      this.search()
    },
    search () {
      this.listLoading = true
      questionApi.pageList(this.queryParam).then(data => {
        const re = data.response
        this.tableData = re.list
        this.selectedQuestionIds = []
        this.total = re.total
        this.queryParam.pageIndex = re.pageNum
        this.listLoading = false
      })
    },
    bankTypeChange (value) {
      if (value === 3) this.queryParam.positionId = null
    },
    importCompleted () {
      this.queryParam.pageIndex = 1
      this.search()
    },
    bankTypeFormatter (row) {
      return { 1: '职位类', 2: '安全类', 3: '职业道德' }[row.bankType] || '未分类'
    },
    addQuestion () {
      this.$router.push('/exam/question/edit/singleChoice')
    },
    showQuestion (row) {
      let _this = this
      this.questionShow.dialog = true
      this.questionShow.loading = true
      questionApi.select(row.id).then(re => {
        _this.questionShow.qType = re.response.questionType
        _this.questionShow.question = re.response
        _this.questionShow.loading = false
      })
    },
    editQuestion (row) {
      let url = this.enumFormat(this.editUrlEnum, row.questionType)
      this.$router.push({ path: url, query: { id: row.id } })
    },
    deleteQuestion (row) {
      let _this = this
      questionApi.deleteQuestion(row.id).then(re => {
        if (re.code === 1) {
          _this.search()
          _this.$message.success(re.message)
        } else {
          _this.$message.error(re.message)
        }
      })
    },
    selectionChange (questions) {
      this.selectedQuestionIds = questions.map(question => question.id)
    },
    batchDeleteQuestions () {
      const count = this.selectedQuestionIds.length
      if (count === 0) return
      this.$confirm(
        `确认删除选中的 ${count} 道题吗？`,
        '批量删除',
        { type: 'warning', confirmButtonText: '确认删除', cancelButtonText: '取消' }
      ).then(() => questionApi.batchDelete(this.selectedQuestionIds)).then(response => {
        if (response.code !== 1) {
          this.$message.error(response.message)
          return
        }
        this.$message.success(`成功删除 ${response.response} 道题`)
        if (response.response === this.tableData.length && this.queryParam.pageIndex > 1) {
          this.queryParam.pageIndex--
        }
        this.search()
      }).catch(() => {})
    },
    questionTypeFormatter (row, column, cellValue, index) {
      return this.enumFormat(this.questionType, cellValue)
    },
    subjectFormatter (row, column, cellValue, index) {
      return this.subjectEnumFormat(cellValue)
    },
    ...mapActions('exam', { initSubject: 'initSubject' })
  },
  computed: {
    ...mapGetters('enumItem', ['enumFormat']),
    ...mapState('enumItem', {
      questionType: state => state.exam.question.typeEnum,
      editUrlEnum: state => state.exam.question.editUrlEnum,
      levelEnum: state => state.user.levelEnum
    }),
    ...mapGetters('exam', ['subjectEnumFormat']),
    ...mapState('exam', { subjects: state => state.subjects })
  }
}
</script>
