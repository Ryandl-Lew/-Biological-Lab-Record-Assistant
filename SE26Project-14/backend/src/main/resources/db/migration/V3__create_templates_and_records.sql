CREATE TABLE record_templates (
  id CHAR(36) NOT NULL PRIMARY KEY,
  scope VARCHAR(20) NOT NULL,
  owner_id CHAR(36) NULL,
  name VARCHAR(120) NOT NULL,
  name_normalized VARCHAR(120) NOT NULL,
  active_name_key VARCHAR(300) NULL,
  experiment_type VARCHAR(100) NULL,
  category VARCHAR(80) NULL,
  description VARCHAR(2000) NULL,
  created_at TIMESTAMP(6) NOT NULL,
  updated_at TIMESTAMP(6) NOT NULL,
  deleted_at TIMESTAMP(6) NULL,
  version BIGINT NOT NULL DEFAULT 0,
  CONSTRAINT uk_template_active_name UNIQUE(active_name_key),
  CONSTRAINT fk_templates_owner FOREIGN KEY(owner_id) REFERENCES users(id)
);
CREATE INDEX idx_templates_scope_owner ON record_templates(scope,owner_id,deleted_at);

CREATE TABLE template_fields (
  id CHAR(36) NOT NULL PRIMARY KEY,
  template_id CHAR(36) NOT NULL,
  field_key VARCHAR(100) NOT NULL,
  label VARCHAR(160) NOT NULL,
  field_type VARCHAR(30) NOT NULL,
  required BOOLEAN NOT NULL DEFAULT FALSE,
  sort_order INT NOT NULL,
  placeholder VARCHAR(300) NULL,
  default_value_json TEXT NULL,
  options_json TEXT NULL,
  CONSTRAINT uk_template_field_key UNIQUE(template_id,field_key),
  CONSTRAINT fk_template_fields_template FOREIGN KEY(template_id) REFERENCES record_templates(id)
);
CREATE INDEX idx_template_fields_order ON template_fields(template_id,sort_order);

CREATE TABLE experiment_records (
  id CHAR(36) NOT NULL PRIMARY KEY,
  code VARCHAR(40) NOT NULL,
  project_id CHAR(36) NOT NULL,
  creator_id CHAR(36) NOT NULL,
  title VARCHAR(255) NOT NULL,
  experiment_type VARCHAR(100) NOT NULL,
  experiment_date DATE NOT NULL,
  purpose VARCHAR(3000) NOT NULL,
  status VARCHAR(30) NOT NULL,
  template_snapshot_json TEXT NOT NULL,
  field_values_json TEXT NOT NULL,
  content_json TEXT NOT NULL,
  content_html_sanitized TEXT NOT NULL,
  content_plain_text TEXT NOT NULL,
  current_revision_no INT NOT NULL DEFAULT 0,
  current_review_id CHAR(36) NULL,
  final_revision_id CHAR(36) NULL,
  version BIGINT NOT NULL DEFAULT 0,
  created_at TIMESTAMP(6) NOT NULL,
  updated_at TIMESTAMP(6) NOT NULL,
  deleted_at TIMESTAMP(6) NULL,
  CONSTRAINT uk_records_code UNIQUE(code),
  CONSTRAINT fk_records_project FOREIGN KEY(project_id) REFERENCES projects(id),
  CONSTRAINT fk_records_creator FOREIGN KEY(creator_id) REFERENCES users(id)
);
CREATE INDEX idx_records_project_status_updated ON experiment_records(project_id,status,updated_at);
CREATE INDEX idx_records_creator_status ON experiment_records(creator_id,status,deleted_at);

INSERT INTO record_templates(id,scope,owner_id,name,name_normalized,active_name_key,experiment_type,category,description,created_at,updated_at,version)
VALUES
('10000000-0000-0000-0000-000000000001','SYSTEM',NULL,'PCR 实验记录','pcr 实验记录','SYSTEM:pcr 实验记录','PCR','分子生物学','PCR 配制、循环参数和结果记录',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,1),
('10000000-0000-0000-0000-000000000002','SYSTEM',NULL,'qPCR 表达分析','qpcr 表达分析','SYSTEM:qpcr 表达分析','qPCR','分子生物学','扩增效率、Ct 值和相对表达分析',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,1),
('10000000-0000-0000-0000-000000000003','SYSTEM',NULL,'细胞培养记录','细胞培养记录','SYSTEM:细胞培养记录','细胞培养','细胞生物学','细胞状态、传代和培养条件记录',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,1);

INSERT INTO template_fields(id,template_id,field_key,label,field_type,required,sort_order,placeholder,default_value_json,options_json) VALUES
('11000000-0000-0000-0000-000000000001','10000000-0000-0000-0000-000000000001','reaction_system','反应体系','MULTI_LINE_TEXT',TRUE,0,'填写试剂及体积',NULL,NULL),
('11000000-0000-0000-0000-000000000002','10000000-0000-0000-0000-000000000001','annealing_temperature','退火温度（℃）','NUMBER',TRUE,1,'例如 60',NULL,NULL),
('11000000-0000-0000-0000-000000000003','10000000-0000-0000-0000-000000000001','result','扩增结果','SELECT',TRUE,2,NULL,NULL,'["成功","失败","需复核"]'),
('11000000-0000-0000-0000-000000000004','10000000-0000-0000-0000-000000000002','ct_value','Ct 值','NUMBER',TRUE,0,NULL,NULL,NULL),
('11000000-0000-0000-0000-000000000005','10000000-0000-0000-0000-000000000002','analysis','结果分析','MULTI_LINE_TEXT',TRUE,1,NULL,NULL,NULL),
('11000000-0000-0000-0000-000000000006','10000000-0000-0000-0000-000000000003','cell_state','细胞状态','MULTI_LINE_TEXT',TRUE,0,NULL,NULL,NULL),
('11000000-0000-0000-0000-000000000007','10000000-0000-0000-0000-000000000003','passage_date','传代日期','DATE',FALSE,1,NULL,NULL,NULL);

