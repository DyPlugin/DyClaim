const mineflayer = require('mineflayer');
const fs = require('fs');
const path = require('path');
const mode = process.argv[2] || 'current';
const sleep = ms => new Promise(resolve=>setTimeout(resolve,ms));
const messages={};
const bots={};
async function connect(name) {
  const bot=mineflayer.createBot({host:'127.0.0.1',port:Number(process.env.DYCLAIM_TEST_PORT || 25575),username:name,version:process.env.DYCLAIM_TEST_CLIENT_VERSION || '1.20.4',auth:'offline'});
  messages[name]=[];bot.on('messagestr',message=>{messages[name].push(message);console.log(name+': '+message)});
  bot.on('error',err=>console.log(name+' ERROR '+err.message));bot.on('kicked',reason=>console.log(name+' KICK '+reason));
  bots[name]=bot;await new Promise(resolve=>bot.once('spawn',resolve));return bot;
}
async function command(bot,text){bot.chat(text);await sleep(650);}
async function consoleCommand(text){fs.appendFileSync(path.join(process.env.DYCLAIM_TEST_SERVER || path.join(__dirname,'../../.local/server'),'commands.txt'),text+'\n');await sleep(650);}
(async()=>{
 const a=await connect('AlphaTest'),b=await connect('BetaTest');
 console.log('BOTS_READY '+mode);
 fs.appendFileSync(path.join(process.env.DYCLAIM_TEST_SERVER || path.join(__dirname,'../../.local/server'),'commands.txt'),'op AlphaTest\nop BetaTest\ngamemode creative AlphaTest\ngamemode creative BetaTest\nfill 0 63 0 63 63 15 minecraft:stone\n');await sleep(1200);
 await consoleCommand('tp AlphaTest 8 64 8');await consoleCommand('tp BetaTest 40 64 8');await consoleCommand('deop AlphaTest');await consoleCommand('deop BetaTest');
 await command(a,'/claim');await command(a,'/confirm');await command(b,'/claim');await command(b,'/confirm');
 await command(a,'/claim trust BetaTest');await command(a,'/claim trustlist');await command(a,'/claim list');await command(a,'/claim see');
 await command(a,'/claim sell');await consoleCommand('tp AlphaTest 40 64 8');await command(a,'/confirm');await sleep(1500);
 const file=path.join(process.env.DYCLAIM_TEST_SERVER || path.join(__dirname,'../../.local/server'),'plugins/DyClaim/claims.json');const data=JSON.parse(fs.readFileSync(file,'utf8'));const claims=data.claims||data;
 const actual=Object.keys(claims);console.log('CLAIM_KEYS '+actual.join(','));
 const pass=mode==='baseline'?actual.includes('world:0:0')&&!actual.includes('world:2:0'):!actual.includes('world:0:0')&&actual.includes('world:2:0');
 if(!pass)throw new Error('Sale context regression: '+actual);
 console.log('PASS sale context '+mode);
 fs.writeFileSync(path.join(__dirname,mode+'-result.json'),JSON.stringify({mode,pass,messages,claimKeys:actual},null,2));
 a.quit();b.quit();await sleep(500);
})().then(()=>process.exit(0)).catch(err=>{console.error(err);Object.values(bots).forEach(bot=>bot.quit());process.exit(1)});




