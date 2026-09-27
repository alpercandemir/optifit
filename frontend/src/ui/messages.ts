import type { Recommendation, Shape } from '../lib/types'

const messages: Record<string, string> = {
  'Select an eyewear category.': 'Bir gözlük türü seçin.',
  'Enter a valid budget between 1 and 1,000,000 TRY.': 'Geçerli bir bütçe girin (1–1.000.000 TL).',
  'Invalid style.': 'Geçersiz stil.',
  'Invalid color.': 'Geçersiz renk.',
  'A valid idempotency key is required.': 'Geçerli bir işlem anahtarı gerekli.',
  'This idempotency key was already used with a different photo or preferences.':
    'Bu işlem anahtarı farklı bir fotoğraf veya tercih için kullanıldı.',
  'The service is busy. Please try again shortly.':
    'Şu anda yoğunluk var. Birazdan tekrar deneyin.',
  'Demo mode: your photo was not analyzed. Products are examples; prices and availability have not been verified.':
    'Demo modu: fotoğrafınız analiz edilmedi. Ürünler örnek kaynaklardır; fiyat ve stok doğrulanmamıştır.',
  'The demo catalog has no optical frames. Live mode searches online for this category.':
    'Demo kataloğunda numaralı çerçeve bulunmuyor. Canlı modda bu kategori internette aranır.',
  'Fewer than three verified models match your filters. Try a different budget or eyewear category.':
    'Filtrelerinize uyan üç doğrulanmış model bulunamadı. Bütçe veya gözlük türünü değiştirerek tekrar deneyebilirsiniz.',
  'The analysis service is unavailable. Your photo has been deleted; please try again later.':
    'Analiz hizmetine ulaşılamadı. Fotoğrafınız silindi; daha sonra tekrar deneyin.',
  'The analysis timed out. Your photo has been deleted; please try again.':
    'İşlem beklenenden uzun sürdü. Fotoğrafınız silindi; tekrar deneyebilirsiniz.',
  'The photo must not exceed 10 MB.': 'Fotoğraf en fazla 10 MB olabilir.',
  'Check the photo and your preferences.': 'Fotoğrafı ve tercihlerinizi kontrol edin.',
  'The request could not be completed. Please try again.':
    'İşlem tamamlanamadı. Lütfen tekrar deneyin.',
  'Your session must be refreshed. Reload the page and try again.':
    'Oturum yenilenmeli. Sayfayı yenileyip tekrar deneyin.',
  'Select a photo between 1 byte and 10 MB.': '1 bayt ile 10 MB arasında bir fotoğraf seçin.',
  'Photo processing is busy. Please try again shortly.':
    'Fotoğraf işleme yoğun. Biraz sonra yeniden deneyin.',
  'The photo could not be read. Select a JPEG, PNG or WebP image.':
    'Fotoğraf okunamadı. JPEG, PNG veya WebP seçin.',
  'The declared file type does not match the image content.':
    'Dosya türü ile fotoğraf içeriği eşleşmiyor.',
  'The photo must be at least 160 by 160 pixels and no larger than 20 megapixels.':
    'Fotoğraf en az 160 × 160 piksel ve en fazla 20 megapiksel olmalı.',
  'The photo could not be processed. Try another photo.':
    'Fotoğraf işlenemedi. Başka bir fotoğraf deneyin.',
  'The product search service is unavailable. Please try again later.':
    'Ürün arama hizmetine şu anda ulaşılamıyor. Daha sonra tekrar deneyin.',
  'The request was cancelled.': 'İşlem durduruldu.',
  'Product sources could not be verified. Please try again later.':
    'Ürün kaynakları doğrulanamadı. Daha sonra tekrar deneyin.',
  'Demo: no photo analysis was performed.': 'Demo: fotoğraf analizi yapılmadı.',
  'Assess the photo to suggest eyewear frame styles.':
    'Fotoğraftan gözlük çerçevesi stil önerileri için görsel değerlendirme yap.',
  'The visual assessment could not be completed. Please try again.':
    'Görsel değerlendirme tamamlanamadı. Tekrar deneyin.',
  'Upload a clear, front-facing photo containing exactly one face.':
    'Fotoğrafta yeterli bilgi tespit edemedik.',
  "We couldn't detect photo as enough information.":
    'Fotoğrafta yeterli bilgi tespit edemedik.',
  'Facial contours are not clear enough. Try a sharper photo.':
    'Yüz hatları yeterince seçilemiyor. Daha net bir fotoğraf deneyin.',
  'The analysis limit has been reached. Please try again later.':
    'Analiz sınırına ulaşıldı. Lütfen daha sonra tekrar deneyin.',
  'This is an example collection. No photo analysis was performed; prices and availability have not been verified.':
    'Bu bir örnek koleksiyon. Fotoğraf analizi yapılmadı; ürün fiyatı ve stoku doğrulanmadı.',
  'Acknowledge the photo processing notice before starting an analysis.':
    'Analiz için fotoğraf işleme bilgilendirmesini onaylayın.',
  'This result is no longer available. Start a new analysis.':
    'Sonuç artık erişilebilir değil. Yeni analiz başlatabilirsiniz.',
  'Upload a photo containing exactly one face.': 'Tek yüz içeren fotoğraf yükleyin.',
  'Unable to connect. Check your internet connection and try again.':
    'Bağlantı kurulamadı. İnternet bağlantınızı kontrol edip tekrar deneyin.',
  'The request could not be completed. Try again.': 'İşlem tamamlanamadı. Tekrar deneyin.',
  'This link is unavailable.': 'Bağlantı kullanılamıyor.',
  'The link could not be copied. Open the product page and copy the address from your browser.':
    'Bağlantı kopyalanamadı. Ürün bağlantısını açıp adres çubuğundan kopyalayabilirsiniz.',
}

const errors: Record<string, string> = {
  PHOTO_NOT_USABLE: 'Fotoğrafta yeterli bilgi tespit edemedik.',
  AI_RESPONSE_INVALID: 'Görsel değerlendirme tamamlanamadı. Tekrar deneyin.',
  SESSION_EXPIRED: 'Oturum yenilenmeli. Sayfayı yenileyip tekrar deneyin.',
  NOT_FOUND: 'Sonuç artık erişilebilir değil. Yeni analiz başlatabilirsiniz.',
  INVALID_INPUT: 'Fotoğrafı ve tercihlerinizi kontrol edin.',
}

export function messageText(message: string | null | undefined, code?: string | null): string {
  return (
    (message && messages[message]) ||
    (code && errors[code]) ||
    'İşlem tamamlanamadı. Lütfen tekrar deneyin.'
  )
}

const shapes: Record<Shape, string> = {
  ROUND: 'Yuvarlak',
  RECTANGULAR: 'Köşeli',
  CAT_EYE: 'Kedi gözü',
  AVIATOR: 'Damla',
  GEOMETRIC: 'Geometrik',
  BROWLINE: 'Kaş çizgili',
  UNKNOWN: 'Bilinmeyen',
}

export function attributeText(key: string, value: string, shape: Shape): string {
  return key === 'shape' ? shapes[shape] : value
}

export function recommendationText(product: Recommendation): string {
  switch (product.reasonCode) {
    case 'DEMO':
      return `Örnek sonuç: ${shapes[product.shape]} çerçeve stilini keşfedin. Bu seçim fotoğraf analizine dayanmıyor.`
    case 'SHAPE_MATCH':
      return `${shapes[product.shape]} formu, fotoğrafınızdaki görünür yüz hatları için önerilen stillerden biri. Uyum ve rahatlığı deneyerek kontrol edin.`
    default:
      return 'Tercihlerinize göre değerlendirebileceğiniz farklı bir stil. Fiziksel uyumu deneyerek kontrol edin.'
  }
}
