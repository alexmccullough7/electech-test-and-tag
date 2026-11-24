// Add to existing Apps Script project. Rename original uploadTests to uploadTestsBase.
function uploadTests(records) {
  const result = uploadTestsBase(records);
  rebuildRegistersUnlocked();
  return result;
}
function rebuildRegisters() {
  const lock = LockService.getScriptLock();
  lock.waitLock(30000);
  try { rebuildRegistersUnlocked(); } finally { lock.releaseLock(); }
}
function rebuildRegistersUnlocked() {
  const ss = SpreadsheetApp.openById(DATABASE_ID);
  const norm = s => String(s || "").trim().toLowerCase();
  const key = (...parts) => JSON.stringify(parts.map(norm));
  function upsert(name, identify, incoming, merge) {
    const sheet = ss.getSheetByName(name);
    const rows = readTable(name);
    const map = new Map(rows.map((r,i)=>[identify(r),{r,i:i+2}]));
    for (const item of incoming) {
      const k = identify(item), old = map.get(k);
      const r = old ? merge(old.r,item) : item;
      const values = TABLES[name].map(h=>safeText(r[h]));
      if(old) sheet.getRange(old.i,1,1,values.length).setValues([values]);
      else { sheet.appendRow(values); map.set(k,{r,i:sheet.getLastRow()}); }
      if(old) old.r=r;
    }
  }
  const tests = readTable("TEST_RESULTS").sort((a,b)=>(a.CREATED_AT||a.TEST_DATE).localeCompare(b.CREATED_AT||b.TEST_DATE));
  const customers = new Map(readTable("CUSTOMERS").map(r=>[norm(r.CUSTOMER_NAME),r]));
  const sites = new Map(), users = new Map(), assets = new Map();
  for(const t of tests) {
    const ck=norm(t.CUSTOMER);
    if(!customers.has(ck)) customers.set(ck,{CUSTOMER_ID:Utilities.getUuid(),CUSTOMER_NAME:t.CUSTOMER,ACTIVE:"TRUE"});
    const cid=customers.get(ck).CUSTOMER_ID;
    const sk=key(cid,t.SITE), uk=key(t.TESTER,t.INITIALS), ak=key(cid,t.ASSET_NUMBER);
    if(!sites.has(sk)) sites.set(sk,{SITE_ID:Utilities.getUuid(),CUSTOMER_ID:cid,SITE_NAME:t.SITE,ACTIVE:"TRUE"});
    if(!users.has(uk)) users.set(uk,{USER_ID:Utilities.getUuid(),NAME:t.TESTER,INITIALS:t.INITIALS,ACTIVE:"TRUE"});
    assets.set(ak,{CUSTOMER_ID:cid,ASSET_NUMBER:t.ASSET_NUMBER,SITE:t.SITE,DESCRIPTION:t.DESCRIPTION,LAST_TEST_DATE:t.TEST_DATE,NEXT_TEST_DATE:t.RESULT==="PASS"?t.NEXT_TEST_DATE:"",LAST_RESULT:t.RESULT,UPDATED_AT:t.CREATED_AT||t.TEST_DATE});
  }
  const preserve=(old,item)=>Object.assign({},item,old);
  upsert("CUSTOMERS",r=>norm(r.CUSTOMER_NAME),customers.values(),preserve);
  upsert("SITES",r=>key(r.CUSTOMER_ID,r.SITE_NAME),sites.values(),preserve);
  upsert("USERS",r=>key(r.NAME,r.INITIALS),users.values(),preserve);
  upsert("ASSETS",r=>key(r.CUSTOMER_ID,r.ASSET_NUMBER),assets.values(),(old,item)=>String(old.UPDATED_AT||"")>String(item.UPDATED_AT||"")?old:Object.assign({},old,item));
}
