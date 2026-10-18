// STC 寻源到合同模块数据类型定义
// 严格对齐《04-STC技术方案设计文档.md》与《05-STC数据模型设计.md》

export type SourcingMethod = 'TENDER' | 'RFQ' | 'NEGOTIATION' | 'SINGLE_SOURCE'
export type TenderType = 'PUBLIC' | 'INVITED'
export type EvaluationMethod = 'LOWEST_PRICE' | 'COMPREHENSIVE'
export type AwardSourceType = 'TENDER' | 'RFQ' | 'SINGLE_SOURCE'
export type ContractType = 'FRAMEWORK' | 'ONE_TIME'

// 通用单据状态
export type BillStatus = 'DRAFT' | 'SUBMITTED' | 'IN_REVIEW' | 'APPROVED' | 'REJECTED' | 'UNAPPROVED' | 'CLOSED' | 'CANCELLED'
export type TenderStatus = 'DRAFT' | 'ANNOUNCED' | 'BIDDING' | 'OPENED' | 'EVALUATING' | 'AWARDED' | 'CANCELLED'
export type RFQStatus = 'DRAFT' | 'PUBLISHED' | 'QUOTING' | 'COMPARING' | 'CLOSED'
export type ContractStatus = 'DRAFT' | 'IN_REVIEW' | 'APPROVED' | 'EXPIRED' | 'TERMINATED'
export type PriceListStatus = 'DRAFT' | 'EFFECTIVE' | 'EXPIRED' | 'SUPERSEDED'
export type BPAStatus = 'DRAFT' | 'EFFECTIVE' | 'EXHAUSTED' | 'EXPIRED' | 'TERMINATED'
export type SRStatus = 'DRAFT' | 'EFFECTIVE' | 'EXPIRED'

// ================= D9 寻源立项单 =================
export interface SourcingCandidateEntry {
  id?: number
  seq: number
  supplierCode: string
  supplierName: string
  matchResult?: string
  certStatus?: string
  perfGrade?: string
  remark?: string
}

export interface SourcingProject {
  id: number
  billNo: string
  projectName: string
  categoryCode: string
  categoryName: string
  budgetAmount: number
  currency: string
  planStartDate: string
  planEndDate: string
  sourcingMethod: SourcingMethod
  singleSourceReason?: string
  initiator: string
  applyDate: string
  status: BillStatus
  candidates: SourcingCandidateEntry[]
  createdDate?: string
}

// ================= D10 招标单 =================
export interface TenderBidderEntry {
  id?: number
  seq: number
  supplierCode: string
  supplierName: string
  bidAmount?: number
  submitTime?: string
  prequalResult: 'PASS' | 'FAIL' | 'NA'
  isShortlisted: boolean
}

export interface TenderEvaluationEntry {
  id?: number
  seq: number
  supplierCode: string
  supplierName: string
  techScore?: number
  bizScore?: number
  totalScore?: number
  rank?: number
  judgeComment?: string
}

export interface TenderBill {
  id: number
  billNo: string
  sourceProjectBillNo?: string
  tenderTitle: string
  tenderType: TenderType
  announceDate: string
  bidDeadline: string
  openDate: string
  needPrequalification: boolean
  evaluationMethod: EvaluationMethod
  status: TenderStatus
  bidders: TenderBidderEntry[]
  evaluations: TenderEvaluationEntry[]
}

// ================= D11 询价单 =================
export interface RFQQuoteEntry {
  id?: number
  roundNo: number
  supplierCode: string
  supplierName: string
  itemCode: string
  itemName: string
  qty: number
  unit: string
  quoteUnitPrice: number
  quoteAmount: number
  quoteDate: string
  deliveryDays?: number
  remark?: string
}

export interface RFQBill {
  id: number
  billNo: string
  sourceProjectBillNo?: string
  rfqTitle: string
  maxRounds: number
  currentRound: number
  quoteDeadline: string
  status: RFQStatus
  quotes: RFQQuoteEntry[]
}

// ================= D12 定标单 =================
export interface AwardItemEntry {
  id?: number
  seq: number
  categoryCode?: string
  itemCode: string
  itemName: string
  spec?: string
  qty: number
  unit: string
  unitPrice: number
  totalAmount: number
  remark?: string
}

export interface AwardBill {
  id: number
  billNo: string
  sourceBillType: AwardSourceType
  sourceBillNo: string
  awardSupplierCode: string
  awardSupplierName: string
  awardSupplierCertStatus?: string // 用于 C3 实时风控校验
  awardConclusion: string
  totalAwardAmount: number
  currency: string
  status: BillStatus
  items: AwardItemEntry[]
}

// ================= D13 采购合同 =================
export interface ContractClauseEntry {
  id?: number
  seq: number
  clauseType: 'TARGET' | 'PAYMENT' | 'WARRANTY' | 'OTHER'
  categoryCode?: string
  itemName?: string
  qty?: number
  unitPrice?: number
  remark?: string
}

export interface PurchaseContract {
  id: number
  billNo: string
  sourceAwardBillNo?: string
  supplierCode: string
  supplierName: string
  contractName: string
  contractAmount: number
  currency: string
  validFrom: string
  validTo: string
  contractType: ContractType
  signatory: string
  legalReviewStatus: 'APPROVED' | 'IN_REVIEW' | 'PENDING'
  sealStatus: 'SEALED' | 'UNSEALED'
  status: ContractStatus
  clauses: ContractClauseEntry[]
}

// ================= D14 价目表 =================
export interface PriceListItemEntry {
  id?: number
  seq: number
  categoryCode: string
  itemCode: string
  itemName: string
  unitPrice: number
  currency: string
  tieredPriceNote?: string
  regionalDiffNote?: string
}

export interface PriceList {
  id: number
  billNo: string
  sourceAwardBillNo?: string
  priceListName: string
  supplierCode: string
  supplierName: string
  effectiveFrom: string
  effectiveTo: string
  status: PriceListStatus
  items: PriceListItemEntry[]
}

// ================= D15 框架协议 BPA =================
export interface BPACategoryEntry {
  id?: number
  seq: number
  categoryCode: string
  categoryName: string
  itemCode?: string
  unitPrice?: number
  maxQty?: number
  remark?: string
}

export interface BPABill {
  id: number
  billNo: string
  sourceAwardBillNo?: string
  agreementName: string
  supplierCode: string
  supplierName: string
  totalQuotaAmount: number
  usedQuotaAmount: number
  currency: string
  validFrom: string
  validTo: string
  status: BPAStatus
  categories: BPACategoryEntry[]
}

// ================= D16 SR 配额单 =================
export interface SupplierRatioEntry {
  id?: number
  seq: number
  supplierCode: string
  supplierName: string
  ratioPercent: number
  perfGradeRef?: string // 只读引用 SRM
  remark?: string
}

export interface SupplierRatioBill {
  id: number
  billNo: string
  categoryCode: string
  categoryName: string
  ratioCycle: string
  status: SRStatus
  ratios: SupplierRatioEntry[]
}
