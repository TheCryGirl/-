import test from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import vm from 'node:vm'
import { buildSummary, summaryRows } from '../src/utils/pricing.js'

const response = () => ({ data: { code: 0, data: { payAmount: 499, shops: [{items: [{skuId:'SKU', price:399, productTitle:'秒杀商品', quantity:1}]}] } } })
function harness() {
  const calls = [], navigations = []
  let next = response
  const router = { push: (value) => navigations.push(value), replace: (value) => navigations.push(value) }
  const context = vm.createContext({
    ref: (value) => ({value}), computed: (get) => ({get value(){return get()}}), onMounted: () => {}, watch: () => {},
    useRouter: () => router, useRoute: () => ({query:{flashSaleId:'FS-145'}}),
    auth: {user:{userId:'BUYER'}}, addressApi:{list:async()=>({data:{code:0,data:[{addressId:'ADDR',isDefault:true}]}})},
    orderApi: {preview: async (data)=>{calls.push(['preview',data]); return next()}, create:async (data,key)=>{
      calls.push(['create',data,key]); return {data:{code:0,data:{orderId:'ORDER'}}}
    }},
    sessionStorage: {getItem:()=>null,removeItem:()=>{throw new Error('must not clear cart')}},
    isOk:(value)=>value?.code===0, unwrapResult:(value)=>value.data,
    showError:()=>{},showWarning:()=>{},showSuccess:()=>{},resolveErrorMessage:(e)=>e.message,
    buildSummary,summaryRows,createIdempotencyKey:()=> 'fixture-key',
  })
  const script=readFileSync(new URL('../src/views/OrderConfirm.vue',import.meta.url),'utf8')
    .match(/<script setup>([\s\S]*?)<\/script>/)[1].replace(/^import .*$/gm,'')
  vm.runInContext(`${script}\nthis.api={load,submit,loadPreview,selectedAddressId}`,context)
  return { ...context.api, calls, navigations, setPreview:(fn)=>{next=fn} }
}
const plain = (value) => JSON.parse(JSON.stringify(value))

test('flash checkout previews without cart and submits the exact displayed quote to formal payment', async () => {
  const h = harness()
  await h.load()
  await h.submit()
  assert.deepEqual(plain(h.calls),[
    ['preview',{flashSaleId:'FS-145',quantity:1,addressId:'ADDR'}],
    ['create',{flashSaleId:'FS-145',quantity:1,addressId:'ADDR',expectedUnitPrice:399,expectedPayAmount:499},'fixture-key'],
  ])
  assert.deepEqual(plain(h.navigations),[{name:'order-pay',params:{id:'ORDER'}}])
})

test('flash checkout never submits an absent quote or a quote for a different address', async () => {
  const h = harness()
  h.setPreview(()=>({data:{code:30008,message:'已售罄'}}))
  await h.load(); await h.submit()
  assert.equal(h.calls.filter(([type])=>type==='create').length,0)
  h.setPreview(response); await h.loadPreview()
  h.selectedAddressId.value='OTHER'; await h.submit()
  assert.equal(h.calls.filter(([type])=>type==='create').length,0)
})
