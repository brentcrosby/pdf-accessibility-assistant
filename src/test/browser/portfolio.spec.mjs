import {test,expect} from '@playwright/test';
import AxeBuilder from '@axe-core/playwright';
async function accessible(page){const result=await new AxeBuilder({page}).withTags(['wcag2a','wcag2aa','wcag21aa']).analyze();expect(result.violations).toEqual([]);}
async function demo(page,name='tagged'){
    await page.goto('/');await page.getByRole('button',{name:`Load ${name} demo`,exact:true}).click();
    await expect(page.locator('#semantic-status')).toContainText('text proposals');await expect(page.locator('#page-status')).toContainText('observations');
}
test('combined repairs export, retain evidence, and reopen with verified alt and order',async({page})=>{
    const errors=[];page.on('pageerror',error=>errors.push(error.message));await demo(page);await accessible(page);
    await page.locator('#figure-alt').fill('Participation increased from 30 to 48 to 70 people.');
    await page.getByRole('button',{name:'Queue alternative text',exact:true}).click();
    const up=page.getByRole('button',{name:/Move H1 .* up/});await up.focus();await page.keyboard.press('Enter');
    await expect(page.locator('#order-list li').first()).toContainText('H1');await expect(page.locator('#order-list button:focus')).toHaveCount(1);
    await page.getByRole('button',{name:'Queue reading order',exact:true}).click();
    await page.getByRole('button',{name:'Remove Figure alternative text',exact:true}).click();
    await page.getByRole('button',{name:'Undo queue change',exact:true}).click();
    await expect(page.locator('#repair-count')).toContainText('2 / 25');await accessible(page);
    await page.getByRole('button',{name:'Verify and export queued repairs',exact:true}).click();
    await expect(page.locator('#repair-status')).toContainText('Verified 2 repairs');
    await expect(page.getByRole('link',{name:'Download verified PDF',exact:true})).toBeFocused();
    const downloadPromise=page.waitForEvent('download');await page.getByRole('link',{name:'Download transaction record'}).click();
    const download=await downloadPromise;expect(download.suggestedFilename()).toMatch(/record.json$/);
    await accessible(page);
    await page.getByRole('button',{name:'Continue reviewing this export',exact:true}).click();
    await expect(page.locator('#figure-alt')).toHaveValue('Participation increased from 30 to 48 to 70 people.');
    await expect(page.locator('#order-list li').first()).toContainText('H1');await expect(page.locator('#repair-count')).toContainText('0 / 25');
    expect(errors).toEqual([]);
});
test('accepted proposals create tags, semantic filter locates content, benchmark exposes errors',async({page})=>{
    await demo(page,'untagged');const buttons=page.getByRole('button',{name:'Accept and queue tag',exact:true});await expect(buttons).toHaveCount(7);
    for(let i=0;i<7;i++)await buttons.nth(i).click();await page.getByRole('button',{name:'Verify and export queued repairs',exact:true}).click();
    await expect(page.locator('#repair-status')).toContainText('Verified 7 repairs');
    await page.getByRole('button',{name:'Continue reviewing this export',exact:true}).click();await expect(buttons).toHaveCount(0);
    await page.locator('#semantic-filter').selectOption('TAGGED');await expect(page.locator('#region-list [data-region]')).toHaveCount(7);
    await page.getByRole('button',{name:'Run benchmark',exact:true}).click();await expect(page.locator('#benchmark-status')).toContainText('of 14 expected roles matched');
    await expect(page.locator('#benchmark-results')).toContainText('Paragraph-only baseline');await expect(page.locator('#benchmark-results')).toContainText('expected H2');await accessible(page);
});
test('mobile high contrast keyboard controls, empty alt refusal and source isolation',async({page})=>{
    await page.setViewportSize({width:390,height:844});await page.emulateMedia({forcedColors:'active',reducedMotion:'reduce'});await demo(page);
    await page.getByRole('button',{name:'Queue alternative text',exact:true}).click();await expect(page.locator('#repair-status')).toContainText('Enter meaningful');
    await page.locator('#figure-alt').fill('Reviewed chart.');await page.getByRole('button',{name:'Queue alternative text',exact:true}).click();
    await page.getByRole('button',{name:'Load untagged demo',exact:true}).click();await expect(page.locator('#repair-count')).toContainText('0 / 25');
    await expect(page.locator('#semantic-status')).toContainText('7 text proposals');await accessible(page);
    expect(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth)).toBe(true);
});
test('idle page has accessible labels and a working keyboard skip link',async({page})=>{
    await page.goto('/');await page.keyboard.press('Tab');await expect(page.getByRole('link',{name:'Skip to workbench'})).toBeFocused();
    await page.keyboard.press('Enter');await accessible(page);
});
test('reviewed path joins metadata in a verified transaction',async({page})=>{
    await demo(page);await page.locator('#kind').selectOption('PATH');await page.locator('#region-list [data-region]').click();
    await expect(page.getByRole('button',{name:'Queue artifact repair',exact:true})).toBeDisabled();
    await page.locator('#confirm-queued-artifact').check();await page.getByRole('button',{name:'Queue artifact repair',exact:true}).click();
    await page.locator('#transaction-language').fill('en-US');await page.getByRole('button',{name:'Queue language',exact:true}).click();
    await page.getByRole('button',{name:'Verify and export queued repairs',exact:true}).click();await expect(page.locator('#repair-status')).toContainText('Verified 2 repairs');
    await page.getByRole('button',{name:'Continue reviewing this export',exact:true}).click();await expect(page.locator('#document-summary')).toContainText('Language: en-US');
    await page.locator('#kind').selectOption('PATH');await page.locator('#semantic-filter').selectOption('ARTIFACT');await expect(page.locator('#region-list [data-region]')).toHaveCount(1);
});
test('failed verification keeps queue and offers no partial export',async({page})=>{
    await demo(page);await page.locator('#figure-alt').fill('Reviewed chart.');await page.getByRole('button',{name:'Queue alternative text',exact:true}).click();
    await page.route('**/repair-transactions',route=>route.fulfill({status:422,contentType:'application/json',body:JSON.stringify({message:'Verification failed. No copy was returned.'})}));
    await page.getByRole('button',{name:'Verify and export queued repairs',exact:true}).click();await expect(page.locator('#repair-status')).toContainText('No copy was returned');
    await expect(page.locator('#repair-count')).toContainText('1 / 25');await expect(page.locator('#transaction-history')).toBeHidden();await accessible(page);
});
