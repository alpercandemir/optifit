<script setup lang="ts">
import { messageText } from './ui/messages'
import { computed, nextTick, onBeforeUnmount, onMounted, ref } from 'vue'
import {
  ArrowRight,
  ArrowUpRight,
  Sparkles,
  ShieldCheck,
  ScanFace,
  Camera,
  Glasses,
  Check,
  Plus,
  Minus,
  X,
  RotateCcw,
  Trash2,
  Info,
  CircleAlert,
} from 'lucide-vue-next'
import FrameArt from './components/FrameArt.vue'
import UploadDialog from './components/UploadDialog.vue'
import ProductCard from './components/ProductCard.vue'
import { ApiError, createJob, deleteJob, getExamples, getJob, getSession } from './lib/api'
import { terminal } from './lib/types'
import type { Job, Preferences, Session } from './lib/types'
const session = ref<Session | null>(null)
const dialogOpen = ref(false)
const busy = ref(false)
const exampleBusy = ref(false)
const job = ref<Job | null>(null)
const error = ref('')
const connectionError = ref('')
const toast = ref('')
const results = ref<HTMLElement>()
const category = ref<'OPTICAL' | 'SUNGLASSES'>('SUNGLASSES')
const selectedFaq = ref<number | null>(0)
const privacyOpen = ref(false)
const privacyDialog = ref<HTMLDialogElement>()
const deleting = ref(false)
let poll: ReturnType<typeof setTimeout> | undefined
let toastTimeout: ReturnType<typeof setTimeout> | undefined
let expiryTimer: ReturnType<typeof setInterval> | undefined
let activeGeneration = 0
let currentKey = ''
let lastInput: { file: File; fingerprint: string } | null = null
const demo = computed(() => session.value?.mode === 'demo')
const finished = computed(() => job.value && terminal(job.value.status))
const examples = computed(() => job.value?.jobId === 'example')
const faqs = [
  {
    q: 'Bana uygun gözlük nasıl seçiliyor?',
    a: 'Canlı analizde fotoğrafındaki görünür yüz hatları değerlendirilir. Gözlük türün, bütçen ve stil tercihlerinle birlikte internetteki ürünler arasından üç farklı model seçilir. Bu bir görsel stil önerisidir; ölçü ve rahatlığı gözlüğü deneyerek kontrol etmelisin.',
  },
  {
    q: 'Fotoğrafıma ne oluyor?',
    a: 'Fotoğrafın yalnızca analiz için geçici olarak işlenir, analiz aşaması tamamlanınca silinir. Sonuçların bir saat boyunca aynı tarayıcı oturumunda erişilebilir. İstediğin zaman sonucu silebilirsin. Canlı analizde fotoğraf Google Gemini’ye iletilir ve sağlayıcının veri saklama koşulları da geçerlidir.',
  },
  {
    q: 'Ücret ödemem veya üye olmam gerekiyor mu?',
    a: 'Hayır. Fotoğraf yüklemek ve öneri almak ücretsizdir; hesap açmana gerek yok. Satın almak istersen ilgili mağazanın kendi sitesine yönlendirilirsin.',
  },
  {
    q: 'Numaralı gözlük de bulabilir miyim?',
    a: 'Evet, canlı modda optik çerçeve veya güneş gözlüğü seçebilirsin. Optik öneriler yalnızca çerçeve içindir; reçete ve cam seçimi yapılmaz. Demo kataloğu güneş gözlüğü örnekleriyle sınırlıdır.',
  },
]
function notify(message: string) {
  toast.value = message
  clearTimeout(toastTimeout)
  toastTimeout = setTimeout(() => (toast.value = ''), 5000)
}
function openUpload(type = category.value) {
  category.value = type
  error.value = ''
  dialogOpen.value = true
}
function stopPoll() {
  clearTimeout(poll)
  activeGeneration++
}
async function refreshSession() {
  try {
    session.value = await getSession()
    connectionError.value = ''
    return session.value
  } catch (e) {
    connectionError.value = message(e)
    return null
  }
}
function message(error: unknown) {
  return error instanceof ApiError ? messageText(error.message, error.code) : 'İşlem tamamlanamadı.'
}
async function showResults() {
  dialogOpen.value = false
  busy.value = false
  await nextTick()
  results.value?.scrollIntoView({
    behavior: matchMedia('(prefers-reduced-motion: reduce)').matches ? 'instant' : 'smooth',
    block: 'start',
  })
}
async function watchJob(id: string, generation: number, failures = 0) {
  try {
    const result = await getJob(id)
    if (generation !== activeGeneration) return
    job.value = result
    if (terminal(result.status)) {
      if (result.status === 'FAILED') {
        busy.value = false
        error.value = messageText(result.message, result.errorCode)
        dialogOpen.value = true
        lastInput = null
        currentKey = ''
      } else await showResults()
      return
    }
    busy.value = true
    dialogOpen.value = true
    poll = setTimeout(() => watchJob(id, generation), 1000)
  } catch (e) {
    if (generation !== activeGeneration) return
    if (e instanceof ApiError && e.status === 404) {
      job.value = null
      sessionStorage.removeItem('optifit-job')
      busy.value = false
      error.value = messageText(e.message, e.code)
      dialogOpen.value = false
      notify(messageText(e.message, e.code))
      return
    }
    if (failures < 2) {
      poll = setTimeout(() => watchJob(id, generation, failures + 1), 2000)
      return
    }
    busy.value = false
    dialogOpen.value = false
    connectionError.value = 'Sonuca bağlantı kesildi. İşlem arka planda devam ediyor olabilir.'
  }
}
async function submit(file: File, prefs: Preferences) {
  error.value = ''
  busy.value = true
  const freshSession = await refreshSession()
  if (!freshSession) {
    busy.value = false
    error.value = connectionError.value
    return
  }
  const fingerprint = JSON.stringify(prefs)
  if (!currentKey || lastInput?.file !== file || lastInput.fingerprint !== fingerprint)
    currentKey = crypto.randomUUID()
  lastInput = { file, fingerprint }
  stopPoll()
  const generation = activeGeneration
  try {
    const result = await createJob(file, prefs, currentKey, freshSession)
    if (generation !== activeGeneration) {
      await deleteJob(result.jobId, freshSession).catch(() => undefined)
      return
    }
    job.value = result
    sessionStorage.setItem('optifit-job', result.jobId)
    await watchJob(result.jobId, generation)
  } catch (e) {
    if (generation !== activeGeneration) return
    busy.value = false
    error.value = message(e)
    if (e instanceof ApiError && e.status !== 0) {
      currentKey = ''
      lastInput = null
    }
  }
}
async function removeResult(close = true) {
  if (deleting.value) return
  deleting.value = true
  try {
    if (job.value && !examples.value) {
      const fresh = await refreshSession()
      if (!fresh) throw new Error(connectionError.value)
      await deleteJob(job.value.jobId, fresh).catch((e) => {
        if (!(e instanceof ApiError && e.status === 404)) throw e
      })
    }
    stopPoll()
    sessionStorage.removeItem('optifit-job')
    job.value = null
    currentKey = ''
    lastInput = null
    busy.value = false
    if (close) dialogOpen.value = false
    notify('Geçici sonuç ve fotoğraf verileri silindi.')
  } catch (e) {
    notify(message(e))
  } finally {
    deleting.value = false
  }
}
async function cancel() {
  if (!job.value || terminal(job.value.status)) {
    stopPoll()
    busy.value = false
    dialogOpen.value = false
    return
  }
  await removeResult()
}
async function showExamples() {
  if (exampleBusy.value) return
  exampleBusy.value = true
  try {
    stopPoll()
    job.value = await getExamples()
    sessionStorage.removeItem('optifit-job')
    await showResults()
  } catch (e) {
    notify(message(e))
  } finally {
    exampleBusy.value = false
  }
}
async function retryConnection() {
  const ready = await refreshSession()
  const id = sessionStorage.getItem('optifit-job')
  if (ready && id) {
    stopPoll()
    await watchJob(id, activeGeneration)
  }
}
async function privacy() {
  privacyOpen.value = true
  await nextTick()
  privacyDialog.value?.showModal()
}
function closePrivacy() {
  privacyDialog.value?.close()
  privacyOpen.value = false
}
onMounted(async () => {
  await refreshSession()
  const id = sessionStorage.getItem('optifit-job')
  if (id && session.value) await watchJob(id, activeGeneration)
  expiryTimer = setInterval(() => {
    if (job.value && !examples.value && Date.parse(job.value.expiresAt) <= Date.now()) {
      stopPoll()
      job.value = null
      busy.value = false
      dialogOpen.value = false
      sessionStorage.removeItem('optifit-job')
      notify('Sonucunun erişim süresi doldu. Yeni bir fotoğrafla tekrar başlayabilirsin.')
    }
  }, 1000)
})
onBeforeUnmount(() => {
  stopPoll()
  clearTimeout(toastTimeout)
  clearInterval(expiryTimer)
})
</script>

<template>
  <a class="skip-link" href="#main">İçeriğe geç</a>
  <header class="site-header wrap">
    <a href="#" class="wordmark" aria-label="OptiFit ana sayfa"
      ><span class="brand-icon"><Glasses :size="27" :stroke-width="1.8" /></span>opti<span>fit</span
      ><span class="brand-dot">.</span></a
    >
    <nav aria-label="Ana menü">
      <a href="#how-it-works">Nasıl çalışır?</a><a href="#style-guide">Stilini keşfet</a
      ><a href="#faq">Merak edilenler</a>
    </nav>
    <button class="button nav-cta" @click="openUpload()">
      Gözlüğünü bul <ArrowUpRight :size="17" />
    </button>
  </header>
  <div v-if="connectionError" class="connection-banner" role="alert">
    <CircleAlert :size="18" /><span>{{ connectionError }}</span
    ><button class="text-button" @click="retryConnection">Tekrar bağlan</button>
  </div>
  <div v-if="demo" class="demo-banner">
    <span class="status-dot" />Demo deneyimi <span class="banner-divider">/</span
    ><span>Fotoğraf analizi ve canlı ürün araması kapalı.</span>
  </div>

  <main id="main">
    <section class="hero wrap" aria-labelledby="hero-heading">
      <div class="hero-copy">
        <div class="eyebrow hero-eyebrow">
          <span class="tiny-spark">✳</span>YAPAY ZEKÂ DESTEKLİ STİL KEŞFİ
        </div>
        <h1 id="hero-heading">Bakışın kadar<br /><em>sana özel.</em></h1>
        <p class="hero-description">
          Binlerce çerçeve. Tek bir sen.<br />Bir fotoğraf yükle, yüzüne ve tarzına uygun<br
            class="desktop-break"
          />
          üç gözlük önerisini keşfet.
        </p>
        <div class="hero-actions">
          <button class="button button-primary hero-cta" @click="openUpload()">
            <Camera :size="20" />Fotoğrafını yükle<ArrowUpRight :size="21" /></button
          ><button class="example-button" :disabled="exampleBusy" @click="showExamples">
            {{ exampleBusy ? 'Hazırlanıyor…' : 'Örnek sonuçları gör' }}<ArrowRight :size="16" />
          </button>
        </div>
        <div class="hero-promises">
          <span><Check :size="15" />Tamamen ücretsiz</span
          ><span><Check :size="15" />Üyelik gerekmez</span>
        </div>
        <div class="hero-footnote">
          <ShieldCheck :size="19" />
          <p>
            Fotoğrafın seninle kalır.<br /><span
              >Analiz tamamlandığında sistemimizden silinir.</span
            >
          </p>
        </div>
      </div>
      <div class="hero-visual">
        <div class="portrait-frame">
          <img
            src="/images/editorial-portrait.jpg"
            alt="Amber renkli optik gözlük takan model — yapay zekâ ile oluşturulmuş tanıtım görseli"
            width="1120"
            height="1400"
            fetchpriority="high"
          />
          <div class="portrait-topline"><span>THE EVERYDAY EDIT</span><span>01 — 03</span></div>
          <span class="portrait-caption">BAKIŞ AÇINI DEĞİŞTİR.</span>
        </div>
        <div class="floating-note">
          <span class="note-icon"><Sparkles :size="21" /></span>
          <div>
            <strong>Doğru çerçeve,<br />bambaşka bir sen.</strong
            ><span>Stilini birlikte bulalım.</span>
          </div>
        </div>
        <div class="round-stamp">
          <span>SANA ÖZEL</span><strong>3</strong><span>ALTERNATİF</span>
        </div>
        <span class="image-credit">Yapay zekâ ile oluşturulmuş editoryal görsel</span>
      </div>
    </section>

    <section
      v-if="finished"
      ref="results"
      class="results-section"
      aria-labelledby="results-heading"
    >
      <div class="wrap">
        <div class="section-heading result-heading">
          <div>
            <span class="eyebrow">{{ job?.demo ? 'ÖRNEK KOLEKSİYON' : 'SENİN İÇİN SEÇİLDİ' }}</span>
            <h2 id="results-heading">
              {{
                job?.status === 'NO_MATCH'
                  ? 'Biraz daha keşfedelim.'
                  : job?.status === 'FAILED'
                    ? 'Yeniden deneyelim.'
                    : 'Yeni bakışın burada.'
              }}
            </h2>
          </div>
          <button class="button button-outline" @click="openUpload()">
            <RotateCcw :size="16" />Yeniden keşfet
          </button>
        </div>
        <div v-if="job?.warnings.length" class="result-notice" role="status">
          <Info :size="20" />
          <div>
            <p v-for="warning in job.warnings" :key="warning">{{ messageText(warning) }}</p>
          </div>
        </div>
        <p v-if="job?.status === 'FAILED'" class="form-error" role="alert">
          {{ messageText(job.message, job.errorCode) }}
        </p>
        <div v-if="job?.recommendations.length" class="product-grid">
          <ProductCard
            v-for="(product, index) in job.recommendations"
            :key="product.productId"
            :product="product"
            :index="index"
            :demo="job.demo"
            @notify="notify"
          />
        </div>
        <div v-else class="empty-results">
          <Glasses :size="44" :stroke-width="1.2" />
          <h3>Doğru modeli bulmak için küçük bir değişiklik.</h3>
          <p>
            Bütçeni genişletebilir veya başka bir gözlük türü seçebilirsin. Doğrulayamadığımız
            ürünleri önermiyoruz.
          </p>
          <button class="button button-primary" @click="openUpload()">
            Tercihlerimi düzenle<ArrowRight :size="18" />
          </button>
        </div>
        <div class="results-footer">
          <p>
            Görsel stil önerisidir. Ölçü ve rahatlığı deneyerek kontrol et.<span
              v-if="!examples && job"
            >
              Sonuçlar
              {{
                new Date(job.expiresAt).toLocaleTimeString('tr-TR', {
                  hour: '2-digit',
                  minute: '2-digit',
                })
              }}
              saatine kadar erişilebilir.</span
            >
          </p>
          <button v-if="!examples" class="text-button" :disabled="deleting" @click="removeResult()">
            <Trash2 :size="15" />{{ deleting ? 'Siliniyor…' : 'Sonucu sil' }}
          </button>
        </div>
      </div>
    </section>

    <div class="manifesto-strip">
      <div class="wrap">
        <span>YÜZÜNE UYGUN.</span><span class="strip-star">✳</span><span>TARZINA YAKIN.</span
        ><span class="strip-star">✳</span><span>TAM SENLİK.</span><span class="strip-star">✳</span
        ><span>OPTİK & GÜNEŞ.</span>
      </div>
    </div>

    <section id="how-it-works" class="how-section wrap" aria-labelledby="how-heading">
      <div class="section-heading">
        <div>
          <span class="eyebrow">AZ UĞRAŞ, İYİ BİR SEÇİM.</span>
          <h2 id="how-heading">Üç adımda, <em>tam senlik.</em></h2>
        </div>
        <p>Kararsızlığı geride bırak.<br />Yeni favorinle tanış.</p>
      </div>
      <div class="steps-grid">
        <article>
          <div class="step-top"><Camera :size="30" :stroke-width="1.4" /><span>01</span></div>
          <h3>Kendini göster.</h3>
          <p>
            Yüzünün net göründüğü bir fotoğraf yükle. Gözlük türünü ve dilersen stil tercihlerini
            seç.
          </p>
        </article>
        <article>
          <div class="step-top"><ScanFace :size="30" :stroke-width="1.4" /><span>02</span></div>
          <h3>Bırak, biz keşfedelim.</h3>
          <p>
            Yapay zekâ yüz hatlarını değerlendirir. Tercihlerine uygun gerçek ürünleri senin için
            ararız.
          </p>
        </article>
        <article>
          <div class="step-top"><Glasses :size="32" :stroke-width="1.4" /><span>03</span></div>
          <h3>Yeni favorini bul.</h3>
          <p>
            Sana özel üç alternatifi karşılaştır. Beğendiğin modeli mağazada incele veya
            bağlantısını paylaş.
          </p>
        </article>
      </div>
    </section>

    <section id="style-guide" class="style-section wrap" aria-labelledby="style-heading">
      <div class="section-heading">
        <div>
          <span class="eyebrow">BİR ÇERÇEVEDEN FAZLASI.</span>
          <h2 id="style-heading">Senin stilin, <em>senin kuralların.</em></h2>
        </div>
        <button class="text-button discover-link" @click="openUpload()">
          Kendi eşleşmeni bul <ArrowUpRight :size="19" />
        </button>
      </div>
      <div class="style-grid">
        <button class="style-card style-classic" @click="openUpload('OPTICAL')">
          <div class="style-card-top"><span>01 / ZAMANSIZ</span><ArrowUpRight :size="24" /></div>
          <FrameArt shape="RECTANGULAR" color="#593e2b" />
          <div class="style-card-bottom">
            <h3>Her zaman sen.</h3>
            <span>Klasik çizgiler, güçlü bir karakter.</span>
          </div>
        </button>
        <button class="style-card style-minimal" @click="openUpload('OPTICAL')">
          <div class="style-card-top"><span>02 / MİNİMAL</span><ArrowUpRight :size="24" /></div>
          <FrameArt shape="ROUND" color="#73634d" />
          <div class="style-card-bottom">
            <h3>Az, ama öz.</h3>
            <span>Hafif detaylar, doğal bir duruş.</span>
          </div>
        </button>
        <button class="style-card style-bold" @click="openUpload('SUNGLASSES')">
          <div class="style-card-top"><span>03 / İDDİALI</span><ArrowUpRight :size="24" /></div>
          <FrameArt shape="CAT_EYE" color="#282a2d" />
          <div class="style-card-bottom">
            <h3>Farkın, bakışında.</h3>
            <span>Girdiğin her yerde iz bırak.</span>
          </div>
        </button>
      </div>
      <p class="style-caption">Çizimler stil rehberi içindir; belirli bir ürünü temsil etmez.</p>
    </section>

    <section class="privacy-section wrap">
      <div class="privacy-art">
        <ShieldCheck :size="46" :stroke-width="1" /><span
          >SENİN FOTOĞRAFIN.<br />SENİN KONTROLÜN.</span
        >
      </div>
      <div>
        <span class="eyebrow">GÜVENLE KEŞFET</span>
        <h2>Stilini tanırız.<br /><em>Fotoğrafını saklamayız.</em></h2>
        <p>
          Hesap açmana gerek yok. Fotoğrafın analiz sonrası silinir, sonuçların yalnızca geçici
          oturumunda kalır.
        </p>
        <button class="text-button" @click="privacy">
          Gizlilik hakkında daha fazla bilgi<ArrowUpRight :size="17" />
        </button>
      </div>
    </section>

    <section id="faq" class="faq-section wrap" aria-labelledby="faq-heading">
      <div>
        <span class="eyebrow">AKLINDA SORU KALMASIN.</span>
        <h2 id="faq-heading">Merak<br /><em>ettiklerin.</em></h2>
      </div>
      <div class="faq-list">
        <article v-for="(faq, i) in faqs" :key="faq.q" :class="{ expanded: selectedFaq === i }">
          <h3>
            <button
              :aria-expanded="selectedFaq === i"
              :aria-controls="`faq-${i}`"
              @click="selectedFaq = selectedFaq === i ? null : i"
            >
              {{ faq.q }}<Minus v-if="selectedFaq === i" :size="19" /><Plus v-else :size="19" />
            </button>
          </h3>
          <p v-if="selectedFaq === i" :id="`faq-${i}`">{{ faq.a }}</p>
        </article>
      </div>
    </section>

    <section class="closing-section wrap">
      <span class="eyebrow">YENİ BİR BAKIŞA HAZIR MISIN?</span>
      <h2>Gözlüğün değil,<br /><em>sen konuşul.</em></h2>
      <button class="button button-primary" @click="openUpload()">
        Kendine bir iyilik yap<ArrowUpRight :size="20" /></button
      ><span class="closing-note">Bir fotoğrafla başla. Kendini yeniden keşfet.</span>
    </section>
  </main>
  <footer class="site-footer wrap">
    <a href="#" class="wordmark">opti<span>fit</span><span class="brand-dot">.</span></a>
    <p>Bakışın kadar sana özel.</p>
    <button class="text-button" @click="privacy">Gizlilik</button
    ><span>© {{ new Date().getFullYear() }} OptiFit</span>
  </footer>
  <UploadDialog
    :open="dialogOpen"
    :busy="busy"
    :job="job"
    :error="error"
    :demo="demo"
    :initial-category="category"
    @close="dialogOpen = false"
    @submit="submit"
    @cancel="cancel"
  />
  <dialog
    v-if="privacyOpen"
    ref="privacyDialog"
    class="privacy-dialog"
    aria-labelledby="privacy-title"
    @cancel.prevent="closePrivacy"
  >
    <div class="dialog-top">
      <span class="eyebrow">GİZLİLİK</span
      ><button class="icon-button" aria-label="Gizlilik penceresini kapat" @click="closePrivacy">
        <X :size="22" />
      </button>
    </div>
    <h2 id="privacy-title">Kontrol sende.</h2>
    <p>
      Üyelik veya e-posta istemiyoruz. Yalnızca sonucu sana göstermek için gerekli geçici oturum
      çerezini kullanıyoruz.
    </p>
    <p>
      Canlı analizde fotoğrafın Google Gemini’ye iletilir. Uygulamamızdaki geçici kopyalar analiz
      aşaması bittiğinde, yarım kalan işlemlerde en geç bir saat içinde silinir. Google’ın saklama
      koşulları ayrıca geçerlidir.
    </p>
    <p>
      Sonuçlar analiz başlangıcından itibaren bir saat erişilebilir. “Sonucu sil” ile daha erken
      kaldırabilirsin. Fotoğrafın veya analiz bilgilerin ürün mağazalarına gönderilmez; paylaştığın
      bağlantılar yalnızca ürüne ya da mağazaya gider.
    </p>
    <p v-if="demo" class="demo-note">
      Şu an demo modundasın. Fotoğraflar Google Gemini’ye gönderilmiyor.
    </p>
    <a
      class="text-button"
      href="https://ai.google.dev/gemini-api/terms"
      target="_blank"
      rel="noopener noreferrer"
      >Google Gemini veri kullanım koşulları<ArrowUpRight :size="16"
    /></a>
  </dialog>
  <Transition name="toast"
    ><div v-if="toast" class="toast-message" role="status">
      <Check :size="18" />{{ toast
      }}<button class="icon-button" aria-label="Bildirimi kapat" @click="toast = ''">
        <X :size="16" />
      </button></div
  ></Transition>
</template>
