import { describe, expect, it } from 'vitest'
import { attributeText, messageText, recommendationText } from './messages'
import type { Recommendation } from '../lib/types'

describe('Turkish presentation of English API responses', () => {
  it('translates known API messages while preserving their meaning', () => {
    expect(
      messageText('This result is no longer available. Start a new analysis.', 'NOT_FOUND'),
    ).toBe('Sonuç artık erişilebilir değil. Yeni analiz başlatabilirsiniz.')
  })

  it('uses error codes instead of exposing arbitrary provider text', () => {
    expect(messageText('Untrusted provider output', 'PHOTO_NOT_USABLE')).toBe(
      'Önden çekilmiş, tek yüz içeren net bir fotoğraf yükleyin.',
    )
  })

  it('renders recommendation codes without displaying English explanations', () => {
    const product: Recommendation = {
      productId: 'one',
      brand: 'Test',
      modelCode: 'T1000',
      name: 'Test frames',
      category: 'SUNGLASSES',
      shape: 'ROUND',
      reasonCode: 'DEMO',
      reason: 'Example result: explore round frames.',
      attributes: {},
      offers: [],
    }
    expect(recommendationText(product)).toContain('Bu seçim fotoğraf analizine dayanmıyor.')
    expect(recommendationText({ ...product, reasonCode: 'SHAPE_MATCH' })).toContain(
      'önerilen stillerden biri',
    )
    expect(attributeText('shape', 'Round', 'ROUND')).toBe('Yuvarlak')
    expect(attributeText('material', 'Acetate', 'ROUND')).toBe('Acetate')
  })
})
