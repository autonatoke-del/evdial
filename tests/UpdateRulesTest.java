package ru.autonatoke.evdial;
public class UpdateRulesTest {
 static int checks;
 static void expect(boolean ok){checks++;if(!ok)throw new AssertionError("check "+checks);}
 static void reject(Runnable r){checks++;try{r.run();}catch(IllegalArgumentException expected){return;}throw new AssertionError("not rejected "+checks);}
 static void candidate(String p,long v,int sdk,long bytes,String sha,String signer,String url){UpdateRules.metadata(p,v,sdk,bytes,sha,signer,url);}
 public static void main(String[] a){
  String url=UpdateRules.BASE+"releases/EVDial-0.2.2.apk",sha="a".repeat(64),key=UpdateRules.SIGNER,p=UpdateRules.PACKAGE;
  candidate(p,4,26,24144000,sha,key,url);checks++;
  expect(UpdateRules.newer(3,4));expect(!UpdateRules.newer(4,4));expect(!UpdateRules.newer(5,4));
  reject(()->candidate("wrong",4,26,24144000,sha,key,url));
  reject(()->candidate(p,0,26,24144000,sha,key,url));
  reject(()->candidate(p,-1,26,24144000,sha,key,url));
  reject(()->candidate(p,4,25,24144000,sha,key,url));
  reject(()->candidate(p,4,26,0,sha,key,url));
  reject(()->candidate(p,4,26,UpdateRules.MAX_APK_BYTES+1,sha,key,url));
  reject(()->candidate(p,4,26,24144000,"123",key,url));
  reject(()->candidate(p,4,26,24144000,sha,sha,url));
  String[] bad={"http://autonatoke.ru/evdial/releases/a.apk","https://evil.test/evdial/releases/a.apk","https://autonatoke.ru.evil.test/evdial/releases/a.apk","https://autonatoke.ru@evil.test/evdial/releases/a.apk","https://name@autonatoke.ru/evdial/releases/a.apk","https://autonatoke.ru:8443/evdial/releases/a.apk","https://autonatoke.ru/other/a.apk","https://autonatoke.ru/evdial/releases/../a.apk","https://autonatoke.ru/evdial/releases/%2e%2e/a.apk","https://autonatoke.ru/evdial/releases/a.apk?url=evil","https://autonatoke.ru/evdial/releases/a.apk#x","file:///evdial/releases/a.apk","https://autonatoke.ru/evdial/releases//a.apk","https://autonatoke.ru/evdial/releases/a.zip"};
  for(String b:bad)reject(()->UpdateRules.apkUrl(b));
  expect(UpdateRules.hex("AB".repeat(32)).equals("ab".repeat(32)));
  reject(()->UpdateRules.hex(null));reject(()->UpdateRules.hex("g".repeat(64)));
  System.out.println("PASS: "+checks+" updater validation assertions");
 }
}
