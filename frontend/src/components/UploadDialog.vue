<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import {
  X,
  Upload,
  ArrowRight,
  ImagePlus,
  Check,
  ShieldCheck,
  SlidersHorizontal,
  LoaderCircle,
  ScanFace,
  Search,
  Sparkles,
  Info,
} from 'lucide-vue-next'
import FrameArt from './FrameArt.vue'
import type { Job, Preferences } from '../lib/types'
const props = defineProps<{
  open: boolean
  busy: boolean
  job: Job | null
  error: string
  demo: boolean
  initialCategory: 'OPTICAL' | 'SUNGLASSES'
}>()
const emit = defineEmits<{ close: []; submit: [file: File, prefs: Preferences]; cancel: [] }>()
const dialog = ref<HTMLDialogElement>()
const input = ref<HTMLInputElement>()
const file = ref<File | null>(null)
const preview = ref('')
const localError = ref('')
const dragging = ref(false)
const preferences = ref<Preferences>({
  category: props.initialCategory,
  budget: '',
  style: 'ANY',
  color: 'ANY',
  consent: false,
})
const stage = computed(() =>
  props.job?.status === 'SEARCHING' ? 2 : props.job?.status === 'ANALYZING' ? 1 : 0,
)
watch(
  () => props.open,
  (open) => {
    if (open) {
      preferences.value.category = props.initialCategory
      dialog.value?.showModal()
    } else {
      dialog.value?.close()
      clearFile()
    }
  },
)
function clearFile() {
  if (preview.value) URL.revokeObjectURL(preview.value)
  preview.value = ''
  file.value = null
  if (input.value) input.value.value = ''
}
function selectFile(selected?: File) {
  localError.value = ''
  if (!selected) return
  if (!['image/jpeg', 'image/png', 'image/webp'].includes(selected.type)) {
    localError.value = 'JPEG, PNG veya WebP formatında bir fotoğraf seçin.'
    return
  }
  if (selected.size > 10 * 1024 * 1024) {
    localError.value = 'Fotoğrafınız 10 MB sınırını aşıyor.'
    return
  }
  clearFile()
  file.value = selected
  preview.value = URL.createObjectURL(selected)
}
function dropped(event: DragEvent) {
  dragging.value = false
  selectFile(event.dataTransfer?.files[0])
}
function submit() {
  if (!file.value) {
    localError.value = 'Devam etmek için bir fotoğraf seçin.'
    return
  }
  if (!preferences.value.consent) {
    localError.value = 'Fotoğraf işleme bilgilendirmesini onaylayın.'
    return
  }
  emit('submit', file.value, { ...preferences.value })
}
onBeforeUnmount(clearFile)
</script>
<template>
  <dialog
    ref="dialog"
    class="upload-dialog"
    aria-labelledby="upload-title"
    @cancel.prevent="busy ? emit('cancel') : emit('close')"
    @click="
      (event) => {
        if (event.target === dialog && !busy) emit('close')
      }
    "
  >
    <div class="dialog-inner">
      <div class="dialog-top">
        <span class="eyebrow">KENDİ BAKIŞINI KEŞFET</span
        ><button
          class="icon-button"
          :aria-label="busy ? 'Analizi iptal et' : 'Pencereyi kapat'"
          @click="busy ? emit('cancel') : emit('close')"
        >
          <X :size="22" />
        </button>
      </div>
      <template v-if="busy">
        <h2 id="upload-title">Sana yakışanları<br /><em>arıyoruz.</em></h2>
        <p class="dialog-description">
          {{
            demo
              ? 'Örnek deneyim hazırlanıyor. Fotoğraf analizi yapılmıyor.'
              : 'Yüz hatlarını ve tercihlerini birlikte değerlendiriyoruz.'
          }}
        </p>
        <div class="analysis-visual">
          <img v-if="preview" :src="preview" alt="Yüklediğiniz fotoğrafın önizlemesi" />
          <div v-else class="analysis-placeholder"><ScanFace :size="64" /></div>
          <span class="analysis-spark"><Sparkles :size="24" /></span>
        </div>
        <ol class="analysis-steps" aria-live="polite">
          <li
            v-for="(label, i) in [
              'Fotoğraf kontrol ediliyor',
              'Görsel stil değerlendiriliyor',
              'Gözlük modelleri aranıyor',
            ]"
            :key="label"
            :class="{ active: i === stage, done: i < stage }"
          >
            <Check v-if="i < stage" :size="18" /><LoaderCircle
              v-else-if="i === stage"
              class="spin"
              :size="18"
            /><span v-else class="step-dot" />{{ label }}
          </li>
        </ol>
        <p class="privacy-note">
          <ShieldCheck :size="16" />Fotoğrafın analiz aşaması tamamlandığında silinir.
        </p>
        <button class="text-button cancel-analysis" @click="emit('cancel')">
          Analizi iptal et ve sil
        </button>
      </template>
      <form v-else @submit.prevent="submit">
        <h2 id="upload-title">Her şey bir<br /><em>fotoğrafla başlar.</em></h2>
        <p class="dialog-description">Önden çekilmiş, yüzünün net göründüğü bir fotoğraf seç.</p>
        <p v-if="demo" class="demo-note">
          <Info :size="17" />Demo modu açık. Fotoğrafın yapay zekâya gönderilmez; örnek sonuçlar
          gösterilir.
        </p>
        <fieldset class="category-options">
          <legend>Ne arıyorsun?</legend>
          <label :class="{ selected: preferences.category === 'OPTICAL' }"
            ><input
              v-model="preferences.category"
              type="radio"
              value="OPTICAL"
              name="category" /><FrameArt /><span>Optik çerçeve</span
            ><Check v-if="preferences.category === 'OPTICAL'" :size="16" /></label
          ><label :class="{ selected: preferences.category === 'SUNGLASSES' }"
            ><input
              v-model="preferences.category"
              type="radio"
              value="SUNGLASSES"
              name="category" /><FrameArt shape="AVIATOR" /><span>Güneş gözlüğü</span
            ><Check v-if="preferences.category === 'SUNGLASSES'" :size="16"
          /></label>
        </fieldset>
        <div
          class="dropzone"
          :class="{ dragging, 'has-photo': file }"
          @dragover.prevent="dragging = true"
          @dragleave.prevent="dragging = false"
          @drop.prevent="dropped"
        >
          <img v-if="preview" :src="preview" alt="Seçilen fotoğraf" class="photo-preview" />
          <template v-else
            ><div class="upload-symbol"><ImagePlus :size="27" :stroke-width="1.5" /></div>
            <strong>Fotoğrafını buraya bırak</strong><span>ya da cihazından seç</span></template
          >
          <input
            ref="input"
            type="file"
            accept="image/jpeg,image/png,image/webp"
            aria-label="Yüz fotoğrafını seç"
            @change="selectFile(($event.target as HTMLInputElement).files?.[0])"
          />
          <button type="button" class="button button-small upload-choose" @click="input?.click()">
            <Upload :size="16" />{{ file ? 'Fotoğrafı değiştir' : 'Fotoğraf seç' }}
          </button>
          <span v-if="!file" class="file-types">JPG, PNG, WEBP · En fazla 10 MB</span>
        </div>
        <div class="photo-tips">
          <span><Check :size="14" />Tek kişi</span><span><Check :size="14" />İyi ışık</span
          ><span><Check :size="14" />Yüzün açıkta</span>
        </div>
        <details class="preferences">
          <summary>
            <SlidersHorizontal :size="17" />Biraz da tarzından bahset <span>İsteğe bağlı</span>
          </summary>
          <div class="preference-fields">
            <label
              >Bütçe üst sınırı (TL)<input
                v-model="preferences.budget"
                type="number"
                min="1"
                max="1000000"
                step="1"
                placeholder="Sınır yok" /></label
            ><label
              >Stilin<select v-model="preferences.style">
                <option value="ANY">Fark etmez</option>
                <option value="CLASSIC">Zamansız / klasik</option>
                <option value="MODERN">Modern / minimal</option>
                <option value="BOLD">İddialı</option>
              </select></label
            ><label
              >Çerçeve rengi<select v-model="preferences.color">
                <option value="ANY">Fark etmez</option>
                <option value="BLACK">Siyah</option>
                <option value="BROWN">Kahverengi / havana</option>
                <option value="GOLD">Altın</option>
                <option value="CLEAR">Şeffaf</option>
              </select></label
            >
          </div>
        </details>
        <label class="consent"
          ><input v-model="preferences.consent" type="checkbox" /><span
            >{{
              demo
                ? 'Demo deneyimini ve geçici fotoğraf işlemeyi kabul ediyorum.'
                : 'Fotoğrafımın gözlük önerisi için Google Gemini’ye gönderilmesini kabul ediyorum.'
            }}
            Fotoğraf analiz sonrası silinir; sonuçlar bir saat erişilebilir. Canlı hizmette
            sağlayıcının saklama koşulları ayrıca geçerlidir.</span
          ></label
        >
        <p v-if="localError || error" class="form-error" role="alert">{{ localError || error }}</p>
        <button class="button button-primary submit-analysis" type="submit">
          <Sparkles :size="18" />{{ demo ? 'Örnek önerilerimi göster' : 'Bana uygun gözlükleri bul'
          }}<ArrowRight :size="19" />
        </button>
        <p class="privacy-note">
          <ShieldCheck :size="15" />Ücretsiz. Üyelik gerekmez. Fotoğrafın kalıcı saklanmaz.
        </p>
      </form>
    </div>
  </dialog>
</template>
