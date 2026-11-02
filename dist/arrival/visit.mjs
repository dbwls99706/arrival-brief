/** User-entered parking reminder. Never a vehicle-location sensor or a route. */
export const PARKING_TTL_MS=24*60*60*1000;
export function createParkingMemo(memo,now=Date.now()){
 if(typeof memo!=='string'||!Number.isSafeInteger(now)||now<0)throw new TypeError('invalid memo');
 const value=memo.trim();
 if(!value||[...value].length>64||/[\u0000-\u001f\u007f]/.test(value))throw new TypeError('invalid memo');
 return {schema:1,memo:value,savedAt:now};
}
export function inspectParkingMemo(raw,now=Date.now()){
 if(raw==null)return {status:'empty'};
 try{
  const r=typeof raw==='string'?JSON.parse(raw):raw;
  if(!r||Array.isArray(r)||Object.keys(r).sort().join(',')!=='memo,savedAt,schema'||r.schema!==1)return {status:'invalid'};
  const normalized=createParkingMemo(r.memo,r.savedAt);
  if(normalized.memo!==r.memo||r.savedAt>now||!Number.isSafeInteger(now))return {status:'invalid'};
  if(now-r.savedAt>=PARKING_TTL_MS)return {status:'expired'};
  return {status:'available',record:normalized,expiresAt:r.savedAt+PARKING_TTL_MS};
 }catch{return {status:'invalid'};}
}
export function canUseParkingMemo(mode,parkedConfirmed){return mode==='parked'&&parkedConfirmed===true;}
export function validateNoticeFile({name,size,type}){
 return typeof name==='string'&&/\.txt$/i.test(name)&&Number.isSafeInteger(size)&&size>0&&size<=40000&&(!type||type==='text/plain');
}
