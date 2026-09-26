# Visual assets

The editorial hero was created with the **built-in imagegen tool**. It is an original illustrative fashion portrait, not a user photo or an image of one of the linked products. The interface labels it as AI-generated editorial imagery.

Saved assets:

- `frontend/public/images/editorial-portrait.png` — generated source.
- `frontend/public/images/editorial-portrait.jpg` — optimized web image, 1400 px maximum dimension.
- `frontend/src/components/FrameArt.vue` — original SVG style illustrations; explicitly not product photographs.
- `frontend/public/favicon.svg` — original OptiFit mark.
- DM Sans Variable and Manrope Variable — self-hosted through `@fontsource-variable`; license files are included in those packages.
- Lucide icons — package-provided SVG icons.

Exact generation prompt:

> Use case: photorealistic-natural. Asset type: OptiFit eyewear website editorial hero, portrait 4:5. High-end fashion editorial photograph of a stylish adult woman with short dark wavy hair wearing bold translucent amber tortoiseshell optical eyeglasses with clear lenses and a cream tailored blazer. Warm natural sunlight creates a crisp shadow on a pale warm beige studio wall. Head and shoulders, relaxed confident expression, head slightly turned toward camera, beautiful natural skin texture, elegant restrained European eyewear campaign art direction. Face in upper middle with generous space around the head, blazer lower third. Palette warm sand, ivory, amber, dark brown. Realistic optical glasses geometry, eyes visible through clear lenses. No text, no branding, no watermark, no UI, no face scanning graphics.

## Demo product photographs

The demo catalogue references external product images directly from Atasun Optik's Product JSON-LD, checked on 2026-09-26. Image binaries are not bundled with the app. The README results screenshot captures these images in the running demo. Photography belongs to the respective rights holders; this does not imply a partnership or a license grant.

| Demo model | Source product page |
| --- | --- |
| RB2140 Wayfarer Tortoise | https://www.atasunoptik.com.tr/ray-ban-rb-x1-2140-902-5022150-erkek-gunes-gozlukleri_81552 |
| RB4171 Erika Classic | https://www.atasunoptik.com.tr/rayban-rb-4171-6228g-5418-unisex-gunes-gozlukleri_78158 |
| RBR0103S Round Reverse | https://www.atasunoptik.com.tr/ray-ban-rb-0rbr0103s-001vr-5321140-unisex-gunes-gozlukleri_83922 |

External image URLs can change; failed loads retain the existing placeholder. Demo photos do not establish current prices, stock, or personalized analysis.
