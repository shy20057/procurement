// SRM Module TypeScript Type Definitions
// Corresponds to com.example.procurement.srm.SrmApiModels

// ==================== Common ====================
export interface PageResponse<T> {
  items: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export interface RejectRequest {
  reason: string;
}

export interface ApproveRequest {
  reason?: string;
}

// ==================== D1 供应商邀请单 ====================
export type InviteStatus = 'PENDING' | 'REGISTERED' | 'DECLINED' | 'EXPIRED';

export interface CategoryEntry {
  seq: number;
  categoryCode: string;
  categoryName: string;
  remark?: string;
}

export interface InviteResponse {
  id: number;
  billNo: string;
  inviteeName: string;
  inviteeCreditCode?: string;
  inviteeContact?: string;
  inviteePhone?: string;
  inviteeEmail?: string;
  invitedBy: string;
  inviteDate: string;
  expireDate: string;
  status: InviteStatus;
  relatedRegBillNo?: string;
  categories: CategoryEntry[];
  createdAt: string;
}

export interface CreateInviteRequest {
  inviteeName: string;
  inviteeCreditCode?: string;
  inviteeContact?: string;
  inviteePhone?: string;
  inviteeEmail?: string;
  invitedBy: string;
  expireDate: string;
  categories?: CategoryEntry[];
}

// ==================== D2 供应商注册申请单 ====================
export type SourceType = 'SELF_REG' | 'INVITED';
export type BillStatus = 'DRAFT' | 'SUBMITTED' | 'IN_CERT' | 'IN_REVIEW' | 'APPROVED' | 'REJECTED';

export interface CertEntry {
  seq: number;
  certType: CertEntryType;
  certNo?: string;
  issueOrg?: string;
  validFrom?: string;
  validTo?: string;
  attachmentUrl?: string;
}

export type CertEntryType = 'BUSINESS_LICENSE' | 'TAX_CERT' | 'ORG_CERT' | 'QUALIFICATION' | 'OTHER';

export interface RegResponse {
  id: number;
  billNo: string;
  supplierName: string;
  unifiedCreditCode?: string;
  contactName?: string;
  contactPhone?: string;
  contactEmail?: string;
  regAddress?: string;
  cooperationNote?: string;
  applyDate: string;
  sourceType: SourceType;
  sourceInviteBillNo?: string;
  status: BillStatus;
  categories: CategoryEntry[];
  certs: CertEntry[];
  createdAt: string;
}

export interface CreateRegRequest {
  supplierName: string;
  unifiedCreditCode?: string;
  contactName?: string;
  contactPhone?: string;
  contactEmail?: string;
  regAddress?: string;
  cooperationNote?: string;
  sourceType: SourceType;
  sourceInviteBillNo?: string;
  categories?: CategoryEntry[];
  certs?: CertEntry[];
}

// ==================== D3 供应商认证单 ====================
export type CertType = 'FIRST' | 'RENEWAL' | 'CHANGE';
export type CertConclusion = 'QUALIFIED' | 'TEMP_QUALIFIED' | 'UNQUALIFIED';
export type CertDimension = 'FACTORY' | 'QUALITY' | 'DELIVERY' | 'PRICE' | 'SERVICE';
export type CertResult = 'PASS' | 'FAIL' | 'NA';

export interface CertDimensionEntry {
  seq: number;
  dimension: CertDimension;
  result?: CertResult;
  score?: number;
  responsiblePerson?: string;
  remark?: string;
  attachmentUrl?: string;
}

export interface CertResponse {
  id: number;
  billNo: string;
  sourceBillNo?: string;
  sourceBillType?: string;
  supplierName: string;
  unifiedCreditCode?: string;
  certType: CertType;
  startDate: string;
  responsiblePerson: string;
  conclusion?: CertConclusion;
  validUntil?: string;
  status: BillStatus;
  entries: CertDimensionEntry[];
  approvedAt?: string;
  createdAt: string;
}

export interface CreateCertRequest {
  supplierCode?: string;
  supplierName: string;
  unifiedCreditCode?: string;
  certType: CertType;
  startDate: string;
  responsiblePerson: string;
  entries?: CertDimensionEntry[];
}

// ==================== D4 供应商档案 ====================
export type SupplierType = 'MANUFACTURER' | 'TRADER' | 'SERVICE';
export type CertStatus = 'POTENTIAL' | 'QUALIFIED' | 'TEMP_QUALIFIED' | 'FROZEN' | 'BLACKLIST';

export interface ContactEntry {
  seq: number;
  contactName: string;
  position?: string;
  phone?: string;
  email?: string;
  isPrimary: boolean;
}

export interface ArchiveResponse {
  supplierCode: string;
  supplierName: string;
  unifiedCreditCode?: string;
  supplierType?: SupplierType;
  certStatus: CertStatus;
  certValidUntil?: string;
  perfGrade?: PerfGrade;
  perfEvalCount: number;
  lastPerfDate?: string;
  bankName?: string;
  bankAccount?: string;
  bankAccountName?: string;
  sourceCertBillNo?: string;
  categories: CategoryEntry[];
  certs: CertEntry[];
  contacts: ContactEntry[];
  createdAt: string;
}

export interface UpdateArchiveRequest {
  supplierType?: SupplierType;
  bankName?: string;
  bankAccount?: string;
  bankAccountName?: string;
}

export interface ExpiringCertResponse {
  supplierCode: string;
  supplierName: string;
  certType?: CertEntryType;
  validTo: string;
}

export interface RelatedBillResponse {
  billType: string;
  billNo: string;
  status: string;
  billDate: string;
}

// ==================== D5 供应商变更单 ====================
export type ChangeType = 'BASIC' | 'CONTACT' | 'BANK' | 'CERT' | 'CATEGORY';

export interface ChangeFieldEntry {
  seq: number;
  fieldName: string;
  oldValue?: string;
  newValue: string;
}

export interface ChangeResponse {
  id: number;
  billNo: string;
  supplierCode: string;
  changeType: ChangeType;
  changeReason?: string;
  applicant: string;
  applyDate: string;
  status: BillStatus;
  entries: ChangeFieldEntry[];
  approvedAt?: string;
  createdAt: string;
}

export interface CreateChangeRequest {
  supplierCode: string;
  changeType: ChangeType;
  changeReason?: string;
  applicant: string;
  entries?: ChangeFieldEntry[];
}

// ==================== D6 供应商冻结/解冻单 ====================
export type FreezeApplyType = 'FREEZE' | 'UNFREEZE';
export type FreezeTriggerSource = 'PERF_WARNING' | 'COMPLIANCE' | 'MANUAL' | 'SYSTEM_AUTO' | 'AUTO_PERF';
export type FreezeScopeType = 'ALL' | 'CATEGORY';

export interface FreezeScopeEntry {
  seq: number;
  scopeType: FreezeScopeType;
  categoryCode?: string;
  categoryName?: string;
  remark?: string;
}

export interface FreezeResponse {
  id: number;
  billNo: string;
  supplierCode: string;
  applyType: FreezeApplyType;
  triggerSource: FreezeTriggerSource;
  sourcePerfBillNo?: string;
  reason: string;
  applicant: string;
  applyDate: string;
  status: BillStatus;
  effectiveDate?: string;
  preStatusSnapshot?: CertStatus;
  rectificationNote?: string;
  entries: FreezeScopeEntry[];
  createdAt: string;
}

export interface CreateFreezeRequest {
  supplierCode: string;
  applyType: FreezeApplyType;
  triggerSource: FreezeTriggerSource;
  sourcePerfBillNo?: string;
  reason: string;
  applicant: string;
  entries?: FreezeScopeEntry[];
}

// ==================== D7 供应商黑名单单 ====================
export type BlacklistTriggerSource = 'PERF_SEVERE' | 'COMPLIANCE_VIOLATION' | 'FRAUD' | 'MANUAL' | 'AUTO_PERF';

export interface BlacklistEvidenceEntry {
  seq: number;
  evidenceType?: string;
  evidenceDesc?: string;
  attachmentUrl?: string;
}

export interface BlacklistResponse {
  id: number;
  billNo: string;
  supplierCode: string;
  triggerSource: BlacklistTriggerSource;
  sourcePerfBillNo?: string;
  reason: string;
  applicant: string;
  applyDate: string;
  approvalLevel?: string;
  isPermanent: boolean;
  reviewDate?: string;
  status: BillStatus;
  effectiveDate?: string;
  entries: BlacklistEvidenceEntry[];
  createdAt: string;
}

export interface CreateBlacklistRequest {
  supplierCode: string;
  triggerSource: BlacklistTriggerSource;
  sourcePerfBillNo?: string;
  reason: string;
  applicant: string;
  approvalLevel?: string;
  isPermanent: boolean;
  reviewDate?: string;
  entries?: BlacklistEvidenceEntry[];
}

// ==================== D8 供应商绩效考核单 ====================
export type PerfDimension = 'QUALITY' | 'DELIVERY' | 'PRICE' | 'SERVICE' | 'COMPREHENSIVE';
export type PerfGrade = 'A' | 'B' | 'C' | 'D';
export type PerfStatus = 'DRAFT' | 'SUBMITTED' | 'CONFIRMED';
export type PerfEvalType = 'ANNUAL' | 'QUARTERLY' | 'MONTHLY' | 'PROJECT';

export interface PerfDimensionEntry {
  seq: number;
  dimension: PerfDimension;
  weight: number;
  score: number;
  sourceBillNo?: string;
  remark?: string;
}

export interface PerfResponse {
  id: number;
  billNo: string;
  supplierCode: string;
  period: string;
  evalType: PerfEvalType;
  totalScore: number;
  grade?: PerfGrade;
  status: PerfStatus;
  evaluator: string;
  evalDate: string;
  entries: PerfDimensionEntry[];
  createdAt: string;
}

export interface CreatePerfRequest {
  supplierCode: string;
  period: string;
  evalType: PerfEvalType;
  evaluator: string;
  entries?: PerfDimensionEntry[];
}
