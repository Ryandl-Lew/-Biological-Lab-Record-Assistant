## 一、Benchling 的基本使用流程

### 1. 注册与登录

Benchling 是云端平台，需要联网使用。官方帮助文档建议使用 **Google Chrome**，也支持 Firefox 和 Safari。

一般流程是：

1. 进入 Benchling 官网；
2. 注册账号；
3. 推荐使用学校邮箱或机构邮箱；
4. 登录后选择使用方向，例如 Notebook 或 Molecular Biology；
5. 如果你属于某个课题组或课程组织，可以加入对应 Organization。

官方入门文档：
https://help.benchling.com/hc/en-us/articles/9684234496013-The-Basics-of-Benchling

---

## 二、Benchling 中几个核心概念

理解 Benchling，最好先理解它的数据组织方式。

### 1. Organization / 组织

可以理解为一个实验室、课题组、公司或课程团队。

例如：

```text
某大学合成生物学实验室
某课题组
某课程教学实验室
```

组织用于成员管理和数据共享。

---

### 2. Project / 项目

Project 用来组织一个研究方向或实验课题。

例如：

```text
Project A：CRISPR 基因敲除项目
Project B：质粒构建项目
Project C：蛋白表达优化项目
```

一个 Project 下可以有多个实验记录、序列文件、样品记录等。

---

### 3. Folder / 文件夹

Folder 用于进一步分类数据。

例如：

```text
CRISPR Project
  ├── 01_设计
  ├── 02_质粒构建
  ├── 03_细胞转染
  ├── 04_筛选
  └── 05_结果分析
```

这和电脑里的文件夹类似。

---

### 4. Notebook Entry / 实验记录

这是 Benchling 作为电子实验记录本的核心。

你可以在 Notebook Entry 中记录：

- 实验目的；
- 实验日期；
- 实验人员；
- 实验材料；
- 实验步骤；
- 反应体系；
- 图片；
- 表格；
- 结果；
- 结论；
- 附件；
- 相关样品或序列链接。

---

### 5. Registry / 注册库

Registry 是 Benchling 很重要的概念，用来管理标准化的生物对象。

例如：

- DNA 序列；
- 质粒；
- 引物；
- 蛋白；
- 细胞系；
- 抗体；
- 样品；
- 批次。

对于普通课程项目调研，你可以把它理解为“结构化生物对象数据库”。

---

## 三、最典型的使用场景：记录一次 PCR 实验

假设你要用 Benchling 记录一次 PCR 实验，可以这样做。

### Step 1：创建项目

进入 Benchling 后，先创建一个 Project，例如：

```text
Project：质粒构建实验
```

然后在项目下创建文件夹：

```text
01_引物设计
02_PCR扩增
03_酶切连接
04_转化筛选
05_测序验证
```

---

### Step 2：新建 Notebook Entry

在 `02_PCR扩增` 文件夹下新建一篇实验记录。

标题可以写：

```text
PCR amplification of GFP fragment - 2026-07-06
```

或中文：

```text
GFP 片段 PCR 扩增实验 - 2026-07-06
```

---

### Step 3：填写实验信息

可以按照模板记录：

```text
实验目的：
扩增 GFP 片段，用于后续克隆到 pET28a 载体中。

实验日期：
2026-07-06

实验人员：
张三

模板 DNA：
pGFP plasmid

引物：
GFP-F
GFP-R

酶：
Phusion High-Fidelity DNA Polymerase

反应体系：
模板 DNA：1 μL
Forward primer：1 μL
Reverse primer：1 μL
2x Master Mix：25 μL
ddH2O：22 μL
Total：50 μL

PCR 程序：
98°C 30 s
98°C 10 s
58°C 20 s
72°C 30 s
循环 35 次
72°C 5 min
4°C hold

结果：
琼脂糖凝胶电泳显示目标条带约 720 bp。

结论：
PCR 成功，可用于后续胶回收。
```

---

### Step 4：插入图片和附件

你可以上传：

- 电泳胶图；
- Nanodrop 浓度结果；
- Excel 数据；
- 仪器导出的 CSV；
- PDF 方案；
- 测序结果文件。

这样实验记录不会只是文字，而是把所有相关数据集中管理。

---

### Step 5：关联序列、引物和样品

Benchling 的优势之一是可以把实验记录和生物对象关联起来。

例如在记录中链接：

```text
pGFP plasmid
GFP-F primer
GFP-R primer
PCR product sample
```

这样以后点击某个引物或质粒，就可以追溯它参与过哪些实验。

---

### Step 6：保存、共享和协作

如果在团队或组织中，可以把实验记录共享给导师或同组成员。

导师可以：

- 查看实验记录；
- 评论；
- 要求补充信息；
- 审核记录。

这和普通 Word 文档相比，优势在于记录集中、可追溯、权限清晰。

---

## 四、Benchling 的分子生物学功能怎么用

Benchling 不只是实验记录本，它还常用于分子生物学设计。

### 1. DNA 序列管理

你可以创建或导入 DNA 序列，例如：

- 质粒序列；
- PCR 片段；
- 基因序列；
- gRNA 序列；
- 测序结果。

常见操作包括：

- 查看序列；
- 查看特征注释；
- 标注 promoter、ORF、tag、restriction site；
- 翻译蛋白序列；
- 搜索限制性内切酶位点；
- 比对测序结果。

---

### 2. 质粒图谱

Benchling 可以把质粒序列显示为线性图或环形图。

常用于：

- 查看启动子；
- 查看抗性基因；
- 查看多克隆位点；
- 查看插入片段；
- 检查酶切位点；
- 设计克隆方案。

---

### 3. 引物设计

你可以在 DNA 序列上设计引物。

一般流程：

1. 打开目标 DNA 序列；
2. 选择目标区域；
3. 创建 primer；
4. 检查引物长度、Tm 值、GC 含量；
5. 保存引物；
6. 在 PCR 实验记录中引用该引物。

---

### 4. 序列比对

例如测序公司返回了 Sanger sequencing 结果，你可以：

1. 上传测序文件；
2. 与理论质粒序列比对；
3. 检查突变、缺失或插入；
4. 在实验记录中写明验证结果。

这对质粒构建项目非常常用。

---

## 五、Benchling 的 Notebook 怎么组织比较好

如果你要模仿 Benchling 做课程项目，可以重点学习它的组织方式。

一个合理结构可以是：

```text
Organization：Synthetic Biology Lab

Project：pET28a-GFP 质粒构建

Folder：
  01_设计
  02_PCR
  03_酶切连接
  04_转化
  05_菌落PCR
  06_测序验证
  07_蛋白表达

Notebook Entries：
  2026-07-06 GFP PCR 扩增
  2026-07-07 pET28a 双酶切
  2026-07-08 连接与转化
  2026-07-09 菌落 PCR 筛选
  2026-07-10 测序结果分析

Biological Objects：
  pET28a vector
  GFP insert
  GFP-F primer
  GFP-R primer
  DH5α competent cells
```

这种组织方式非常适合作为你们课程项目的需求参考。

---

## 六、Benchling 对课程项目的启发

如果你要做“生物实验记录助手”，可以从 Benchling 借鉴这些设计：

### 1. 实验记录不是普通笔记，而是结构化对象

普通笔记软件只存文字。

Benchling 的思路是：

```text
实验记录 + 样品 + 序列 + 引物 + 附件 + 项目 + 权限
```

你的系统也可以这样设计：

```text
Experiment
Sample
Reagent
Protocol
Attachment
Project
User
Comment
AuditLog
```

---

### 2. 实验记录要能和生物对象互相链接

例如一篇 PCR 记录中引用了：

- Sample-001；
- Primer-F；
- Primer-R；
- Taq enzyme batch-202607；
- Gel image file。

以后查询 Sample-001 时，可以看到它参与过哪些实验。

---

### 3. 模板非常重要

Benchling 中 Notebook 可以支持较规范的实验记录方式。

你的项目可以做成模板化：

```text
PCR 模板
Western blot 模板
qPCR 模板
细胞传代模板
质粒提取模板
电泳模板
```

每个模板都有固定字段，方便统一记录。

---

### 4. 权限和协作是核心功能

在真实实验室中，数据不是完全个人的。

应该支持：

- 个人记录；
- 项目内共享；
- 课题组共享；
- 只读权限；
- 可编辑权限；
- 导师审核；
- 评论批注。

---

### 5. 可追溯性很重要

Benchling 面向科研和企业研发，所以非常强调数据组织、追踪和协作。

你的课程项目可以简化实现：

- 创建人；
- 创建时间；
- 最后修改时间；
- 修改历史；
- 版本号；
- 审核状态。

---

## 七、Benchling 的优点和不足

### 优点

1. 生物实验场景适配度高；
2. 支持 DNA、质粒、引物等生命科学对象；
3. Notebook 和分子设计工具结合紧密；
4. 团队协作方便；
5. 数据可追溯；
6. 适合分子克隆、合成生物学、抗体研发、细胞实验等场景。

### 不足

1. 学习成本比普通笔记软件高；
2. 功能较多，新用户容易迷路；
3. 企业级功能可能需要付费或机构账号；
4. 对简单课程实验来说可能偏重；
5. 本地化和中文支持不一定适合所有国内教学场景。

---

## 八、你们课程项目可以如何“简化 Benchling”

你们不需要完整复刻 Benchling，可以做一个轻量版：

```text
BioNote = 简化版 Benchling Notebook + 样品管理 + 试剂管理
```

建议实现：

| Benchling 功能 | 课程项目简化版 |
|---|---|
| Organization | 实验室/课题组 |
| Project | 项目 |
| Folder | 分类文件夹 |
| Notebook Entry | 实验记录 |
| Registry | 样品库/试剂库 |
| DNA sequence editor | 可选，不建议第一版做 |
| Molecular cloning tools | 可选，高难度 |
| Audit trail | 简化为修改历史 |
| Permission | 简化为个人/组内/公开 |
| AI | 可做实验记录整理助手 |

最适合课程项目的版本是：

```text
用户登录
项目管理
实验记录
实验模板
样品管理
试剂管理
附件上传
评论协作
搜索
导出 PDF
```

---