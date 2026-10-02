const fs=require('fs'),path=require('path'),mf=require('mineflayer'),assert=require('assert');const dir=path.resolve(process.env.DYCLAIM_TEST_SERVER || path.join(__dirname,'../../.local/server'));const sleep=ms=>new Promise(r=>setTimeout(r,ms));const results=[];let bot;
async function consoleCmd(text){fs.appendFileSync(path.join(dir,'commands.txt'),text+'\n');await sleep(500)}
async function probe(name,condition){const token='DYCLAIM_PROBE_'+name;const file=path.join(dir,'logs/latest.log');const before=fs.readFileSync(file,'utf8').length;await consoleCmd('execute '+condition+' run say '+token);const log=fs.readFileSync(file,'utf8').slice(before);assert(log.includes('[Not Secure] [Server] '+token)||log.includes('[Server] '+token)||log.includes(token),'Probe failed: '+name);results.push(name);console.log('PASS '+name)}
(async()=>{
 bot=mf.createBot({host:'127.0.0.1',port:Number(process.env.DYCLAIM_TEST_PORT || 25575),username:'BetaTest',version:process.env.DYCLAIM_TEST_CLIENT_VERSION || '1.20.4',auth:'offline'});bot.on('error',console.error);await new Promise(r=>bot.once('spawn',r));
 await consoleCmd('tp BetaTest 56 64 8');bot.chat('/unclaim');await sleep(600);bot.chat('/confirm');await sleep(600);
 await consoleCmd('fill 28 64 0 70 66 22 air');await consoleCmd('setblock 31 64 4 piston[facing=east]');await consoleCmd('setblock 32 64 4 stone');await consoleCmd('setblock 30 64 4 redstone_block');await sleep(500);await probe('PISTON_INCOMING','if block 32 64 4 stone if block 33 64 4 air');
 await consoleCmd('setblock 47 64 4 piston[facing=east]');await consoleCmd('setblock 48 64 4 stone');await consoleCmd('setblock 46 64 4 redstone_block');await sleep(500);await probe('PISTON_OUTGOING','if block 48 64 4 stone if block 49 64 4 air');
 await consoleCmd('tp BetaTest 56 64 8');bot.chat('/claim');await sleep(700);bot.chat('/confirm');await sleep(700);
 await consoleCmd('setblock 46 64 4 air');await consoleCmd('setblock 46 64 4 redstone_block');await sleep(500);await probe('PISTON_SAME_OWNER','if block 49 64 4 stone');
 await consoleCmd('setblock 64 64 8 chest');await consoleCmd('setblock 63 64 8 hopper[facing=east]{Items:[{Slot:0b,id:"minecraft:diamond",Count:1b}]}');await sleep(1500);await probe('HOPPER_OUTGOING','if data block 63 64 8 Items[{id:"minecraft:diamond"}] unless data block 64 64 8 Items[0]');
 await consoleCmd('setblock 63 64 10 chest');await consoleCmd('setblock 64 64 10 hopper[facing=west]{Items:[{Slot:0b,id:"minecraft:diamond",Count:1b}]}');await sleep(1500);await probe('HOPPER_INCOMING','if data block 64 64 10 Items[{id:"minecraft:diamond"}] unless data block 63 64 10 Items[0]');
 await consoleCmd('setblock 48 64 12 chest');await consoleCmd('setblock 47 64 12 hopper[facing=east]{Items:[{Slot:0b,id:"minecraft:diamond",Count:1b}]}');await sleep(1500);await probe('HOPPER_SAME_OWNER','if data block 48 64 12 Items[{id:"minecraft:diamond"}]');
 await consoleCmd('fill 60 64 14 66 64 18 air');await consoleCmd('setblock 63 64 14 water');await sleep(1800);await probe('FLUID_OUTGOING','if block 63 64 14 water if block 64 64 14 air');
 await consoleCmd('fill 48 64 0 79 66 31 air');await consoleCmd('setblock 64 64 14 water');await sleep(1800);await probe('FLUID_INCOMING','if block 64 64 14 water if block 63 64 14 air');
 fs.writeFileSync(path.join(__dirname,'boundaries-result.json'),JSON.stringify({results},null,2));bot.quit();await sleep(500);
})().then(()=>process.exit(0)).catch(err=>{console.error(err);fs.writeFileSync(path.join(__dirname,'boundaries-failed.json'),JSON.stringify({results,error:err.stack},null,2));if(bot)bot.quit();process.exit(1)});





