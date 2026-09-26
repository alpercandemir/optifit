<script setup lang="ts">
withDefaults(defineProps<{ shape?: string; color?: string }>(), {
  shape: 'RECTANGULAR',
  color: 'currentColor',
})
</script>
<template>
  <svg viewBox="0 0 300 120" fill="none" aria-hidden="true" class="frame-art" :style="{ color }">
    <g stroke="currentColor" stroke-linecap="round" stroke-linejoin="round">
      <template v-if="shape === 'ROUND'">
        <circle cx="81" cy="64" r="43" stroke-width="6" />
        <circle cx="220" cy="64" r="43" stroke-width="6" />
        <path d="M125 57 Q150 35 176 57 M38 55 16 46M263 55l22-9" stroke-width="5" />
      </template>
      <template v-else-if="shape === 'CAT_EYE'">
        <path
          d="M24 23q50-10 107 18l-7 34q-10 39-53 21Q45 85 24 23ZM276 23q-50-10-107 18l7 34q10 39 53 21Q255 85 276 23Z"
          stroke-width="8"
        />
        <path d="M131 46q19-14 38 0" stroke-width="6" />
      </template>
      <template v-else-if="shape === 'AVIATOR'">
        <path
          d="M34 33q40-18 93 2l-7 39q-8 46-46 28Q26 78 34 33ZM266 33q-40-18-93 2l7 39q8 46 46 28Q274 78 266 33Z"
          stroke-width="5"
        />
        <path d="M129 39q21-13 42 0M129 23h42M34 37l-18-6m250 6 18-6" stroke-width="4" />
      </template>
      <template v-else>
        <path
          d="M28 31q42-14 104 0l-5 43q-3 22-26 24H64Q40 95 36 74ZM272 31q-42-14-104 0l5 43q3 22 26 24h37q24-3 28-24Z"
          stroke-width="9"
        />
        <path d="M132 40q18-12 36 0M29 36H15m256 0h14" stroke-width="7" />
        <path d="M47 37h7m192 0h7" stroke="#eee9dd" stroke-width="2" />
      </template>
    </g>
  </svg>
</template>
