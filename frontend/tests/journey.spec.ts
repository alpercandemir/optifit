import { expect, test } from '@playwright/test'
import path from 'node:path'

test('anonymous photo upload, reload, sharing links and deletion', async ({ page }) => {
  await page.goto('/')
  await expect(page.locator('.demo-banner')).toContainText('Demo deneyimi')
  await page.getByRole('button', { name: 'Fotoğrafını yükle', exact: true }).click()
  await page
    .getByLabel('Yüz fotoğrafını seç')
    .setInputFiles(path.resolve('public/images/editorial-portrait.jpg'))
  await page.getByRole('checkbox').check()
  await page.getByRole('button', { name: 'Örnek önerilerimi göster', exact: true }).click()
  await expect(page.locator('.product-card')).toHaveCount(3)
  await expect(page.getByRole('dialog')).not.toBeVisible()
  await page.reload()
  await expect(page.locator('.product-card')).toHaveCount(3)
  for (const link of await page.getByRole('link', { name: 'Ürünü incele', exact: true }).all())
    expect(await link.getAttribute('href')).toMatch(/^https:\/\/www\.atasunoptik\.com\.tr\//)
  await page.getByRole('button', { name: 'Sonucu sil', exact: true }).click()
  await expect(page.locator('.product-card')).toHaveCount(0)
  await page.reload()
  await expect(page.locator('.product-card')).toHaveCount(0)
})
test('optical demo reports no match instead of substituting sunglasses', async ({ page }) => {
  await page.goto('/')
  await page.getByRole('button', { name: 'Fotoğrafını yükle', exact: true }).click()
  await page.getByRole('radio', { name: 'Optik çerçeve', exact: true }).check()
  await page
    .getByLabel('Yüz fotoğrafını seç')
    .setInputFiles(path.resolve('public/images/editorial-portrait.jpg'))
  await page.getByRole('checkbox').check()
  await page.getByRole('button', { name: 'Örnek önerilerimi göster', exact: true }).click()
  await expect(page.getByRole('heading', { name: 'Biraz daha keşfedelim.' })).toBeVisible()
  await expect(page.locator('.product-card')).toHaveCount(0)
})
for (const width of [360, 390, 768, 1440]) {
  test(`responsive landing and results at ${width}px`, async ({ page }) => {
    await page.setViewportSize({ width, height: 900 })
    await page.goto('/')
    await expect(page.getByRole('heading', { level: 1 })).toBeVisible()
    expect(
      await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth),
    ).toBeTruthy()
    await page.getByRole('button', { name: 'Örnek sonuçları gör', exact: true }).click()
    await expect(page.locator('.product-card')).toHaveCount(3)
    expect(
      await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth),
    ).toBeTruthy()
    await page.screenshot({ path: `test-results/optifit-${width}.png`, fullPage: true })
  })
}
