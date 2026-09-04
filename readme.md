This repo contains the router and compiler services for KojoJS.

### Prerequisites

* In another terminal window, start the **kojojs-editor web-server**, see instructions here: https://github.com/litan/kojojs-editor 

### How to run in terminal:
  
* Start `sbt` i terminal.
* Execute the `compile` command inside sbt.
* Execute the `update` command inside sbt.
* Execute the `router/reStart` command inside sbt to get the **router** going.
* Execute the `compilerServer/reStart` command inside sbt to get the **compiler service** going
* Navigate to localhost:9000 using a browser to start using KojoJS

### Instructions for using with IntelliJ Idea:
* Import sbt project
* Open sbt shell
* Run `router/reStart` to get the router going. The kojojs-editor web-server should be running at this point.
* Run `compilerServer/reStart` to get the compiler service going
* Navigate to localhost:9000 to start using KojoJS

## Koco dağıtımı (ikojo.fly.dev)

Türkçe (Koco) sürüm — router + compilerServer + editör tek konteynerde,
nginx önünde — `bulent2k2/koco-deploy` ile paketlenip Fly.io'ya dağıtılıyor.
Tam belge: koco-deploy/README.md. Özet:

```sh
cd <yol>/koco-deploy
git -C ../kojojs-core pull      # güncel 'page' (kojojs-dev'den senkronlanmış runtime)
git -C ../kojojs-dev  pull      # kaynak (isteğe bağlı)

# build.sh İKİ JDK + yamalı derleyici yolu İSTİYOR (yoksa derleme patlar):
export KOCO_JDK_CORE=/path/to/jdk11    # 11/17/21 — kojojs-core Java 9+ ister (readAllBytes)
export KOCO_JDK_EDITOR=/path/to/jdk8   # kojojs-editor sbt 0.13 + Play 2.6 → Java 8
export KOCO_SCALA_TR=/path/to/kojo/scala-tr/build/pack/lib   # yan yana kojo klonu yoksa

./build.sh                     # üç servisi paketler + yamalı jar takası (KOCO_TOOLCHAIN=tr)

# Yerel makinede çalıştır / test et:
docker build -t koco .
docker run --rm -p 7860:7860 --memory 4g koco    # -> http://localhost:7860

# Fly'a dağıt (yerelde kurulan imajı iter):
fly deploy --local-only -a ikojo
```

Not: `page` bu repoda ÜRETİLMİŞ bir kopyadır — doğrudan düzenlenmez. Kaynak
kojojs-dev; `sync-kojojs-core.sh` (KOJOJS_CORE_213=1) ile buraya kopyalanır.
