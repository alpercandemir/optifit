<script setup lang="ts">
import { attributeText, messageText, recommendationText } from '../ui/messages'
import { computed, ref, watch } from 'vue'
import { ArrowUpRight, Share2, Store, Check, Glasses } from 'lucide-vue-next'
import type { Recommendation } from '../lib/types'
import { safeLink, shareLink } from '../lib/api'
const props = defineProps<{ product: Recommendation; index: number; demo: boolean }>()
const emit = defineEmits<{ notify: [message: string] }>()
const offer = computed(() => props.product.offers[0]!)
const copied = ref('')
const imageFailed = ref(false)
const imageUrl = computed(() => safeLink(props.product.imageUrl ?? ''))
watch(imageUrl, () => (imageFailed.value = false))
const price = computed(() =>
  offer.value.price == null
    ? null
    : new Intl.NumberFormat('tr-TR', {
        style: 'currency',
        currency: offer.value.currency || 'TRY',
        maximumFractionDigits: 0,
      }).format(offer.value.price),
)
async function share() {
  try {
    const result = await shareLink(
      `${props.product.brand} ${props.product.name}`,
      offer.value.productUrl,
    )
    if (result === 'copied') {
      copied.value = 'product'
      emit('notify', 'Bağlantı kopyalandı.')
      setTimeout(() => (copied.value = ''), 2500)
    }
  } catch (error) {
    emit('notify', error instanceof Error ? messageText(error.message) : 'Paylaşım tamamlanamadı.')
  }
}
</script>
<template>
  <article class="product-card">
    <div class="product-image" :class="`tone-${index % 3}`">
      <span class="product-index">0{{ index + 1 }}</span
      ><span class="product-kind">{{
        product.category === 'OPTICAL' ? 'OPTİK ÇERÇEVE' : 'GÜNEŞ GÖZLÜĞÜ'
      }}</span>
      <img
        v-if="imageUrl && !imageFailed"
        class="product-photo"
        :src="imageUrl"
        :alt="`${product.brand} ${product.modelCode} ürün görseli`"
        loading="lazy"
        decoding="async"
        referrerpolicy="no-referrer"
        @error="imageFailed = true"
      />
      <div v-else class="product-placeholder">
        <Glasses :size="74" :stroke-width="1" /><span>Ürün görseli mağazada</span>
      </div>
      <span v-if="demo" class="sample-label">Örnek ürün</span>
    </div>
    <div class="product-body">
      <div class="product-brand">
        {{ product.brand }} <span>{{ product.modelCode }}</span>
      </div>
      <h3>{{ product.name }}</h3>
      <div class="attribute-row">
        <span v-for="(value, key) in product.attributes" :key="key">{{
          attributeText(key, value, product.shape)
        }}</span>
      </div>
      <p class="product-reason">{{ recommendationText(product) }}</p>
      <div class="price-row">
        <strong>{{ price || 'Fiyatı mağazada gör' }}</strong
        ><span v-if="offer.availability === 'IN_STOCK'">Stokta</span>
      </div>
      <p v-if="offer.checkedAt" class="checked-time">
        Kontrol: {{ new Date(offer.checkedAt).toLocaleString('tr-TR') }}
      </p>
      <p v-else class="checked-time">Güncel fiyat ve stok mağazada doğrulanmalıdır.</p>
      <a
        class="button product-visit"
        :href="safeLink(offer.productUrl)"
        target="_blank"
        rel="noopener noreferrer"
        >Ürünü incele <ArrowUpRight :size="18"
      /></a>
      <div class="merchant-row">
        <a :href="safeLink(offer.productUrl)" target="_blank" rel="noopener noreferrer"
          ><Store :size="16" />{{ offer.merchantName }}</a
        ><button
          class="icon-button"
          :aria-label="`${product.name} ürün bağlantısını paylaş`"
          @click="share()"
        >
          <Check v-if="copied === 'product'" :size="18" /><Share2 v-else :size="18" />
        </button>
      </div>
    </div>
  </article>
</template>
