import { expect, test } from '@playwright/test'

const imageUrl =
  'https://stn-atasun.mncdn.com/Content/media/ProductImg/original/gu021637-rb-2140-901-5022150-638682294388171884.png'

test('product photos render with safe fallback for broken and missing images', async ({ page }) => {
  await page.route('**/api/v1/session', (route) =>
    route.fulfill({ json: { mode: 'live', csrfToken: 'test', csrfHeader: 'X-CSRF-TOKEN' } }),
  )
  await page.route('**/api/v1/examples', (route) =>
    route.fulfill({
      json: {
        jobId: 'example',
        status: 'COMPLETED',
        demo: false,
        warnings: [],
        recommendations: [imageUrl, 'https://stn-atasun.mncdn.com/missing-test.png', null].map(
          (image, i) => ({
            productId: String(i),
            brand: 'Ray-Ban',
            modelCode: 'RB2140',
            name: 'Wayfarer',
            category: 'SUNGLASSES',
            shape: 'RECTANGULAR',
            reasonCode: 'SHAPE_MATCH',
            attributes: {},
            imageUrl: image,
            offers: [
              {
                merchantName: 'Atasun Optik',
                productUrl:
                  'https://www.atasunoptik.com.tr/rayban-rb-x1-2140-901-5022-unisex-gunes-gozlukleri_78185',
                price: null,
                currency: 'TRY',
                availability: 'UNKNOWN',
                checkedAt: null,
              },
            ],
          }),
        ),
      },
    }),
  )
  if (!process.env.OPTIFIT_LIVE_IMAGES) {
    await page.route(imageUrl, (route) =>
      route.fulfill({
        contentType: 'image/svg+xml',
        body: '<svg xmlns="http://www.w3.org/2000/svg" width="600" height="240"><path d="M50 80h200v100H50zm300 0h200v100H350zM250 110h100" fill="none" stroke="black" stroke-width="15"/></svg>',
      }),
    )
  }
  await page.route('**/missing-test.png', (route) => route.fulfill({ status: 404, body: '' }))
  await page.goto('/')
  await page.getByRole('button', { name: 'Örnek sonuçları gör', exact: true }).click()
  const cards = page.locator('.product-card')
  await expect(cards).toHaveCount(3)
  const photo = cards.nth(0).locator('img.product-photo')
  await expect(photo).toBeVisible()
  await expect
    .poll(() => photo.evaluate((image: HTMLImageElement) => image.naturalWidth))
    .toBeGreaterThan(0)
  await expect(photo).toHaveAttribute('referrerpolicy', 'no-referrer')
  await expect(cards.nth(0).locator('.product-placeholder')).toHaveCount(0)
  await expect(cards.nth(1).locator('.product-placeholder')).toBeVisible()
  await expect(cards.nth(1).locator('img')).toHaveCount(0)
  await expect(cards.nth(2).locator('.product-placeholder')).toBeVisible()
  await page.screenshot({ path: 'test-results/product-images-desktop.png', fullPage: true })
  await page.setViewportSize({ width: 390, height: 844 })
  await expect(photo).toBeVisible()
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBeTruthy()
  await page.screenshot({ path: 'test-results/product-images-mobile.png', fullPage: true })
})
