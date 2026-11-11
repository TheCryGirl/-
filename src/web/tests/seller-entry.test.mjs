import test from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import vm from 'node:vm'

// Execute the real SFC script with boundary stubs, not a duplicate seller-policy implementation.
function header(loggedIn, seller) {
  const source = readFileSync(new URL('../src/components/AppHeader.vue', import.meta.url), 'utf8')
  const script = source.match(/<script setup>([\s\S]*?)<\/script>/)[1]
    .replace(/^import .* from .*$/gm, '')
  const routes=[], notices=[], calls=[], redirects=[]
  const context={
    ref:v=>({value:v}),computed:fn=>({get value(){return fn()}}),watch:()=>{},onMounted:()=>{},onBeforeUnmount:()=>{},
    useRoute:()=>({query:{},path:'/products',fullPath:'/products'}),useRouter:()=>({push:r=>routes.push(r)}),
    auth:{user:{role:seller?'SELLER':'BUYER'}},isAdmin:{value:false},isLoggedIn:{value:loggedIn},isSeller:{value:seller},
    logout:()=>{},setLogin:()=>calls.push('setLogin'),
    showInfo:m=>notices.push(m),showError:m=>notices.push(m),showSuccess:m=>notices.push(m),resolveErrorMessage:e=>e.message,
    userApi:{activateSeller:async()=>{calls.push('activateSeller');return {data:{code:0,data:{token:'fake',user:{}}}}}},
    cartApi:{},productApi:{},unwrapResult:p=>p.data,hotKeywords:[],
    sessionStorage:{setItem:(k,v)=>redirects.push([k,v])},
  }
  vm.createContext(context);vm.runInContext(script+'\nthis.entry=goSell;',context)
  return {run:()=>context.entry(),routes,notices,calls,redirects}
}
test('BUYER asks administrator instead of changing role or calling self-activation',async()=>{
  const h=header(true,false);await h.run();assert.equal(h.calls.length,0);assert.equal(h.routes.length,0)
  assert.match(h.notices.join(' '),/管理员/)
})
test('existing seller opens workbench without role mutation',async()=>{
  const h=header(true,true);await h.run();assert.equal(h.routes[0].name,'seller-products');assert.equal(h.calls.length,0)
})
test('anonymous entry goes to login without changing any role',async()=>{
  const h=header(false,false);await h.run();assert.equal(h.routes[0].name,'login');assert.equal(h.calls.length,0)
})
