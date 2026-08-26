import {api}from"./apiClient";export const reportApi={inventory:()=>api.get("/reports/inventory",{responseType:"blob"}).then(r=>r.data)};
