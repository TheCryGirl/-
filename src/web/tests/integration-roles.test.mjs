import test from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import vm from 'node:vm'
import { canManageProducts, canAccessRoleRoute } from '../src/utils/roleAccess.js'

function router(role, loggedIn = true) {
  const script = readFileSync(new URL('../src/router/index.js', import.meta.url),'utf8')
    .replace(/^import .* from .*$/gm,'').replace(/^export default router.*$/gm,'')
  let guard, config
  const context={canAccessRoleRoute,isLoggedIn:{value:loggedIn},currentRole:{value:role},
    createWebHistory:()=>({}),createRouter:c=>{config=c;return {beforeEach:g=>guard=g}},
    document:{title:''},sessionStorage:{setItem:()=>{}}}
  vm.createContext(context);vm.runInContext(script,context)
  return {guard,config}
}

test('role matrix mirrors seller-only product writes and admin-only operations',()=>{
  for(const role of ['BUYER','ADMIN','GUEST',''])assert.equal(canManageProducts(role),false)
  assert.equal(canManageProducts('SELLER'),true)
  const meta={role:'SELLER',requiresAuth:true,title:'seller'}
  assert.equal(router('ADMIN').guard({meta,fullPath:'/seller/products'}).name,'products')
  assert.equal(router('BUYER').guard({meta,fullPath:'/seller/products'}).name,'products')
  assert.equal(router('SELLER').guard({meta,fullPath:'/seller/products'}),true)
  assert.equal(router('ADMIN').guard({meta:{role:'ADMIN',requiresAuth:true},fullPath:'/admin/users'}),true)
  assert.equal(router('SELLER').guard({meta:{role:'ADMIN',requiresAuth:true},fullPath:'/admin/users'}).name,'products')
})
test('merged router retains search and exactly one admin-shops registration',()=>{
  const routes=router('BUYER').config.routes
  assert.equal(routes.filter(r=>r.name==='admin-shops').length,1)
  assert.equal(routes.find(r=>r.name==='search').path,'/search')
  assert.equal(router('SELLER',false).guard({meta:{role:'SELLER',requiresAuth:true},fullPath:'/seller/products'}).name,'login')
})

test('expired or revoked token clears client session and dispatches auth-expired once',async()=>{
  const script=readFileSync(new URL('../src/api/index.js',import.meta.url),'utf8')
    .replace(/^import .* from .*$/gm,'').replace(/export (const|function) /g,'$1 ').replace(/^export .*$/gm,'')
  let reject
  const removed=[],events=[]
  const http={interceptors:{request:{use:()=>{}},response:{use:(_,fn)=>reject=fn}}}
  const context={axios:{create:()=>http},localStorage:{getItem:()=>null,removeItem:k=>removed.push(k)},
    window:{dispatchEvent:e=>events.push(e.type)},Event:class{constructor(type){this.type=type}}}
  vm.createContext(context);vm.runInContext(script,context)
  await assert.rejects(reject({response:{status:401}}))
  assert.deepEqual(removed,['token','user']);assert.deepEqual(events,['auth:expired'])
})

test('shipment receiver displays the server masked snapshot in the new layout',()=>{
  const script=readFileSync(new URL('../src/views/seller/SellerShipments.vue',import.meta.url),'utf8')
    .match(/<script setup>([\s\S]*?)<\/script>/)[1].replace(/^import .* from .*$/gm,'')
  const ctx={ref:v=>({value:v}),reactive:v=>v,computed:f=>({get value(){return f()}}),onMounted:()=>{}}
  vm.createContext(ctx);vm.runInContext(script+'\nthis.receiver=receiverText;',ctx)
  assert.equal(ctx.receiver({addressSnapshot:{receiver:'收货人',phone:'138****8888',fullAddress:null}}),'收货人 138****8888')
  assert.equal(ctx.receiver({}),'—')
})
