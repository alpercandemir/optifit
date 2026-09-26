export type Category = 'OPTICAL' | 'SUNGLASSES'
export type Shape =
  'RECTANGULAR' | 'ROUND' | 'CAT_EYE' | 'AVIATOR' | 'GEOMETRIC' | 'BROWLINE' | 'UNKNOWN'
export interface Offer {
  merchantName: string
  productUrl: string
  merchantUrl: string
  price: number | null
  currency: string
  availability: string
  checkedAt: string | null
}
export interface Recommendation {
  imageUrl?: string | null
  productId: string
  brand: string
  modelCode: string
  name: string
  category: Category
  shape: Shape
  reasonCode: 'DEMO' | 'SHAPE_MATCH' | 'STYLE_ALTERNATIVE'
  reason: string
  attributes: Record<string, string>
  offers: Offer[]
}
export interface Job {
  jobId: string
  status: 'QUEUED' | 'ANALYZING' | 'SEARCHING' | 'COMPLETED' | 'PARTIAL' | 'NO_MATCH' | 'FAILED'
  expiresAt: string
  recommendations: Recommendation[]
  warnings: string[]
  demo: boolean
  errorCode: string | null
  message: string | null
}
export interface Session {
  csrfToken: string
  csrfHeader: string
  mode: 'demo' | 'live'
  resultTtlSeconds: number
}
export interface Preferences {
  category: Category
  budget: string
  style: string
  color: string
  consent: boolean
}
export const terminal = (status: Job['status']) =>
  ['COMPLETED', 'PARTIAL', 'NO_MATCH', 'FAILED'].includes(status)
